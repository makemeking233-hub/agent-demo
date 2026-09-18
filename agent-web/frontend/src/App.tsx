import { useEffect, useState } from "react";
import {
  ChatApi,
  inferProvider,
  readModelSelection,
  writeModelSelection,
  type ModelEntry,
  type ModelSelection,
  type ProviderGroup,
  type SessionSummary,
  type Workspace,
} from "./api/chat";
import { ChatPanel } from "./components/ChatPanel";
import { OfflineBanner } from "./components/OfflineBanner";
import { PwaUpdatePrompt } from "./components/PwaUpdatePrompt";
import { Sidebar, type SidebarSession } from "./components/Sidebar";
import { TopBar } from "./components/TopBar";
import { OnlineProvider } from "./hooks/useOnline";
import styles from "./App.module.css";

function toSidebar(s: SessionSummary): SidebarSession {
  return { id: s.id, title: s.title, preview: s.preview, workspace: s.workspace, time: s.time };
}

const DEFAULT_SELECTION: ModelSelection = {
  provider: "deepseek",
  model: "deepseek-chat",
  reasoningEffort: undefined,
};

/** 在嵌套 providers 里找 model（返回 provider id + entry）。 */
function findModel(
  providers: ProviderGroup[],
  modelId: string
): { providerId: string; entry: ModelEntry } | null {
  for (const p of providers) {
    const hit = p.models.find((m) => m.id === modelId);
    if (hit) return { providerId: p.id, entry: hit };
  }
  return null;
}

/**
 * 按已加载的 providers 校验并规整一个 {@link ModelSelection}（add-provider-catalog-abstract task 11.2）。
 *
 * <p>规整规则：
 *
 * <ol>
 *   <li>model 不在目录里 → 回退 default（deepseek-chat，再退到第一个 provider 的第一个 model）
 *   <li>provider 为空（旧格式 localStorage）→ 用 {@link inferProvider} 按 model 前缀推断，
 *       推断失败用 model 实际所属 provider
 *   <li>reasoningEffort 不在该 model 档位里 → 取第一档，model 不支持则置 undefined
 * </ol>
 */
function normalizeSelection(
  providers: ProviderGroup[],
  raw: ModelSelection
): { selection: ModelSelection; entry: ModelEntry | null } {
  const hit = findModel(providers, raw.model);
  if (!hit) {
    const fallbackHit =
      findModel(providers, "deepseek-chat") ??
      (providers[0]?.models[0]
        ? { providerId: providers[0].id, entry: providers[0].models[0] }
        : null);
    if (!fallbackHit) return { selection: raw, entry: null };
    return { selection: regularize(providers, fallbackHit.providerId, fallbackHit.entry, raw), entry: fallbackHit.entry };
  }
  return { selection: regularize(providers, hit.providerId, hit.entry, raw), entry: hit.entry };
}

function regularize(
  providers: ProviderGroup[],
  actualProviderId: string,
  entry: ModelEntry,
  raw: ModelSelection
): ModelSelection {
  const inferred = raw.provider && raw.provider.length > 0 ? raw.provider : inferProvider(entry.id);
  const provider = inferred ?? actualProviderId;
  let effort: string | undefined;
  if (entry.supportsReasoning && entry.reasoningEfforts.length > 0) {
    const keep = entry.reasoningEfforts.some((e) => e.id === raw.reasoningEffort);
    effort = keep ? raw.reasoningEffort : entry.reasoningEfforts[0].id;
  }
  return { provider, model: entry.id, reasoningEffort: effort };
}

export function App() {
  const [sessions, setSessions] = useState<SidebarSession[]>([]);
  const [archived, setArchived] = useState<SidebarSession[]>([]);
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [activeWorkspace, setActiveWorkspace] = useState<string>("agent-demo");
  const [currentSessionId, setCurrentSessionId] = useState<string | null>("1");
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  // add-provider-catalog-abstract task 11：模型选择从 {model, reasoningEffort} 升级为
  // ModelSelection（含 provider）。App 持有并下发给 TopBar（完整 selection）+
  // ChatPanel（拆开的 model / effort / entry，供 Composer 用）。
  const [selection, setSelection] = useState<ModelSelection>(DEFAULT_SELECTION);
  const [currentModelEntry, setCurrentModelEntry] = useState<ModelEntry | null>(null);

  const api = new ChatApi();

  // 拉 provider 目录用于校验 localStorage 持久化的 selection（task 11.2）
  useEffect(() => {
    let cancelled = false;
    const saved = readModelSelection() ?? DEFAULT_SELECTION;
    api
      .listModels()
      .then((resp) => {
        if (cancelled) return;
        const { selection: normalized, entry } = normalizeSelection(resp.providers ?? [], saved);
        setSelection(normalized);
        setCurrentModelEntry(entry);
        writeModelSelection(normalized);
      })
      .catch(() => {
        // 拉取失败：乐观采用持久化值（provider 空时按 model 前缀推断）
        if (cancelled) return;
        const provider = saved.provider || inferProvider(saved.model) || DEFAULT_SELECTION.provider;
        setSelection({ ...saved, provider });
      });
    return () => {
      cancelled = true;
    };
  }, []);

  /** 切换模型选择（ModelSelect 两层菜单回调）→ 落 localStorage + 刷新 model entry。 */
  function handleSelectionChange(next: ModelSelection) {
    setSelection(next);
    writeModelSelection(next);
    api
      .listModels()
      .then((resp) => {
        const { selection: normalized, entry } = normalizeSelection(resp.providers ?? [], next);
        setCurrentModelEntry(entry);
        // provider 空时 normalize 会补上推断值，回写保持一致
        if (normalized.provider !== next.provider) {
          setSelection(normalized);
          writeModelSelection(normalized);
        }
      })
      .catch(() => {
        setCurrentModelEntry(null);
      });
  }

  /** Composer 里只改 effort（保持当前 provider / model）。 */
  function handleReasoningEffortChange(newEffort: string) {
    const next: ModelSelection = { ...selection, reasoningEffort: newEffort };
    setSelection(next);
    writeModelSelection(next);
  }

  const refresh = () => {
    api.listWorkspaces().then(setWorkspaces).catch(() => setWorkspaces([]));
    api
      .listSessions(activeWorkspace)
      .then((l) => setSessions(l.map(toSidebar)))
      .catch(() => setSessions([]));
    api
      .listArchived(activeWorkspace)
      .then((l) => setArchived(l.map(toSidebar)))
      .catch(() => setArchived([]));
  };

  // 拉取真实会话列表（add-session-switch）+ 归档列表（add-session-management）+ 工作区
  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeWorkspace]);

  function handleNewSession() {
    const newId = String(Date.now());
    setSessions((prev) => [
      { id: newId, title: "新会话", preview: "", workspace: activeWorkspace, time: Date.now() },
      ...prev,
    ]);
    setCurrentSessionId(newId);
  }

  // 删除（归档）会话：若删除的是当前查看会话，切到下一条或空态
  async function handleArchive(id: string) {
    await api.archiveSession(id);
    const list = await api
      .listSessions(activeWorkspace)
      .catch(() => []);
    const mapped = list.map(toSidebar);
    setSessions(mapped);
    if (currentSessionId === id) setCurrentSessionId(mapped[0]?.id ?? null);
    api
      .listArchived(activeWorkspace)
      .then((l) => setArchived(l.map(toSidebar)))
      .catch(() => setArchived([]));
  }

  // 恢复归档会话
  async function handleRestore(id: string) {
    await api.restoreSession(id);
    refresh();
  }

  // 重命名会话
  async function handleRename(id: string, title: string) {
    await api.renameSession(id, title);
    refresh();
  }

  // 新建工作区（名称 + 目录）
  async function handleCreateWorkspace(name: string, dir: string) {
    await api.createWorkspace(name, dir);
    setActiveWorkspace(name);
    refresh();
  }

  return (
    <OnlineProvider>
      <OfflineBanner />
      <PwaUpdatePrompt />
      <div className={styles.app}>
        <TopBar
          api={api}
          selection={selection}
          onSelectionChange={handleSelectionChange}
          onOpenSettings={() => alert("设置 v0.2 接入")}
        />
        <div
          className={
            sidebarCollapsed
              ? `${styles.body} ${styles.bodyCollapsed}`
              : styles.body
          }
        >
          <Sidebar
            sessions={sessions}
            archived={archived}
            workspaces={workspaces}
            activeWorkspace={activeWorkspace}
            currentSessionId={currentSessionId}
            onSelect={setCurrentSessionId}
            onNewSession={handleNewSession}
            onWorkspaceChange={setActiveWorkspace}
            onRename={handleRename}
            onCreateWorkspace={handleCreateWorkspace}
            onArchive={handleArchive}
            onRestore={handleRestore}
            onCollapseToggle={setSidebarCollapsed}
          />
          <main className={styles.main}>
            <ChatPanel
              currentSessionId={currentSessionId}
              workspace={activeWorkspace}
              provider={selection.provider}
              model={selection.model}
              reasoningEffort={selection.reasoningEffort ?? ""}
              currentModelEntry={currentModelEntry}
              onReasoningEffortChange={handleReasoningEffortChange}
            />
          </main>
        </div>
      </div>
    </OnlineProvider>
  );
}

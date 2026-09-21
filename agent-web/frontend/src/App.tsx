import { useEffect, useRef, useState } from "react";
import {
  ChatApi,
  inferProvider,
  readModelSelection,
  writeModelSelection,
  type ModelEntry,
  type ModelSelection,
  type SessionSummary,
  type Workspace,
} from "./api/chat";
import { ChatPanel } from "./components/ChatPanel";
import { OfflineBanner } from "./components/OfflineBanner";
import { PwaUpdatePrompt } from "./components/PwaUpdatePrompt";
import { SettingsModal } from "./components/SettingsModal";
import { Sidebar, type SidebarSession } from "./components/Sidebar";
import { TopBar } from "./components/TopBar";
import { OnlineProvider } from "./hooks/useOnline";
import { useThemeApplication } from "./hooks/useThemeApplication";
import { resolveModelSelection } from "./lib/model-selection";
import styles from "./App.module.css";

function toSidebar(s: SessionSummary): SidebarSession {
  return { id: s.id, title: s.title, preview: s.preview, workspace: s.workspace, time: s.time };
}

/**
 * 未取得目录 / 无历史选择时的初始 selection。
 *
 * <p>`model` 留空串（与 main 的 fix-stale-model-fallback 一致）：空串在请求体里表示
 * 「未指定」，由服务端回落到 `agent.chat.default-model` —— 那个值必然是合法的。
 */
const EMPTY_SELECTION: ModelSelection = { provider: "", model: "", reasoningEffort: undefined };

export function App() {
  // add-settings-general-items: 把 settings store 的 preference 应用到 <html data-theme>
  useThemeApplication();
  const [sessions, setSessions] = useState<SidebarSession[]>([]);
  const [archived, setArchived] = useState<SidebarSession[]>([]);
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [activeWorkspace, setActiveWorkspace] = useState<string>("agent-demo");
  const [currentSessionId, setCurrentSessionId] = useState<string | null>("1");
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  // add-settings-foundation: 设置 modal 状态
  const [settingsOpen, setSettingsOpen] = useState(false);
  const settingsTriggerRef = useRef<HTMLElement | null>(null);
  // add-provider-catalog-abstract task 11：模型选择从 {model, reasoningEffort} 升级为
  // ModelSelection（含 provider）。App 持有并下发给 TopBar（完整 selection）+
  // ChatPanel（拆开的 model / effort / entry，供 Composer 用）。
  // provider 随 mode/model 一起持久化到 localStorage。
  const [selection, setSelection] = useState<ModelSelection>(EMPTY_SELECTION);
  const [currentModelEntry, setCurrentModelEntry] = useState<ModelEntry | null>(null);

  const api = new ChatApi();

  // 拉模型目录用于校验 localStorage 持久化的 selection（task 11.2）
  useEffect(() => {
    let cancelled = false;
    const saved = readModelSelection() ?? EMPTY_SELECTION;
    api
      .listModels()
      .then((resp) => {
        if (cancelled) return;
        // fix-stale-model-fallback：兜底值来自服务端 defaultProvider/defaultModel，不硬编码。
        // 解析规则与不变量见 lib/model-selection.ts（同目录有单测锁死）。
        const resolved = resolveModelSelection(
          resp.providers ?? [],
          resp.defaultProvider,
          resp.defaultModel,
          saved
        );
        setSelection(resolved.selection);
        setCurrentModelEntry(resolved.entry);
        writeModelSelection(resolved.selection);
      })
      .catch(() => {
        // 拉不到目录就无法校验历史选择，因此不发未经校验的 id：
        // 置空让服务端用配置默认值，而不是把一个可能已下线的模型发出去。
        if (cancelled) return;
        const provider = saved.provider || inferProvider(saved.model) || "";
        setSelection({ provider, model: "", reasoningEffort: saved.reasoningEffort });
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
        const resolved = resolveModelSelection(
          resp.providers ?? [],
          resp.defaultProvider,
          resp.defaultModel,
          next
        );
        setCurrentModelEntry(resolved.entry);
        // provider / effort 被规整时回写，保持持久化与展示一致
        if (
          resolved.selection.provider !== next.provider ||
          resolved.selection.reasoningEffort !== next.reasoningEffort
        ) {
          setSelection(resolved.selection);
          writeModelSelection(resolved.selection);
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
    const list = await api.listSessions(activeWorkspace).catch(() => []);
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

  // 新建工作区（align-dsh-workspace v2: 只传 path，name + title 后端派生）
  async function handleCreateWorkspace(dir: string) {
    const ws = await api.createWorkspace(dir);
    setActiveWorkspace(ws.name);
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
          onOpenSettings={() => {
            settingsTriggerRef.current = document.activeElement as HTMLElement | null;
            setSettingsOpen(true);
          }}
        />
        <div
          className={
            sidebarCollapsed ? `${styles.body} ${styles.bodyCollapsed}` : styles.body
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
      <SettingsModal
        open={settingsOpen}
        onClose={() => setSettingsOpen(false)}
        triggerElement={settingsTriggerRef.current}
        api={api}
        selection={selection}
        reasoningEfforts={currentModelEntry?.reasoningEfforts ?? []}
        onSelectionChange={handleSelectionChange}
        onReasoningEffortChange={handleReasoningEffortChange}
      />
    </OnlineProvider>
  );
}

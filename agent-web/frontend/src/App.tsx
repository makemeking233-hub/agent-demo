import { useEffect, useRef, useState } from "react";
import { ChatApi, type ModelEntry, type SessionSummary, type Workspace } from "./api/chat";
import { ChatPanel } from "./components/ChatPanel";
import { OfflineBanner } from "./components/OfflineBanner";
import { PwaUpdatePrompt } from "./components/PwaUpdatePrompt";
import { SettingsModal } from "./components/SettingsModal";
import { Sidebar, type SidebarSession } from "./components/Sidebar";
import { TopBar } from "./components/TopBar";
import { OnlineProvider } from "./hooks/useOnline";
import styles from "./App.module.css";

function toSidebar(s: SessionSummary): SidebarSession {
  return { id: s.id, title: s.title, preview: s.preview, workspace: s.workspace, time: s.time };
}

export function App() {
  const [sessions, setSessions] = useState<SidebarSession[]>([]);
  const [archived, setArchived] = useState<SidebarSession[]>([]);
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [activeWorkspace, setActiveWorkspace] = useState<string>("agent-demo");
  const [currentSessionId, setCurrentSessionId] = useState<string | null>("1");
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  // add-settings-foundation: 设置 modal 状态
  const [settingsOpen, setSettingsOpen] = useState(false);
  const settingsTriggerRef = useRef<HTMLElement | null>(null);
  // add-models-dropdown-v0：模型/思考强度全局状态（App 持有，TopBar 和 ChatPanel 共享）
  const [model, setModel] = useState<string>("deepseek-chat");
  const [reasoningEffort, setReasoningEffort] = useState<string>("medium");
  const [currentModelEntry, setCurrentModelEntry] = useState<ModelEntry | null>(null);

  const api = new ChatApi();

  // 拉 supported-models 用于校验 localStorage 持久化的 model + reasoningEffort
  useEffect(() => {
    let cancelled = false;
    let savedModel = "deepseek-chat";
    let savedEffort = "medium";
    try {
      const raw = window.localStorage.getItem("agent-demo:model-selection");
      if (raw) {
        const parsed = JSON.parse(raw) as { model?: string; reasoningEffort?: string };
        if (typeof parsed.model === "string" && parsed.model.length > 0) savedModel = parsed.model;
        if (typeof parsed.reasoningEffort === "string" && parsed.reasoningEffort.length > 0)
          savedEffort = parsed.reasoningEffort;
      }
    } catch {
      /* ignore */
    }
    api
      .listModels()
      .then((resp) => {
        if (cancelled) return;
        const valid = resp.models.find((m) => m.id === savedModel);
        const finalModel = valid ? savedModel : "deepseek-chat";
        const entry =
          valid ?? resp.models.find((m) => m.id === "deepseek-chat") ?? resp.models[0] ?? null;
        const finalEffort =
          entry && entry.reasoningEfforts.includes(savedEffort)
            ? savedEffort
            : entry && entry.reasoningEfforts.length > 0
              ? entry.reasoningEfforts[0]
              : "medium";
        setModel(finalModel);
        setReasoningEffort(finalEffort);
        setCurrentModelEntry(entry);
        try {
          window.localStorage.setItem(
            "agent-demo:model-selection",
            JSON.stringify({ model: finalModel, reasoningEffort: finalEffort })
          );
        } catch {
          /* ignore */
        }
      })
      .catch(() => {
        setModel(savedModel);
        setReasoningEffort(savedEffort);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  function handleModelChange(newModel: string) {
    setModel(newModel);
    api
      .listModels()
      .then((resp) => {
        const entry = resp.models.find((m) => m.id === newModel) ?? null;
        setCurrentModelEntry(entry);
        const nextEffort =
          entry && entry.reasoningEfforts.includes(reasoningEffort)
            ? reasoningEffort
            : entry && entry.reasoningEfforts.length > 0
              ? entry.reasoningEfforts[0]
              : reasoningEffort;
        setReasoningEffort(nextEffort);
        try {
          window.localStorage.setItem(
            "agent-demo:model-selection",
            JSON.stringify({ model: newModel, reasoningEffort: nextEffort })
          );
        } catch {
          /* ignore */
        }
      })
      .catch(() => {
        try {
          window.localStorage.setItem(
            "agent-demo:model-selection",
            JSON.stringify({ model: newModel, reasoningEffort })
          );
        } catch {
          /* ignore */
        }
      });
  }

  function handleReasoningEffortChange(newEffort: string) {
    setReasoningEffort(newEffort);
    try {
      window.localStorage.setItem(
        "agent-demo:model-selection",
        JSON.stringify({ model, reasoningEffort: newEffort })
      );
    } catch {
      /* ignore */
    }
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
          model={model}
          onModelChange={handleModelChange}
          onOpenSettings={() => {
            settingsTriggerRef.current = document.activeElement as HTMLElement | null;
            setSettingsOpen(true);
          }}
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
              model={model}
              reasoningEffort={reasoningEffort}
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
      />
    </OnlineProvider>
  );
}

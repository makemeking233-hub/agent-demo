import { useEffect, useState } from "react";
import { ChatApi, type SessionSummary } from "./api/chat";
import { ChatPanel } from "./components/ChatPanel";
import { Sidebar, type SidebarSession } from "./components/Sidebar";
import { TopBar } from "./components/TopBar";
import styles from "./App.module.css";

function toSidebar(s: SessionSummary): SidebarSession {
  return { id: s.id, title: s.title, preview: s.preview, workspace: s.workspace, time: s.time };
}

export function App() {
  const [sessions, setSessions] = useState<SidebarSession[]>([]);
  const [archived, setArchived] = useState<SidebarSession[]>([]);
  const [currentSessionId, setCurrentSessionId] = useState<string | null>("1");
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  const api = new ChatApi();

  const refresh = () => {
    api.listSessions().then((l) => setSessions(l.map(toSidebar))).catch(() => setSessions([]));
    api.listArchived().then((l) => setArchived(l.map(toSidebar))).catch(() => setArchived([]));
  };

  // 拉取真实会话列表（add-session-switch）+ 归档列表（add-session-management）
  useEffect(() => {
    refresh();
  }, []);

  function handleNewSession() {
    const newId = String(Date.now());
    setSessions((prev) => [{ id: newId, title: "新会话", preview: "", workspace: "agent-demo", time: Date.now() }, ...prev]);
    setCurrentSessionId(newId);
  }

  // 删除（归档）会话：若删除的是当前查看会话，切到下一条或空态
  async function handleArchive(id: string) {
    await api.archiveSession(id);
    const list = await api.listSessions().catch(() => []);
    const mapped = list.map(toSidebar);
    setSessions(mapped);
    if (currentSessionId === id) setCurrentSessionId(mapped[0]?.id ?? null);
    api.listArchived().then((l) => setArchived(l.map(toSidebar))).catch(() => setArchived([]));
  }

  // 恢复归档会话
  async function handleRestore(id: string) {
    await api.restoreSession(id);
    refresh();
  }

  return (
    <div className={styles.app}>
      <TopBar onOpenSettings={() => alert("设置 v0.2 接入")} />
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
          currentSessionId={currentSessionId}
          onSelect={setCurrentSessionId}
          onNewSession={handleNewSession}
          onArchive={handleArchive}
          onRestore={handleRestore}
          onCollapseToggle={setSidebarCollapsed}
        />
        <main className={styles.main}>
          <ChatPanel currentSessionId={currentSessionId} />
        </main>
      </div>
    </div>
  );
}

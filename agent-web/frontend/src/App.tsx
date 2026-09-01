import { useEffect, useState } from "react";
import { ChatApi } from "./api/chat";
import { ChatPanel } from "./components/ChatPanel";
import { Sidebar, type SidebarSession } from "./components/Sidebar";
import { TopBar } from "./components/TopBar";
import styles from "./App.module.css";

export function App() {
  const [sessions, setSessions] = useState<SidebarSession[]>([]);
  const [currentSessionId, setCurrentSessionId] = useState<string | null>("1");
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  // 拉取真实会话列表（add-session-switch）
  useEffect(() => {
    const api = new ChatApi();
    api
      .listSessions()
      .then((list) => setSessions(list.map((s) => ({ id: s.id, title: s.title, preview: s.preview, workspace: s.workspace }))))
      .catch(() => setSessions([]));
  }, []);

  return (
    <div className={styles.app}>
      <TopBar
        onNewSession={() => {
          const newId = String(Date.now());
          setSessions((prev) => [{ id: newId, title: "新会话", preview: "", workspace: "agent-demo" }, ...prev]);
          setCurrentSessionId(newId);
        }}
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
          currentSessionId={currentSessionId}
          onSelect={setCurrentSessionId}
          onCollapseToggle={setSidebarCollapsed}
        />
        <main className={styles.main}>
          <ChatPanel currentSessionId={currentSessionId} />
        </main>
      </div>
    </div>
  );
}

import { useState } from "react";
import { ChatPanel } from "./components/ChatPanel";
import { Sidebar, type SidebarSession } from "./components/Sidebar";
import { TopBar } from "./components/TopBar";
import styles from "./App.module.css";

const PLACEHOLDER_SESSIONS: SidebarSession[] = [
  { id: "1", title: "在 agent-demo 加前端", preview: "类似 DSH 的三栏布局…", workspace: "agent-demo" },
  { id: "2", title: "实现 SSE 流", preview: "message_start / delta / stop", workspace: "agent-demo" },
  { id: "3", title: "权限 in-chat UX", preview: "yes / no / always", workspace: "agent-demo" },
  { id: "4", title: "会话历史查询", preview: "SessionStore.loadLatest", workspace: "agent-demo" },
  { id: "5", title: "OpenSpec 提案", preview: "spec-driven workflow", workspace: "open-source" },
];

export function App() {
  const [currentSessionId, setCurrentSessionId] = useState<string | null>("1");
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  return (
    <div className={styles.app}>
      <TopBar
        onNewSession={() => {
          const newId = String(Date.now());
          PLACEHOLDER_SESSIONS.unshift({
            id: newId,
            title: "新会话",
            preview: "",
            workspace: "agent-demo",
          });
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
          sessions={PLACEHOLDER_SESSIONS}
          currentSessionId={currentSessionId}
          onSelect={setCurrentSessionId}
          onCollapseToggle={setSidebarCollapsed}
        />
        <main className={styles.main}>
          <ChatPanel />
        </main>
      </div>
    </div>
  );
}

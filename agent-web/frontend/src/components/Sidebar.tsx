import { Folder, MessageSquare, PanelLeftClose, PanelLeftOpen } from "lucide-react";
import { useState } from "react";
import styles from "./Sidebar.module.css";

export interface SidebarSession {
  id: string;
  title: string;
  preview: string;
  workspace: string;
  active?: boolean;
}

interface SidebarProps {
  sessions: SidebarSession[];
  currentSessionId: string | null;
  onSelect: (sessionId: string) => void;
  onCollapseToggle: (collapsed: boolean) => void;
}

export function Sidebar(props: SidebarProps) {
  const [collapsed, setCollapsed] = useState(false);

  function toggle() {
    const next = !collapsed;
    setCollapsed(next);
    props.onCollapseToggle(next);
  }

  if (collapsed) {
    return (
      <button type="button" className={styles.collapseButton} onClick={toggle} aria-label="展开侧栏">
        <PanelLeftOpen size={18} />
      </button>
    );
  }

  // 按 workspace 分组
  const groups = new Map<string, SidebarSession[]>();
  for (const s of props.sessions) {
    if (!groups.has(s.workspace)) groups.set(s.workspace, []);
    groups.get(s.workspace)!.push(s);
  }

  return (
    <aside className={styles.sidebar}>
      <div className={styles.header}>
        <span className={styles.title}>会话</span>
        <button type="button" className={styles.iconButton} onClick={toggle} aria-label="折叠侧栏">
          <PanelLeftClose size={16} />
        </button>
      </div>
      <div className={styles.list}>
        {Array.from(groups.entries()).map(([workspace, list]) => (
          <div key={workspace} className={styles.group}>
            <div className={styles.workspaceHeader}>
              <Folder size={12} />
              <span>{workspace}</span>
            </div>
            {list.map((s) => (
              <button
                key={s.id}
                type="button"
                className={
                  s.id === props.currentSessionId
                    ? `${styles.item} ${styles.itemActive}`
                    : styles.item
                }
                onClick={() => props.onSelect(s.id)}
              >
                <MessageSquare size={12} className={styles.itemIcon} />
                <div className={styles.itemText}>
                  <div className={styles.itemTitle}>{s.title}</div>
                  <div className={styles.itemPreview}>{s.preview}</div>
                </div>
              </button>
            ))}
          </div>
        ))}
      </div>
    </aside>
  );
}

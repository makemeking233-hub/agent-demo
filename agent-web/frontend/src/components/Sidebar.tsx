import {
  Folder,
  MessageSquare,
  PanelLeftClose,
  PanelLeftOpen,
  Plus,
  Archive,
  RotateCcw,
  Trash2,
} from "lucide-react";
import { useState } from "react";
import styles from "./Sidebar.module.css";

export interface SidebarSession {
  id: string;
  title: string;
  preview: string;
  workspace: string;
  time: number;
}

interface SidebarProps {
  sessions: SidebarSession[];
  archived: SidebarSession[];
  currentSessionId: string | null;
  onSelect: (sessionId: string) => void;
  onNewSession: () => void;
  onArchive: (sessionId: string) => void;
  onRestore: (sessionId: string) => void;
  onCollapseToggle: (collapsed: boolean) => void;
}

/** 每工作区默认展示的会话数，其余收进"展开其余 N 个会话"。 */
const DEFAULT_VISIBLE = 5;
/** 展开状态持久化 key. */
const EXPANDED_KEY = "agent-demo.sidebar.expanded-workspaces";

function readExpanded(): Set<string> {
  try {
    const raw = localStorage.getItem(EXPANDED_KEY);
    return new Set(JSON.parse(raw ?? "[]") as string[]);
  } catch {
    return new Set();
  }
}

function persistExpanded(next: Set<string>) {
  try {
    localStorage.setItem(EXPANDED_KEY, JSON.stringify([...next]));
  } catch {
    /* 忽略 */
  }
}

/** 相对时间：刚刚 / N分钟 / N小时 / N天。 */
function formatRelativeTime(ms: number): string {
  if (!ms) return "";
  const diff = Date.now() - ms;
  const min = 60_000;
  const hour = 3_600_000;
  const day = 86_400_000;
  if (diff < min) return "刚刚";
  if (diff < hour) return Math.floor(diff / min) + "分钟";
  if (diff < day) return Math.floor(diff / hour) + "小时";
  return Math.floor(diff / day) + "天";
}

export function Sidebar(props: SidebarProps) {
  const [collapsed, setCollapsed] = useState(false);
  const [archiveView, setArchiveView] = useState(false);
  const [expanded, setExpanded] = useState<Set<string>>(() => readExpanded());
  const [confirmingDelete, setConfirmingDelete] = useState<string | null>(null);

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

  /** 按工作区分组（保持传入顺序，传入方已按 mtime 降序）。 */
  const groups = new Map<string, SidebarSession[]>();
  for (const s of (archiveView ? props.archived : props.sessions)) {
    if (!groups.has(s.workspace)) groups.set(s.workspace, []);
    groups.get(s.workspace)!.push(s);
  }

  function toggleExpand(workspace: string) {
    const next = new Set(expanded);
    if (next.has(workspace)) next.delete(workspace);
    else next.add(workspace);
    setExpanded(next);
    persistExpanded(next);
  }

  function requestArchive(id: string) {
    setConfirmingDelete(id);
  }

  function confirmArchive() {
    if (confirmingDelete) props.onArchive(confirmingDelete);
    setConfirmingDelete(null);
  }

  function cancelArchive() {
    setConfirmingDelete(null);
  }

  return (
    <aside className={styles.sidebar}>
      {/* 新会话按钮：侧栏顶部（add-session-management） */}
      <button type="button" className={styles.newSession} onClick={props.onNewSession}>
        <Plus size={16} />
        <span>新会话</span>
      </button>

      <div className={styles.header}>
        <span className={styles.title}>会话</span>
        <div className={styles.headerActions}>
          <button
            type="button"
            className={styles.iconButton}
            onClick={() => setArchiveView(!archiveView)}
            aria-label={archiveView ? "返回会话列表" : "归档"}
            title={archiveView ? "返回会话列表" : "归档/回收站"}
          >
            <Archive size={14} />
          </button>
          <button type="button" className={styles.iconButton} onClick={toggle} aria-label="折叠侧栏">
            <PanelLeftClose size={16} />
          </button>
        </div>
      </div>

      {confirmingDelete && (
        <div className={styles.confirmBar}>
          <span className={styles.confirmText}>删除该会话？</span>
          <span className={styles.confirmActions}>
            <button type="button" className={styles.confirmYes} onClick={confirmArchive}>
              删除
            </button>
            <button type="button" className={styles.confirmNo} onClick={cancelArchive}>
              取消
            </button>
          </span>
        </div>
      )}

      <div className={styles.list}>
        {Array.from(groups.entries()).map(([workspace, list]) => {
          const isExpanded = expanded.has(workspace);
          const visibleList = isExpanded ? list : list.slice(0, DEFAULT_VISIBLE);
          const hiddenCount = list.length - visibleList.length;
          return (
            <div key={workspace} className={styles.group}>
              <div className={styles.workspaceHeader}>
                <Folder size={12} />
                <span>{workspace}</span>
              </div>
              {visibleList.map((s) => (
                <div
                  key={s.id}
                  className={`${styles.item} ${s.id === props.currentSessionId ? styles.itemActive : ""}`}
                >
                  <button
                    type="button"
                    className={styles.itemMain}
                    onClick={() => props.onSelect(s.id)}
                    title={s.preview || s.title}
                  >
                    <MessageSquare size={12} className={styles.itemIcon} />
                    <span className={styles.itemTitle}>{s.title}</span>
                    <span className={styles.itemTime}>{formatRelativeTime(s.time)}</span>
                  </button>
                  {archiveView ? (
                    <button
                      type="button"
                      className={styles.itemAction}
                      onClick={() => props.onRestore(s.id)}
                      aria-label="恢复"
                      title="恢复"
                    >
                      <RotateCcw size={13} />
                    </button>
                  ) : (
                    <button
                      type="button"
                      className={styles.itemAction}
                      onClick={() => requestArchive(s.id)}
                      aria-label="删除"
                      title="删除"
                    >
                      <Trash2 size={13} />
                    </button>
                  )}
                </div>
              ))}
              {list.length > DEFAULT_VISIBLE && (
                <button type="button" className={styles.expandButton} onClick={() => toggleExpand(workspace)}>
                  {isExpanded ? "收起" : `展开其余 ${hiddenCount} 个会话`}
                </button>
              )}
            </div>
          );
        })}
      </div>
    </aside>
  );
}

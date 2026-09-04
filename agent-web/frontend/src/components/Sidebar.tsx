import {
  Archive,
  Check,
  Folder,
  MessageSquare,
  MoreHorizontal,
  PanelLeftClose,
  PanelLeftOpen,
  Pencil,
  Plus,
  RotateCcw,
  Trash2,
  X,
} from "lucide-react";
import { useState } from "react";
import { WorkspacePickerModal } from "./WorkspacePickerModal";
import styles from "./Sidebar.module.css";

export interface SidebarSession {
  id: string;
  title: string;
  preview: string;
  workspace: string;
  time: number;
}

export interface SidebarWorkspace {
  name: string;
  dir: string;
  sessionCount: number;
}

interface SidebarProps {
  sessions: SidebarSession[];
  archived: SidebarSession[];
  workspaces: SidebarWorkspace[];
  activeWorkspace: string;
  currentSessionId: string | null;
  onSelect: (sessionId: string) => void;
  onNewSession: () => void;
  onWorkspaceChange: (workspace: string) => void;
  onRename: (sessionId: string, title: string) => void;
  onCreateWorkspace: (name: string, dir: string) => void;
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
  const [menuOpen, setMenuOpen] = useState<string | null>(null);
  const [renamingId, setRenamingId] = useState<string | null>(null);
  const [renameValue, setRenameValue] = useState("");
  const [showPicker, setShowPicker] = useState(false);

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

  function startRename(id: string, title: string) {
    setRenamingId(id);
    setRenameValue(title);
    setMenuOpen(null);
  }

  function submitRename() {
    if (renamingId && renameValue.trim()) {
      props.onRename(renamingId, renameValue.trim());
    }
    setRenamingId(null);
    setRenameValue("");
  }

  const source = archiveView ? props.archived : props.sessions;

  return (
    <aside className={styles.sidebar}>
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

      {/* 工作区切换条 + 新建工作区入口（仿 DSH 头部 +） */}
      <div className={styles.workspaceBar}>
        <div className={styles.workspaceList}>
          {props.workspaces.map((ws) => (
            <button
              key={ws.name}
              type="button"
              className={`${styles.workspaceItem} ${
                ws.name === props.activeWorkspace ? styles.workspaceActive : ""
              }`}
              onClick={() => {
                setArchiveView(false);
                props.onWorkspaceChange(ws.name);
              }}
              title={ws.dir}
            >
              <Folder size={12} />
              <span className={styles.workspaceName}>{ws.name}</span>
              <span className={styles.workspaceCount}>{ws.sessionCount}</span>
            </button>
          ))}
        </div>
        <button
          type="button"
          className={styles.iconButton}
          onClick={() => setShowPicker(true)}
          aria-label="新建工作区"
          title="新建工作区"
        >
          <Plus size={16} />
        </button>
      </div>

      {showPicker && (
        <WorkspacePickerModal
          open={showPicker}
          onClose={() => setShowPicker(false)}
          onSubmit={props.onCreateWorkspace}
        />
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
                  className={`${styles.item} ${
                    s.id === props.currentSessionId ? styles.itemActive : ""
                  }`}
                >
                  {renamingId === s.id ? (
                    <div className={styles.renameRow}>
                      <input
                        className={styles.renameInput}
                        value={renameValue}
                        onChange={(e) => setRenameValue(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === "Enter") submitRename();
                          if (e.key === "Escape") setRenamingId(null);
                        }}
                        autoFocus
                      />
                      <button type="button" className={styles.iconButton} onClick={submitRename} aria-label="确认重命名">
                        <Check size={13} />
                      </button>
                      <button type="button" className={styles.iconButton} onClick={() => setRenamingId(null)} aria-label="取消重命名">
                        <X size={13} />
                      </button>
                    </div>
                  ) : (
                    <>
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
                      <div className={styles.menuWrap}>
                        <button
                          type="button"
                          className={styles.menuButton}
                          onClick={() => setMenuOpen(menuOpen === s.id ? null : s.id)}
                          aria-label="会话操作"
                          title="更多操作"
                        >
                          <MoreHorizontal size={14} />
                        </button>
                        {menuOpen === s.id && (
                          <div className={styles.menu}>
                            {!archiveView && (
                              <button
                                type="button"
                                className={styles.menuItem}
                                onClick={() => startRename(s.id, s.title)}
                              >
                                <Pencil size={12} /> <span>重命名</span>
                              </button>
                            )}
                            <button
                              type="button"
                              className={styles.menuItem}
                              onClick={() => {
                                archiveView ? props.onRestore(s.id) : props.onArchive(s.id);
                                setMenuOpen(null);
                              }}
                            >
                              {archiveView ? <RotateCcw size={12} /> : <Trash2 size={12} />}
                              <span>{archiveView ? "恢复" : "归档"}</span>
                            </button>
                          </div>
                        )}
                      </div>
                    </>
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
        {source.length === 0 && (
          <div className={styles.empty}>
            <p>暂无会话</p>
          </div>
        )}
      </div>
    </aside>
  );
}

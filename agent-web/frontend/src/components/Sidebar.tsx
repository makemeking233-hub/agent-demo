import {
  Archive,
  Check,
  ChevronDown,
  Folder,
  Link2,
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
import { useEffect, useRef, useState } from "react";
import { WorkspacePickerModal } from "./WorkspacePickerModal";
import styles from "./Sidebar.module.css";

export interface SidebarSession {
  id: string;
  title: string;
  preview: string;
  workspace: string;
  time: number;
  /** 时间分档（auto-archive-stale-sessions）：仅归档列表有值。 */
  bucket?: string;
}

/**
 * 归档视图的时间分档标题（auto-archive-stale-sessions）。
 *
 * <p>键与后端 `SessionAgeBucket` 一致；分档在**后端**计算，保证"7 天保留期"与"7–14 天档"
 * 同源一致，前端只负责按字段分组渲染。
 */
const BUCKET_LABELS: Record<string, string> = {
  recent: "最近归档",
  last_week: "上周",
  within_month: "本月",
  earlier: "更早",
};

/** 分档展示顺序（近 → 远）。 */
const BUCKET_ORDER = ["recent", "last_week", "within_month", "earlier"];

/** 归档会话的分组键：无分档字段（旧数据/异常）时归入「更早」，保证不丢项。 */
export function bucketKeyOf(session: SidebarSession): string {
  return session.bucket && BUCKET_LABELS[session.bucket] ? session.bucket : "earlier";
}

/** align-dsh-workspace v2: Sidebar 暴露 v2 record 字段（id / title / status / updatedAt） */
export interface SidebarWorkspace {
  name: string;
  id?: string;
  title?: string;
  updatedAt?: number;
  status?: "ok" | "missing_dir";
  // v1 兼容字段
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
  /** align-dsh-workspace-ui-polish: v2 单 action；path 由后端派生 name+title */
  onCreateWorkspace: (path: string) => void;
  onWorkspaceChange: (workspace: string) => void;
  onRename: (sessionId: string, title: string) => void;
  onArchive: (sessionId: string) => void;
  onRestore: (sessionId: string) => void;
  onCollapseToggle: (collapsed: boolean) => void;
  /** 拖拽重排后应用新顺序（不含默认 workspace） */
  onReorderWorkspaces: (orderedNames: string[]) => void;
  /** 重命名 workspace display title（不动 dir/path/name/id） */
  onRenameWorkspace: (name: string, newTitle: string) => void;
  /** 删除 workspace record（不动 dir/session log） */
  onDeleteWorkspace: (name: string) => void;
  /** missing_dir 重新连接：复用 picker 选新 path，调 createWorkspace(path) */
  onReconnectMissingWorkspace: (name: string, newPath: string) => void;
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
  const [workspaceMenuOpen, setWorkspaceMenuOpen] = useState(false);
  const workspaceMenuRef = useRef<HTMLDivElement | null>(null);
  // align-dsh-workspace-ui-polish T7+T8+T9:
  const [draggedName, setDraggedName] = useState<string | null>(null);
  const [contextMenuFor, setContextMenuFor] = useState<string | null>(null);
  const [renamingWs, setRenamingWs] = useState<string | null>(null);
  const [renameWsValue, setRenameWsValue] = useState("");
  const [reconnectFor, setReconnectFor] = useState<string | null>(null);
  const contextMenuRef = useRef<HTMLDivElement | null>(null);

  // 点击外部关闭右键菜单
  useEffect(() => {
    if (!contextMenuFor) return;
    function onMouseDown(e: MouseEvent) {
      if (contextMenuRef.current && !contextMenuRef.current.contains(e.target as Node)) {
        setContextMenuFor(null);
      }
    }
    document.addEventListener("mousedown", onMouseDown);
    return () => document.removeEventListener("mousedown", onMouseDown);
  }, [contextMenuFor]);

  function toggle() {
    const next = !collapsed;
    setCollapsed(next);
    props.onCollapseToggle(next);
  }

  // 点击 workspaceMenu 外部关闭
  useEffect(() => {
    if (!workspaceMenuOpen) return;
    function onMouseDown(e: MouseEvent) {
      if (workspaceMenuRef.current && !workspaceMenuRef.current.contains(e.target as Node)) {
        setWorkspaceMenuOpen(false);
      }
    }
    document.addEventListener("mousedown", onMouseDown);
    return () => document.removeEventListener("mousedown", onMouseDown);
  }, [workspaceMenuOpen]);

  if (collapsed) {
    return (
      <button type="button" className={styles.collapseButton} onClick={toggle} aria-label="展开侧栏">
        <PanelLeftOpen size={18} />
      </button>
    );
  }

  /**
   * 分组：**归档视图按时间分档**（auto-archive-stale-sessions），普通视图按工作区。
   *
   * <p>归档视图的分档键用后端返回的 `bucket`，保证与后端保留期阈值同源；档内保持传入顺序
   * （传入方已按 mtime 降序），档间按近→远固定排序。
   */
  const groups = new Map<string, SidebarSession[]>();
  for (const s of archiveView ? props.archived : props.sessions) {
    const key = archiveView ? bucketKeyOf(s) : s.workspace;
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key)!.push(s);
  }
  /** 分组标题：归档视图显示分档中文名，普通视图显示工作区名。 */
  const groupEntries = Array.from(groups.entries()).sort((a, b) => {
    if (!archiveView) return 0; // 普通视图保持传入顺序
    return BUCKET_ORDER.indexOf(a[0]) - BUCKET_ORDER.indexOf(b[0]);
  });
  const groupLabel = (key: string) => (archiveView ? (BUCKET_LABELS[key] ?? key) : key);

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

  function handleWorkspaceMenuItem(action: () => void) {
    setWorkspaceMenuOpen(false);
    action();
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

      {/* 工作区切换条 + Menu（对齐 dsh：列已有 workspaces + 「Add workspace...」项） */}
      <div className={styles.workspaceBar}>
        <div className={styles.workspaceList}>
          {props.workspaces.map((ws) => (
            renamingWs === ws.name ? (
              <input
                key={`rename-${ws.name}`}
                className={styles.workspaceRenameInput}
                autoFocus
                value={renameWsValue}
                onChange={(e) => setRenameWsValue(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    const v = renameWsValue.trim();
                    if (v) props.onRenameWorkspace(ws.name, v);
                    setRenamingWs(null);
                  } else if (e.key === "Escape") {
                    setRenamingWs(null);
                  }
                }}
                onBlur={() => setRenamingWs(null)}
                data-testid={`workspace-rename-${ws.name}`}
              />
            ) : (
              <button
                key={ws.name}
                type="button"
                className={`${styles.workspaceItem} ${
                  ws.name === props.activeWorkspace ? styles.workspaceActive : ""
                } ${ws.status === "missing_dir" ? styles.workspaceMissing : ""}`}
                onClick={() => {
                  setArchiveView(false);
                  props.onWorkspaceChange(ws.name);
                }}
                onContextMenu={(e) => {
                  e.preventDefault();
                  setContextMenuFor(ws.name);
                }}
                draggable={true}
                onDragStart={(e) => {
                  setDraggedName(ws.name);
                  e.dataTransfer.effectAllowed = "move";
                  e.dataTransfer.setData("text/plain", ws.name);
                }}
                onDragOver={(e) => {
                  if (draggedName && draggedName !== ws.name) {
                    e.preventDefault();
                    e.dataTransfer.dropEffect = "move";
                  }
                }}
                onDrop={(e) => {
                  e.preventDefault();
                  const fromName = e.dataTransfer.getData("text/plain");
                  setDraggedName(null);
                  if (!fromName || fromName === ws.name) return;
                  // 重排：从当前 props.workspaces 出发，把 fromName 移到 ws.name 之前
                  const names = props.workspaces.map((w) => w.name).filter((n) => n !== fromName);
                  const idx = names.indexOf(ws.name);
                  if (idx < 0) return;
                  names.splice(idx, 0, fromName);
                  props.onReorderWorkspaces(names);
                }}
                title={ws.status === "missing_dir"
                  ? `目录已移动：${ws.dir}`
                  : ws.dir}
                data-testid={`workspace-item-${ws.name}`}
              >
                <Folder size={12} />
                <span className={styles.workspaceName}>{ws.title ?? ws.name}</span>
                <span className={styles.workspaceCount}>{ws.sessionCount}</span>
                {ws.status === "missing_dir" && (
                  <button
                    type="button"
                    className={styles.workspaceReconnect}
                    onClick={(e) => {
                      e.stopPropagation();
                      setReconnectFor(ws.name);
                    }}
                    title="重新连接：选择新路径复用此 workspace record"
                    data-testid={`workspace-reconnect-${ws.name}`}
                  >
                    <Link2 size={10} />
                  </button>
                )}
              </button>
            )
          ))}
        </div>
        <div className={styles.workspaceMenuWrap} ref={workspaceMenuRef}>
          <button
            type="button"
            className={styles.iconButton}
            onClick={() => setWorkspaceMenuOpen(!workspaceMenuOpen)}
            aria-label="新建工作区"
            aria-expanded={workspaceMenuOpen}
            aria-haspopup="menu"
            title="新建工作区"
            data-testid="workspace-add-button"
          >
            <Plus size={16} />
            <ChevronDown size={10} />
          </button>
          {workspaceMenuOpen && (
            <div className={styles.workspaceMenu} role="menu" data-testid="workspace-add-menu">
              {props.workspaces.length > 0 && (
                <div className={styles.workspaceMenuHeader}>
                  <span>选择工作区</span>
                </div>
              )}
              {props.workspaces.map((ws) => (
                <button
                  key={ws.name}
                  type="button"
                  className={styles.workspaceMenuItem}
                  role="menuitem"
                  onClick={() =>
                    handleWorkspaceMenuItem(() => {
                      setArchiveView(false);
                      props.onWorkspaceChange(ws.name);
                    })
                  }
                >
                  {ws.name === props.activeWorkspace ? (
                    <Check size={12} />
                  ) : (
                    <Folder size={12} />
                  )}
                  <span>{ws.name}</span>
                </button>
              ))}
              {props.workspaces.length > 0 && <div className={styles.workspaceMenuDivider} />}
              <button
                type="button"
                className={styles.workspaceMenuItem}
                role="menuitem"
                onClick={() => handleWorkspaceMenuItem(() => setShowPicker(true))}
                data-testid="workspace-add-new"
              >
                <Plus size={12} />
                <span>Add workspace...</span>
              </button>
            </div>
          )}
        </div>
      </div>

      {/* T8: workspace 右键菜单（重命名 / 删除） */}
      {contextMenuFor && (
        <div
          ref={contextMenuRef}
          className={styles.workspaceContextMenu}
          role="menu"
          data-testid={`workspace-context-menu-${contextMenuFor}`}
        >
          <button
            type="button"
            className={styles.workspaceContextItem}
            role="menuitem"
            onClick={() => {
              const ws = props.workspaces.find((w) => w.name === contextMenuFor);
              if (ws) setRenameWsValue(ws.title ?? ws.name);
              setRenamingWs(contextMenuFor);
              setContextMenuFor(null);
            }}
            data-testid={`workspace-rename-btn-${contextMenuFor}`}
          >
            <Pencil size={12} /> <span>重命名</span>
          </button>
          <button
            type="button"
            className={`${styles.workspaceContextItem} ${styles.workspaceContextItemDanger}`}
            role="menuitem"
            onClick={() => {
              if (window.confirm(`确认删除工作区 ${contextMenuFor}？\n目录和会话日志不会被删除。`)) {
                props.onDeleteWorkspace(contextMenuFor);
              }
              setContextMenuFor(null);
            }}
            data-testid={`workspace-delete-btn-${contextMenuFor}`}
          >
            <Trash2 size={12} /> <span>删除</span>
          </button>
        </div>
      )}

      {/* T9: missing_dir workspace 重新连接 → 复用 picker 选新 path */}
      {reconnectFor && (
        <WorkspacePickerModal
          open={true}
          onClose={() => setReconnectFor(null)}
          onSubmit={async (path) => {
            props.onReconnectMissingWorkspace(reconnectFor, path);
            setReconnectFor(null);
          }}
        />
      )}

      {showPicker && (
        <WorkspacePickerModal
          open={showPicker}
          onClose={() => setShowPicker(false)}
          onSubmit={props.onCreateWorkspace}
        />
      )}

      <div className={styles.list}>
        {groupEntries.map(([workspace, list]) => {
          const isExpanded = expanded.has(workspace);
          const visibleList = isExpanded ? list : list.slice(0, DEFAULT_VISIBLE);
          const hiddenCount = list.length - visibleList.length;
          return (
            <div key={workspace} className={styles.group}>
              <div className={styles.workspaceHeader}>
                <Folder size={12} />
                <span>{groupLabel(workspace)}</span>
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

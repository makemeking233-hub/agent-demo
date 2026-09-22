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

/**
 * Sidebar 类名映射（shadcn-components-p2）：
 *  原 CSS Module 类 → 现有 Tailwind utility 串（合并到对应元素上）
 *  Sidebar.module.css（11 KB / 490 行）迁移后删除，所有视觉等价。
 */

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
      <button type="button" className="m-2 flex h-8 w-8 cursor-pointer items-center justify-center rounded-sm border border-border bg-transparent text-muted-foreground" onClick={toggle} aria-label="展开侧栏">
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
    <aside className="flex w-[260px] flex-col overflow-hidden border-r border-border bg-card">
      <button type="button" className="mx-2.5 mt-2.5 mb-1 flex cursor-pointer items-center justify-center gap-1.5 rounded-sm border border-border bg-secondary px-2 py-2 text-[13px] font-medium text-foreground hover:bg-accent-subtle" onClick={props.onNewSession}>
        <Plus size={16} />
        <span>新会话</span>
      </button>

      <div className="flex items-center justify-between border-b border-border px-4 pt-3 pb-2">
        <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">会话</span>
        <div className="inline-flex gap-1">
          <button
            type="button"
            className="inline-flex h-6 w-6 cursor-pointer items-center justify-center rounded-sm border-none bg-transparent text-muted-foreground hover:bg-secondary"
            onClick={() => setArchiveView(!archiveView)}
            aria-label={archiveView ? "返回会话列表" : "归档"}
            title={archiveView ? "返回会话列表" : "归档/回收站"}
          >
            <Archive size={14} />
          </button>
          <button type="button" className="inline-flex h-6 w-6 cursor-pointer items-center justify-center rounded-sm border-none bg-transparent text-muted-foreground hover:bg-secondary" onClick={toggle} aria-label="折叠侧栏">
            <PanelLeftClose size={16} />
          </button>
        </div>
      </div>

      {/* 工作区切换条 + Menu（对齐 dsh：列已有 workspaces + 「Add workspace...」项） */}
      <div className="flex items-center gap-1 border-b border-border px-3 py-1.5">
        <div className="flex min-w-0 flex-1 gap-0.5 overflow-x-auto">
          {props.workspaces.map((ws) => (
            renamingWs === ws.name ? (
              <input
                key={`rename-${ws.name}`}
                className="flex-1 rounded border border-primary bg-background px-2 py-1.5 text-xs text-foreground"
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
                className={
                  ws.status === "missing_dir"
                    ? "inline-flex items-center gap-1 whitespace-nowrap rounded-sm border bg-transparent px-1.5 py-1 text-[11px] text-destructive [text-decoration:line-through] [text-decoration-color:rgba(220,38,38,0.5)] cursor-pointer hover:bg-destructive/[0.08] !border-destructive"
                    : ws.name === props.activeWorkspace
                      ? "inline-flex items-center gap-1 whitespace-nowrap rounded-sm border bg-accent-subtle px-1.5 py-1 text-[11px] text-foreground !border-primary cursor-pointer hover:bg-secondary"
                      : "inline-flex items-center gap-1 whitespace-nowrap rounded-sm border border-transparent bg-transparent px-1.5 py-1 text-[11px] text-muted-foreground cursor-pointer hover:bg-secondary"
                }
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
                <span className="max-w-[120px] overflow-hidden text-ellipsis">{ws.title ?? ws.name}</span>
                <span className="opacity-70">{ws.sessionCount}</span>
                {ws.status === "missing_dir" && (
                  <button
                    type="button"
                    className="ml-1 inline-flex h-4 w-4 cursor-pointer items-center justify-center rounded-[3px] border border-destructive bg-destructive/10 p-0 text-destructive hover:bg-destructive/20"
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
        <div className="relative shrink-0" ref={workspaceMenuRef}>
          <button
            type="button"
            className="inline-flex h-6 w-6 cursor-pointer items-center justify-center rounded-sm border-none bg-transparent text-muted-foreground hover:bg-secondary"
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
            <div className="absolute top-[calc(100%+4px)] right-0 z-50 flex min-w-[200px] flex-col rounded-md border border-border bg-card p-1 shadow-[0_6px_24px_rgba(0,0,0,0.12)]" role="menu" data-testid="workspace-add-menu">
              {props.workspaces.length > 0 && (
                <div className="px-3 py-1.5 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                  <span>选择工作区</span>
                </div>
              )}
              {props.workspaces.map((ws) => (
                <button
                  key={ws.name}
                  type="button"
                  className="flex w-full cursor-pointer items-center gap-2 border-none bg-transparent px-3 py-1.5 text-left text-[13px] text-foreground hover:bg-secondary"
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
              {props.workspaces.length > 0 && <div className="mx-0 my-1 h-px bg-border" />}
              <button
                type="button"
                className="flex w-full cursor-pointer items-center gap-2 border-none bg-transparent px-3 py-1.5 text-left text-[13px] text-foreground hover:bg-secondary"
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
          className="absolute top-[calc(100%+4px)] right-0 z-[100] flex min-w-[140px] flex-col rounded-lg border border-border bg-background p-1 shadow-[0_6px_18px_rgba(0,0,0,0.12)]"
          role="menu"
          data-testid={`workspace-context-menu-${contextMenuFor}`}
        >
          <button
            type="button"
            className="flex w-full cursor-pointer items-center gap-2 rounded border-none bg-transparent px-2.5 py-1.5 text-left text-[13px] text-foreground hover:bg-secondary"
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
            className="flex w-full cursor-pointer items-center gap-2 rounded border-none bg-transparent px-2.5 py-1.5 text-left text-[13px] text-destructive hover:bg-destructive/[0.08]"
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

      <div className="flex-1 overflow-y-auto px-4 py-2">
        {groupEntries.map(([workspace, list]) => {
          const isExpanded = expanded.has(workspace);
          const visibleList = isExpanded ? list : list.slice(0, DEFAULT_VISIBLE);
          const hiddenCount = list.length - visibleList.length;
          return (
            <div key={workspace} className="mb-3">
              <div className="flex items-center gap-1.5 px-4 py-1 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                <Folder size={12} />
                <span>{groupLabel(workspace)}</span>
              </div>
              {visibleList.map((s) => (
                <div
                  key={s.id}
                  className={
                    s.id === props.currentSessionId
                      ? "flex w-full cursor-pointer items-stretch gap-2 border-l-2 border-primary bg-accent-subtle px-4 py-2"
                      : "flex w-full cursor-pointer items-stretch gap-2 border-l-2 border-transparent bg-transparent px-4 py-2 hover:bg-secondary"
                  }
                >
                  {renamingId === s.id ? (
                    <div className="flex min-w-0 flex-1 items-center gap-1">
                      <input
                        className="min-w-0 flex-1 rounded-sm border border-primary bg-background px-2 py-1 text-[13px] text-foreground"
                        value={renameValue}
                        onChange={(e) => setRenameValue(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === "Enter") submitRename();
                          if (e.key === "Escape") setRenamingId(null);
                        }}
                        autoFocus
                      />
                      <button type="button" className="inline-flex h-6 w-6 cursor-pointer items-center justify-center rounded-sm border-none bg-transparent text-muted-foreground hover:bg-secondary" onClick={submitRename} aria-label="确认重命名">
                        <Check size={13} />
                      </button>
                      <button type="button" className="inline-flex h-6 w-6 cursor-pointer items-center justify-center rounded-sm border-none bg-transparent text-muted-foreground hover:bg-secondary" onClick={() => setRenamingId(null)} aria-label="取消重命名">
                        <X size={13} />
                      </button>
                    </div>
                  ) : (
                    <>
                      <button
                        type="button"
                        className="flex min-w-0 flex-1 cursor-pointer items-center gap-2 border-none bg-transparent p-0 text-left text-inherit"
                        onClick={() => props.onSelect(s.id)}
                        title={s.preview || s.title}
                      >
                        <MessageSquare size={12} className="shrink-0 text-muted-foreground" />
                        <span className="min-w-0 flex-1 truncate text-[13px] font-medium">{s.title}</span>
                        <span className="ml-2 shrink-0 text-[11px] text-muted-foreground">{formatRelativeTime(s.time)}</span>
                      </button>
                      <div className="relative shrink-0">
                        <button
                          type="button"
                          className="inline-flex h-[26px] w-[26px] cursor-pointer items-center justify-center rounded-sm border-none bg-transparent text-muted-foreground hover:bg-secondary"
                          onClick={() => setMenuOpen(menuOpen === s.id ? null : s.id)}
                          aria-label="会话操作"
                          title="更多操作"
                        >
                          <MoreHorizontal size={14} />
                        </button>
                        {menuOpen === s.id && (
                          <div className="absolute right-0 top-full z-10 flex min-w-[110px] flex-col rounded-sm border border-border bg-card p-1 shadow-md">
                            {!archiveView && (
                              <button
                                type="button"
                                className="flex cursor-pointer items-center gap-1.5 border-none bg-transparent px-2 py-1.5 text-left text-xs text-foreground hover:bg-secondary"
                                onClick={() => startRename(s.id, s.title)}
                              >
                                <Pencil size={12} /> <span>重命名</span>
                              </button>
                            )}
                            <button
                              type="button"
                              className="flex cursor-pointer items-center gap-1.5 border-none bg-transparent px-2 py-1.5 text-left text-xs text-foreground hover:bg-secondary"
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
                <button type="button" className="block w-full cursor-pointer border-none bg-transparent px-4 py-1.5 text-left text-xs text-muted-foreground hover:text-primary" onClick={() => toggleExpand(workspace)}>
                  {isExpanded ? "收起" : `展开其余 ${hiddenCount} 个会话`}
                </button>
              )}
            </div>
          );
        })}
        {source.length === 0 && (
          <div className="px-4 py-6 text-center text-xs text-muted-foreground">
            <p>暂无会话</p>
          </div>
        )}
      </div>
    </aside>
  );
}

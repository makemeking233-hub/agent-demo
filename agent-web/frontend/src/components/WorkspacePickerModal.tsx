/**
 * WorkspacePickerModal（polish-workspace-picker-dsh-style）。
 *
 * <p>仿 DSH `Select Workspace Directory` 视觉：
 * 顶部 ←/→/↑ + 面包屑 + 显示隐藏；主区域左导航树 + 右文件列表；底部路径框 + name 输入。
 */

import {
  ChevronRight,
  CornerLeftUp,
  Eye,
  EyeOff,
  Folder,
  FolderPlus,
  HardDrive,
  Home,
  RefreshCw,
  X,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useReducer, useRef } from "react";
import {
  FsError,
  getDrives,
  getHome,
  getQuickAccess,
  listDir,
  mkdir,
  type FsDrive,
  type FsEntry,
  type FsQuickAccessItem,
} from "../api/fs";
import styles from "./WorkspacePickerModal.module.css";

const STORAGE_KEY = "agent-demo.workspace-picker.last-path";
const NAME_RE = /^[A-Za-z0-9._-]+$/;
const HISTORY_MAX = 50;

type SortBy = "name" | "mtime" | "type";
type SortDir = "asc" | "desc";

export interface WorkspacePickerModalProps {
  open: boolean;
  onClose: () => void;
  onSubmit: (name: string, dir: string) => Promise<void>;
}

interface State {
  currentPath: string;
  refreshCounter: number;
  entries: FsEntry[];
  loading: boolean;
  error: string | null;
  selectedPath: string | null;
  workspaceName: string;
  includeHidden: boolean;
  isCreatingWs: boolean;
  drives: FsDrive[];
  pathInput: string;
  showMkdir: boolean;
  mkdirName: string;
  mkdirError: string | null;
  // polish-workspace-picker-dsh-style
  history: string[];
  historyIndex: number;
  sortBy: SortBy;
  sortDir: SortDir;
  quickAccess: FsQuickAccessItem[];
}

type Action =
  | { type: "set-path-input"; value: string }
  | { type: "navigate"; path: string }
  | { type: "loaded"; entries: FsEntry[] }
  | { type: "set-loading"; loading: boolean }
  | { type: "set-error"; error: string | null }
  | { type: "select"; path: string }
  | { type: "set-name"; name: string }
  | { type: "toggle-hidden" }
  | { type: "set-creating"; creating: boolean }
  | { type: "set-drives"; drives: FsDrive[] }
  | { type: "show-mkdir"; show: boolean; error?: string }
  | { type: "set-mkdir-name"; name: string }
  | { type: "set-mkdir-error"; error: string | null }
  // polish-workspace-picker-dsh-style
  | { type: "back" }
  | { type: "forward" }
  | { type: "up" }
  | { type: "set-sort"; by: SortBy }
  | { type: "set-quick-access"; items: FsQuickAccessItem[] };

function reducer(state: State, action: Action): State {
  switch (action.type) {
    case "set-path-input":
      return { ...state, pathInput: action.value };
    case "navigate": {
      const newHistory = [
        ...state.history.slice(0, state.historyIndex + 1),
        action.path,
      ];
      const trimmed =
        newHistory.length > HISTORY_MAX
          ? newHistory.slice(newHistory.length - HISTORY_MAX)
          : newHistory;
      return {
        ...state,
        currentPath: action.path,
        pathInput: action.path,
        entries: [],
        loading: true,
        error: null,
        selectedPath: null,
        workspaceName: "",
        history: trimmed,
        historyIndex: trimmed.length - 1,
        refreshCounter:
          state.currentPath === action.path ? state.refreshCounter + 1 : state.refreshCounter,
      };
    }
    case "loaded":
      return { ...state, entries: action.entries, loading: false, error: null };
    case "set-loading":
      return { ...state, loading: action.loading };
    case "set-error":
      return { ...state, error: action.error, loading: false };
    case "select": {
      const name = basenameOf(action.path);
      return {
        ...state,
        selectedPath: action.path,
        workspaceName: name,
        error: null,
      };
    }
    case "set-name":
      return { ...state, workspaceName: action.name };
    case "toggle-hidden":
      return { ...state, includeHidden: !state.includeHidden };
    case "set-creating":
      return { ...state, isCreatingWs: action.creating };
    case "set-drives":
      return { ...state, drives: action.drives };
    case "show-mkdir":
      return {
        ...state,
        showMkdir: action.show,
        mkdirName: action.show ? "" : state.mkdirName,
        mkdirError: action.error ?? null,
      };
    case "set-mkdir-name":
      return { ...state, mkdirName: action.name };
    case "set-mkdir-error":
      return { ...state, mkdirError: action.error };
    case "back":
      if (state.historyIndex <= 0) return state;
      return {
        ...state,
        historyIndex: state.historyIndex - 1,
        currentPath: state.history[state.historyIndex - 1],
        pathInput: state.history[state.historyIndex - 1],
        entries: [],
        loading: true,
        error: null,
        selectedPath: null,
        workspaceName: "",
        refreshCounter: state.refreshCounter + 1,
      };
    case "forward":
      if (state.historyIndex >= state.history.length - 1) return state;
      return {
        ...state,
        historyIndex: state.historyIndex + 1,
        currentPath: state.history[state.historyIndex + 1],
        pathInput: state.history[state.historyIndex + 1],
        entries: [],
        loading: true,
        error: null,
        selectedPath: null,
        workspaceName: "",
        refreshCounter: state.refreshCounter + 1,
      };
    case "up": {
      const parent = parentOf(state.currentPath);
      if (!parent || parent === state.currentPath) return state;
      return reducer(state, { type: "navigate", path: parent });
    }
    case "set-sort": {
      // 同字段点击切换升降序；新字段默认升序
      if (state.sortBy === action.by) {
        return { ...state, sortDir: state.sortDir === "asc" ? "desc" : "asc" };
      }
      return { ...state, sortBy: action.by, sortDir: "asc" };
    }
    case "set-quick-access":
      return { ...state, quickAccess: action.items };
  }
}

const INITIAL: State = {
  currentPath: "",
  refreshCounter: 0,
  entries: [],
  loading: false,
  error: null,
  selectedPath: null,
  workspaceName: "",
  includeHidden: false,
  isCreatingWs: false,
  drives: [],
  pathInput: "",
  showMkdir: false,
  mkdirName: "",
  mkdirError: null,
  history: [],
  historyIndex: -1,
  sortBy: "name",
  sortDir: "asc",
  quickAccess: [],
};

function basenameOf(p: string): string {
  if (!p) return "";
  const m = p.match(/[^\\/]+$/);
  return m ? m[0] : "";
}

function parentOf(p: string): string | null {
  if (!p) return null;
  const idx = Math.max(p.lastIndexOf("\\"), p.lastIndexOf("/"));
  if (idx <= 0) return null;
  return p.slice(0, idx);
}

function errorMessage(err: unknown): string {
  if (err instanceof FsError) {
    switch (err.code) {
      case "path_not_absolute":
        return "路径必须是绝对路径";
      case "path_not_found":
        return "路径不存在";
      case "path_outside_home":
        return "路径不在家目录范围内";
      case "dir_exists":
        return "目录已存在";
      case "name_invalid":
        return "名称非法（仅允许字母数字 . _ -）";
      default:
        return `错误：${err.code}`;
    }
  }
  return (err as Error)?.message ?? "未知错误";
}

function joinPath(parent: string, child: string): string {
  const sep = parent.includes("\\") ? "\\" : "/";
  return parent.endsWith(sep) ? parent + child : parent + sep + child;
}

function sortEntries(
  entries: FsEntry[],
  by: SortBy,
  dir: SortDir,
): FsEntry[] {
  const out = entries.slice();
  const mult = dir === "asc" ? 1 : -1;
  out.sort((a, b) => {
    // 目录始终优先
    if (a.isDir !== b.isDir) return a.isDir ? -1 : 1;
    let cmp = 0;
    if (by === "name") {
      cmp = a.name.localeCompare(b.name, undefined, { sensitivity: "base" });
    } else if (by === "mtime") {
      cmp = a.mtime - b.mtime;
    } else {
      // type: 目录固定为 __dir__，文件按扩展名
      const ka = a.isDir ? "__dir__" : (a.name.split(".").pop() ?? "");
      const kb = b.isDir ? "__dir__" : (b.name.split(".").pop() ?? "");
      cmp = ka.localeCompare(kb, undefined, { sensitivity: "base" });
    }
    return cmp * mult;
  });
  return out;
}

export function WorkspacePickerModal({
  open,
  onClose,
  onSubmit,
}: WorkspacePickerModalProps) {
  const [state, dispatch] = useReducer(reducer, INITIAL);
  const overlayRef = useRef<HTMLDivElement | null>(null);

  // 打开时初始化：拉 home + drives + quick-access
  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    (async () => {
      try {
        const [home, drives, qa] = await Promise.all([
          getHome(),
          getDrives().catch(() => ({ drives: [] as FsDrive[] })),
          getQuickAccess().catch(() => ({ items: [] as FsQuickAccessItem[] })),
        ]);
        if (cancelled) return;
        const remembered = localStorage.getItem(STORAGE_KEY);
        const initial =
          remembered && remembered.startsWith(home.path) ? remembered : home.path;
        dispatch({ type: "set-drives", drives: drives.drives });
        dispatch({ type: "set-quick-access", items: qa.items });
        dispatch({ type: "navigate", path: initial });
      } catch (e) {
        dispatch({ type: "set-error", error: errorMessage(e) });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [open]);

  // 路径变化 / refresh / includeHidden 变化时拉列表
  useEffect(() => {
    if (!open || !state.currentPath) return;
    let cancelled = false;
    dispatch({ type: "set-loading", loading: true });
    listDir(state.currentPath, state.includeHidden)
      .then((r) => {
        if (cancelled) return;
        dispatch({ type: "loaded", entries: r.entries });
      })
      .catch((e) => {
        if (cancelled) return;
        dispatch({ type: "set-error", error: errorMessage(e) });
      });
    return () => {
      cancelled = true;
    };
  }, [open, state.currentPath, state.includeHidden, state.refreshCounter]);

  // Esc 关闭
  const handleClose = useCallback(() => {
    if (state.currentPath) {
      try {
        localStorage.setItem(STORAGE_KEY, state.currentPath);
      } catch {
        /* 静默 */
      }
    }
    onClose();
  }, [onClose, state.currentPath]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") handleClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, handleClose]);

  // 排序后的条目
  const sortedEntries = useMemo(
    () => sortEntries(state.entries, state.sortBy, state.sortDir),
    [state.entries, state.sortBy, state.sortDir],
  );

  const canBack = state.historyIndex > 0;
  const canForward = state.historyIndex < state.history.length - 1;
  const canUp = (() => {
    const p = parentOf(state.currentPath);
    return p && p !== state.currentPath;
  })();
  const isDrivesView = state.currentPath === "__drives__";

  const breadcrumbs = isDrivesView
    ? []
    : state.currentPath.split(/[\\/]/).filter(Boolean);

  const handlePathInputKey = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") {
      e.preventDefault();
      const v = state.pathInput.trim();
      if (!v) return;
      dispatch({ type: "navigate", path: v });
    }
  };

  const handleEntryClick = (entry: FsEntry) => {
    if (!entry.isDir) return;
    dispatch({ type: "select", path: entry.path });
  };

  const handleEntryDoubleClick = (entry: FsEntry) => {
    if (!entry.isDir) return;
    dispatch({ type: "navigate", path: entry.path });
  };

  const handleQuickAccessClick = (item: FsQuickAccessItem) => {
    dispatch({ type: "navigate", path: item.path });
  };

  const handleDriveClick = (drive: FsDrive) => {
    dispatch({ type: "navigate", path: drive.path });
  };

  const handleSubmit = async () => {
    if (!state.selectedPath) return;
    const name = state.workspaceName.trim();
    if (!name || !NAME_RE.test(name) || name.length > 64) {
      dispatch({ type: "set-error", error: "工作区名称非法" });
      return;
    }
    dispatch({ type: "set-creating", creating: true });
    try {
      await onSubmit(name, state.selectedPath);
      handleClose();
    } catch (e) {
      dispatch({ type: "set-error", error: errorMessage(e) });
    } finally {
      dispatch({ type: "set-creating", creating: false });
    }
  };

  const handleMkdirConfirm = async () => {
    const name = state.mkdirName.trim();
    if (!name || !NAME_RE.test(name)) {
      dispatch({ type: "set-mkdir-error", error: "名称非法" });
      return;
    }
    const fullPath = joinPath(state.currentPath, name);
    try {
      await mkdir(fullPath);
      dispatch({ type: "show-mkdir", show: false });
      dispatch({ type: "navigate", path: state.currentPath });
    } catch (e) {
      dispatch({ type: "set-mkdir-error", error: errorMessage(e) });
    }
  };

  const canSubmit =
    !!state.selectedPath &&
    !!state.workspaceName.trim() &&
    NAME_RE.test(state.workspaceName.trim()) &&
    state.workspaceName.trim().length <= 64 &&
    !state.isCreatingWs;

  if (!open) return null;

  return (
    <div
      className={styles.overlay}
      ref={overlayRef}
      onClick={(e) => {
        if (e.target === overlayRef.current) handleClose();
      }}
      role="dialog"
      aria-modal="true"
      aria-label="选择工作区目录"
    >
      <div className={styles.modal}>
        <header className={styles.header}>
          <span className={styles.title}>Select Workspace Directory</span>
          <button
            type="button"
            className={styles.iconButton}
            onClick={handleClose}
            aria-label="关闭"
          >
            <X size={16} />
          </button>
        </header>

        <div className={styles.toolbar}>
          <div className={styles.historyButtons}>
            <button
              type="button"
              className={styles.iconButton}
              onClick={() => dispatch({ type: "back" })}
              disabled={!canBack}
              aria-label="后退"
              title="后退"
            >
              ←
            </button>
            <button
              type="button"
              className={styles.iconButton}
              onClick={() => dispatch({ type: "forward" })}
              disabled={!canForward}
              aria-label="前进"
              title="前进"
            >
              →
            </button>
            <button
              type="button"
              className={styles.iconButton}
              onClick={() => dispatch({ type: "up" })}
              disabled={!canUp}
              aria-label="上一级"
              title="上一级"
            >
              <CornerLeftUp size={14} />
            </button>
          </div>

          <nav className={styles.breadcrumb} aria-label="面包屑">
            <button
              type="button"
              className={styles.breadcrumbItem}
              onClick={() =>
                dispatch({
                  type: "navigate",
                  path: state.quickAccess[0]?.path ?? "",
                })
              }
            >
              <Home size={12} /> Home
            </button>
            {!isDrivesView &&
              breadcrumbs.slice(1).map((seg, i) => {
                const fullPath = state.currentPath
                  .split(/[\\/]/)
                  .slice(0, i + 2)
                  .join(state.currentPath.includes("\\") ? "\\" : "/");
                return (
                  <span key={`${seg}-${i}`} className={styles.breadcrumbPart}>
                    <ChevronRight size={12} className={styles.breadcrumbSep} />
                    <button
                      type="button"
                      className={styles.breadcrumbItem}
                      onClick={() => dispatch({ type: "navigate", path: fullPath })}
                    >
                      {seg}
                    </button>
                  </span>
                );
              })}
          </nav>

          <button
            type="button"
            className={`${styles.iconButton} ${styles.showHiddenToggle}`}
            onClick={() => dispatch({ type: "toggle-hidden" })}
            aria-label={state.includeHidden ? "隐藏文件" : "显示隐藏文件"}
            title={state.includeHidden ? "隐藏文件" : "显示隐藏文件"}
          >
            {state.includeHidden ? <EyeOff size={14} /> : <Eye size={14} />}
          </button>

          <button
            type="button"
            className={styles.iconButton}
            onClick={() =>
              dispatch({ type: "navigate", path: state.currentPath })
            }
            aria-label="刷新"
            title="刷新"
          >
            <RefreshCw size={14} />
          </button>

          <button
            type="button"
            className={styles.toolButton}
            onClick={() => dispatch({ type: "show-mkdir", show: true })}
            disabled={isDrivesView}
          >
            <FolderPlus size={14} /> 新建文件夹
          </button>
        </div>

        {state.showMkdir && (
          <div className={styles.mkdirRow}>
            <input
              className={styles.pathInput}
              value={state.mkdirName}
              onChange={(e) =>
                dispatch({ type: "set-mkdir-name", name: e.target.value })
              }
              onKeyDown={(e) => {
                if (e.key === "Enter") handleMkdirConfirm();
                if (e.key === "Escape")
                  dispatch({ type: "show-mkdir", show: false });
              }}
              placeholder="新文件夹名"
              autoFocus
            />
            <button
              type="button"
              className={styles.confirmYes}
              onClick={handleMkdirConfirm}
            >
              创建
            </button>
            <button
              type="button"
              className={styles.confirmNo}
              onClick={() => dispatch({ type: "show-mkdir", show: false })}
            >
              取消
            </button>
            {state.mkdirError && (
              <span className={styles.errorText}>{state.mkdirError}</span>
            )}
          </div>
        )}

        {state.error && <div className={styles.errorBanner}>{state.error}</div>}

        <div className={styles.main}>
          <aside className={styles.tree} aria-label="导航树">
            <div className={styles.treeSection}>
              <div className={styles.treeSectionTitle}>快速访问</div>
              {state.quickAccess.map((item) => (
                <button
                  key={item.path}
                  type="button"
                  className={`${styles.treeItem} ${
                    state.currentPath === item.path ? styles.treeItemActive : ""
                  }`}
                  onClick={() => handleQuickAccessClick(item)}
                  title={item.path}
                >
                  {item.name === "Home" ? (
                    <Home size={13} className={styles.treeItemIcon} />
                  ) : (
                    <Folder size={13} className={styles.treeItemIcon} />
                  )}
                  <span>{item.name}</span>
                </button>
              ))}
            </div>
            {state.drives.length > 0 && (
              <div className={styles.treeSection}>
                <div className={styles.treeSectionTitle}>此电脑</div>
                {state.drives.map((d) => (
                  <button
                    key={d.path}
                    type="button"
                    className={styles.treeItem}
                    onClick={() => handleDriveClick(d)}
                    title={d.path}
                  >
                    <HardDrive size={13} className={styles.treeItemIcon} />
                    <span>{d.name}</span>
                  </button>
                ))}
              </div>
            )}
          </aside>

          <div className={styles.list}>
            <div className={styles.listHeader}>
              <button
                type="button"
                className={`${styles.listHeaderCell} ${styles.listHeaderName}`}
                onClick={() => dispatch({ type: "set-sort", by: "name" })}
              >
                名称{state.sortBy === "name" && (state.sortDir === "asc" ? " ↑" : " ↓")}
              </button>
              <button
                type="button"
                className={`${styles.listHeaderCell} ${styles.listHeaderMtime}`}
                onClick={() => dispatch({ type: "set-sort", by: "mtime" })}
              >
                修改时间{state.sortBy === "mtime" && (state.sortDir === "asc" ? " ↑" : " ↓")}
              </button>
              <button
                type="button"
                className={`${styles.listHeaderCell} ${styles.listHeaderType}`}
                onClick={() => dispatch({ type: "set-sort", by: "type" })}
              >
                类型{state.sortBy === "type" && (state.sortDir === "asc" ? " ↑" : " ↓")}
              </button>
            </div>

            <div className={styles.entryList} role="list">
              {!isDrivesView && (
                <button
                  type="button"
                  className={styles.entryItem}
                  onClick={() => dispatch({ type: "up" })}
                >
                  <Folder size={14} className={styles.entryIcon} /> ..
                </button>
              )}
              {sortedEntries.map((e) => (
                <button
                  key={e.path}
                  type="button"
                  className={`${styles.entryItem} ${
                    state.selectedPath === e.path ? styles.entrySelected : ""
                  } ${!e.isDir ? styles.entryDisabled : ""}`}
                  onClick={() => handleEntryClick(e)}
                  onDoubleClick={() => handleEntryDoubleClick(e)}
                  disabled={!e.isDir}
                  title={e.path}
                >
                  {e.isDir ? (
                    <Folder size={14} className={styles.entryIcon} />
                  ) : (
                    <span className={styles.fileIcon}>📄</span>
                  )}
                  <span className={styles.entryName}>{e.name}</span>
                  <span className={styles.entryMtime}>
                    {e.mtime ? new Date(e.mtime).toLocaleString() : ""}
                  </span>
                  <span className={styles.entryType}>
                    {e.isDir ? "文件夹" : "文件"}
                  </span>
                </button>
              ))}
              {!isDrivesView &&
                sortedEntries.length === 0 &&
                !state.loading &&
                !state.error && <div className={styles.emptyHint}>此目录为空</div>}
              {state.loading && <div className={styles.loadingHint}>加载中…</div>}
            </div>
          </div>
        </div>

        <footer className={styles.footer}>
          <div className={styles.footerRow}>
            <span className={styles.footerLabel}>文件夹:</span>
            <input
              className={styles.pathInput}
              value={state.pathInput}
              onChange={(e) =>
                dispatch({ type: "set-path-input", value: e.target.value })
              }
              onKeyDown={handlePathInputKey}
              placeholder="输入绝对路径后按 Enter 跳转"
              aria-label="文件夹路径"
            />
          </div>
          <div className={styles.footerRow}>
            <span className={styles.footerLabel}>工作区名称:</span>
            <input
              className={styles.workspaceNameInput}
              value={state.workspaceName}
              onChange={(e) => dispatch({ type: "set-name", name: e.target.value })}
              placeholder="md-main"
              aria-label="工作区名称"
            />
            <div className={styles.footerActions}>
              <button
                type="button"
                className={styles.confirmNo}
                onClick={handleClose}
              >
                取消
              </button>
              <button
                type="button"
                className={styles.confirmYes}
                onClick={handleSubmit}
                disabled={!canSubmit}
              >
                {state.isCreatingWs ? "创建中…" : "选择此目录"}
              </button>
            </div>
          </div>
        </footer>
      </div>
    </div>
  );
}

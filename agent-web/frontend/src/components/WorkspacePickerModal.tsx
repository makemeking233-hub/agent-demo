/**
 * WorkspacePickerModal（add-workspace-picker-modal）。
 *
 * <p>仿 DSH 文件选择器布局的目录选择 Modal：路径框 + 面包屑 + 工具栏 + 条目列表 + 提交区。
 * 默认定位到 {@code localStorage["agent-demo.workspace-picker.last-path"]} 或家目录。
 *
 * <p>props.onSubmit 成功后由父组件负责刷新 + 切换工作区（设计 D5/D6）。
 */

import {
  ChevronRight,
  Eye,
  EyeOff,
  Folder,
  FolderPlus,
  RefreshCw,
  X,
} from "lucide-react";
import { useCallback, useEffect, useReducer, useRef } from "react";
import { FsError, getDrives, getHome, listDir, mkdir, type FsDrive, type FsEntry } from "../api/fs";
import styles from "./WorkspacePickerModal.module.css";

const STORAGE_KEY = "agent-demo.workspace-picker.last-path";
const NAME_RE = /^[A-Za-z0-9._-]+$/;

export interface WorkspacePickerModalProps {
  open: boolean;
  onClose: () => void;
  /** 提交回调（创建工作区）。父组件负责 POST + 刷新 + 切换。 */
  onSubmit: (name: string, dir: string) => Promise<void>;
}

interface State {
  currentPath: string;
  /** 递增计数器，让 effect 在 refresh 时（currentPath 不变）也重新拉列表。 */
  refreshCounter: number;
  entries: FsEntry[];
  loading: boolean;
  error: string | null;
  selectedPath: string | null;
  workspaceName: string;
  includeHidden: boolean;
  isCreatingWs: boolean;
  drives: FsDrive[];
  /** 顶部路径框当前输入内容（与 currentPath 解耦，支持尚未跳转的输入）。 */
  pathInput: string;
  /** 新建文件夹 inline 输入态。 */
  showMkdir: boolean;
  mkdirName: string;
  mkdirError: string | null;
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
  | { type: "set-mkdir-error"; error: string | null };

function reducer(state: State, action: Action): State {
  switch (action.type) {
    case "set-path-input":
      return { ...state, pathInput: action.value };
    case "navigate":
      return {
        ...state,
        currentPath: action.path,
        pathInput: action.path,
        entries: [],
        loading: true,
        error: null,
        selectedPath: null,
        workspaceName: "",
        // 同路径 refresh 时递增 counter，触发 effect 重新拉列表
        refreshCounter:
          state.currentPath === action.path
            ? state.refreshCounter + 1
            : state.refreshCounter,
      };
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
};

function basenameOf(p: string): string {
  if (!p) return "";
  const m = p.match(/[^\\/]+$/);
  return m ? m[0] : "";
}

function parentOf(p: string): string | null {
  if (!p) return null;
  const idx = Math.max(p.lastIndexOf("\\"), p.lastIndexOf("/"));
  return idx <= 0 ? null : p.slice(0, idx);
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

export function WorkspacePickerModal({
  open,
  onClose,
  onSubmit,
}: WorkspacePickerModalProps) {
  const [state, dispatch] = useReducer(reducer, INITIAL);
  const overlayRef = useRef<HTMLDivElement | null>(null);

  // 打开时初始化路径
  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    (async () => {
      try {
        const [home, drives] = await Promise.all([getHome(), getDrives().catch(() => ({ drives: [] }))]);
        if (cancelled) return;
        const remembered = localStorage.getItem(STORAGE_KEY);
        const initial = remembered && remembered.startsWith(home.path) ? remembered : home.path;
        dispatch({ type: "set-drives", drives: drives.drives });
        dispatch({ type: "navigate", path: initial });
      } catch (e) {
        dispatch({ type: "set-error", error: errorMessage(e) });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [open]);

  // 路径变化或 refresh 时拉列表
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

  // Esc 关闭（依赖 handleClose，让 handleClose 拿到最新的 currentPath）
  const handleClose = useCallback(() => {
    if (state.currentPath) {
      try {
        localStorage.setItem(STORAGE_KEY, state.currentPath);
      } catch {
        /* 静默失败 */
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

  const handleRefresh = useCallback(() => {
    dispatch({ type: "navigate", path: state.currentPath });
  }, [state.currentPath]);

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

  const handleParentNav = () => {
    const p = parentOf(state.currentPath);
    if (p) dispatch({ type: "navigate", path: p });
  };

  const handleDrillDown = (path: string) => {
    dispatch({ type: "navigate", path });
  };

  const handleShowThisPc = () => {
    // "此电脑"层：仅 Windows 有意义；Linux/macOS 直接跳回 home
    if (state.drives.length > 0) {
      // 切到第一个盘符列表视图 —— 用临时路径表示
      dispatch({ type: "navigate", path: "__drives__" });
    }
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
      handleRefresh();
    } catch (e) {
      dispatch({ type: "set-mkdir-error", error: errorMessage(e) });
    }
  };

  if (!open) return null;

  // 面包屑段
  const breadcrumbs = state.currentPath === "__drives__"
    ? []
    : state.currentPath.split(/[\\/]/).filter(Boolean);

  const canSubmit =
    !!state.selectedPath &&
    !!state.workspaceName.trim() &&
    NAME_RE.test(state.workspaceName.trim()) &&
    state.workspaceName.trim().length <= 64 &&
    !state.isCreatingWs;

  return (
    <div
      className={styles.overlay}
      ref={overlayRef}
      onClick={(e) => {
        if (e.target === overlayRef.current) handleClose();
      }}
    >
      <div className={styles.modal} role="dialog" aria-modal="true" aria-label="选择工作区目录">
        <header className={styles.header}>
          <span className={styles.title}>选择工作区目录</span>
          <button type="button" className={styles.iconButton} onClick={handleClose} aria-label="关闭">
            <X size={16} />
          </button>
        </header>

        <div className={styles.pathRow}>
          <input
            className={styles.pathInput}
            value={state.pathInput}
            onChange={(e) => dispatch({ type: "set-path-input", value: e.target.value })}
            onKeyDown={handlePathInputKey}
            placeholder="输入绝对路径后按 Enter 跳转"
            aria-label="路径输入框"
          />
          <button
            type="button"
            className={styles.pathJump}
            onClick={() => dispatch({ type: "navigate", path: state.pathInput.trim() })}
            disabled={!state.pathInput.trim()}
          >
            跳转
          </button>
        </div>

        <nav className={styles.breadcrumb} aria-label="面包屑">
          <button
            type="button"
            className={styles.breadcrumbItem}
            onClick={handleShowThisPc}
            disabled={state.drives.length === 0 && breadcrumbs.length === 0}
          >
            此电脑
          </button>
          {breadcrumbs.length > 0 && state.currentPath !== "__drives__" && (
            <ChevronRight size={12} className={styles.breadcrumbSep} />
          )}
          {breadcrumbs.map((seg, i) => {
            const fullPath = reconstructPath(state.currentPath, i);
            const isLast = i === breadcrumbs.length - 1;
            return (
              <span key={`${seg}-${i}`} className={styles.breadcrumbPart}>
                <button
                  type="button"
                  className={`${styles.breadcrumbItem} ${isLast ? styles.breadcrumbCurrent : ""}`}
                  onClick={() => !isLast && handleDrillDown(fullPath)}
                  disabled={isLast}
                >
                  {seg}
                </button>
                {!isLast && <ChevronRight size={12} className={styles.breadcrumbSep} />}
              </span>
            );
          })}
        </nav>

        <div className={styles.toolbar}>
          <button type="button" className={styles.toolButton} onClick={() => dispatch({ type: "show-mkdir", show: true })} disabled={state.currentPath === "__drives__"}>
            <FolderPlus size={14} /> 新建文件夹
          </button>
          <button type="button" className={styles.toolButton} onClick={handleRefresh} aria-label="刷新">
            <RefreshCw size={14} />
          </button>
          <button type="button" className={styles.toolButton} onClick={() => dispatch({ type: "toggle-hidden" })} aria-label={state.includeHidden ? "隐藏文件" : "显示文件"}>
            {state.includeHidden ? <EyeOff size={14} /> : <Eye size={14} />}
            {state.includeHidden ? "隐藏文件" : "显示隐藏"}
          </button>
        </div>

        {state.showMkdir && (
          <div className={styles.mkdirRow}>
            <input
              className={styles.pathInput}
              value={state.mkdirName}
              onChange={(e) => dispatch({ type: "set-mkdir-name", name: e.target.value })}
              onKeyDown={(e) => {
                if (e.key === "Enter") handleMkdirConfirm();
                if (e.key === "Escape") dispatch({ type: "show-mkdir", show: false });
              }}
              placeholder="新文件夹名"
              autoFocus
            />
            <button type="button" className={styles.confirmYes} onClick={handleMkdirConfirm}>
              创建
            </button>
            <button type="button" className={styles.confirmNo} onClick={() => dispatch({ type: "show-mkdir", show: false })}>
              取消
            </button>
            {state.mkdirError && <span className={styles.errorText}>{state.mkdirError}</span>}
          </div>
        )}

        {state.error && <div className={styles.errorBanner}>{state.error}</div>}

        <div className={styles.entryList} role="list">
          {state.currentPath !== "__drives__" && (
            <button type="button" className={styles.entryItem} onClick={handleParentNav}>
              <Folder size={14} className={styles.entryIcon} /> ..
            </button>
          )}
          {state.currentPath === "__drives__" &&
            state.drives.map((d) => (
              <button
                key={d.path}
                type="button"
                className={styles.entryItem}
                onClick={() => dispatch({ type: "select", path: d.path })}
                onDoubleClick={() => dispatch({ type: "navigate", path: d.path })}
              >
                <Folder size={14} className={styles.entryIcon} /> {d.name}
              </button>
            ))}
          {state.currentPath !== "__drives__" &&
            state.entries.map((e) => (
              <button
                key={e.path}
                type="button"
                className={`${styles.entryItem} ${state.selectedPath === e.path ? styles.entrySelected : ""} ${
                  !e.isDir ? styles.entryDisabled : ""
                }`}
                onClick={() => handleEntryClick(e)}
                onDoubleClick={() => handleEntryDoubleClick(e)}
                disabled={!e.isDir}
                title={e.path}
              >
                {e.isDir ? <Folder size={14} className={styles.entryIcon} /> : <span className={styles.fileIcon}>📄</span>}
                <span className={styles.entryName}>{e.name}</span>
                <span className={styles.entryMeta}>{formatSize(e.size)}</span>
              </button>
            ))}
          {state.currentPath !== "__drives__" && state.entries.length === 0 && !state.loading && !state.error && (
            <div className={styles.emptyHint}>此目录为空</div>
          )}
          {state.loading && <div className={styles.loadingHint}>加载中…</div>}
        </div>

        <footer className={styles.footer}>
          <div className={styles.footerPath}>
            <span className={styles.footerLabel}>当前路径：</span>
            <span className={styles.footerValue}>{state.selectedPath ?? state.currentPath ?? "—"}</span>
          </div>
          <div className={styles.footerNameRow}>
            <span className={styles.footerLabel}>工作区名称：</span>
            <input
              className={styles.workspaceNameInput}
              value={state.workspaceName}
              onChange={(e) => dispatch({ type: "set-name", name: e.target.value })}
              placeholder="md-main"
              aria-label="工作区名称"
            />
          </div>
          <div className={styles.footerActions}>
            <button type="button" className={styles.confirmYes} onClick={handleSubmit} disabled={!canSubmit}>
              {state.isCreatingWs ? "创建中…" : "选择此目录"}
            </button>
            <button type="button" className={styles.confirmNo} onClick={handleClose}>
              取消
            </button>
          </div>
        </footer>
      </div>
    </div>
  );
}

function formatSize(bytes: number): string {
  if (bytes === 0) return "—";
  const units = ["B", "KB", "MB", "GB"];
  let i = 0;
  let v = bytes;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i++;
  }
  return `${v.toFixed(v < 10 ? 1 : 0)} ${units[i]}`;
}

function joinPath(parent: string, child: string): string {
  if (parent === "__drives__") return child;
  const sep = parent.includes("\\") ? "\\" : "/";
  return parent.endsWith(sep) ? parent + child : parent + sep + child;
}

/** 从完整路径与分隔符位置重组面包屑点击的路径。 */
function reconstructPath(full: string, segmentIndex: number): string {
  const sep = full.includes("\\") ? "\\" : "/";
  const parts = full.split(/[\\/]/).filter(Boolean);
  return parts.slice(0, segmentIndex + 1).join(sep);
}

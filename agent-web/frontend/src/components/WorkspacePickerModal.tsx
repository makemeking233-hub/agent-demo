/**
 * WorkspacePickerModal（picker-async）。
 *
 * 异步 picker 流程：
 *  1. 点"选择文件夹..." → POST /api/workspaces/pick-folder → 立即返回 202 + task_id
 *  2. 启动 polling（500ms 间隔，30s 后切到 2s 间隔）
 *  3. status="done" → 填路径 + 自动 basename
 *  4. status="cancelled" / "timeout" → 不提示 / 提示超时
 *  5. 关闭 modal → DELETE task_id（destroy OS process）
 *
 * 另提供"在资源管理器中显示"按钮 → 调 /api/settings/reveal（复用现有端点）。
 */

import { ExternalLink, FolderSearch, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { cancelPickFolder, pollPickFolder, startPickFolder } from "../api/workspace";
import styles from "./WorkspacePickerModal.module.css";

const STORAGE_KEY = "agent-demo.workspace-picker.last-path";
const NAME_RE = /^[A-Za-z0-9._-]+$/;
const POLL_FAST_MS = 500;
const POLL_SLOW_MS = 2000;
const POLL_SWITCH_AFTER_MS = 30_000;

function basenameOf(p: string): string {
  if (!p) return "";
  const m = p.match(/[^\\/]+$/);
  return m ? m[0] : "";
}

async function callReveal(): Promise<void> {
  const r = await fetch("/api/settings/reveal", { method: "POST" });
  if (!r.ok) throw new Error(`reveal ${r.status}`);
}

export interface WorkspacePickerModalProps {
  open: boolean;
  onClose: () => void;
  onSubmit: (name: string, dir: string) => Promise<void>;
}

export function WorkspacePickerModal({ open, onClose, onSubmit }: WorkspacePickerModalProps) {
  const [selectedPath, setSelectedPath] = useState<string>("");
  const [workspaceName, setWorkspaceName] = useState<string>("");
  const [picking, setPicking] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [taskId, setTaskId] = useState<string | null>(null);
  const overlayRef = useRef<HTMLDivElement | null>(null);
  const taskIdRef = useRef<string | null>(null);

  useEffect(() => {
    taskIdRef.current = taskId;
  }, [taskId]);

  // 取消任务（关闭 modal 时）
  useEffect(() => {
    return () => {
      // 组件卸载时若有未完成任务，尝试取消
      // （注意：这里读不到 taskId 闭包；modal 关闭路径会在 onClose 前显式 cancel）
    };
  }, []);

  // Esc 关闭
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        if (taskId) void cancelPickFolder(taskId);
        onClose();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose, taskId]);

  // 打开时恢复上次路径
  useEffect(() => {
    if (!open) {
      setSelectedPath("");
      setWorkspaceName("");
      setError(null);
      return;
    }
    try {
      const last = localStorage.getItem(STORAGE_KEY);
      if (last) setSelectedPath(last);
    } catch {
      /* ignore */
    }
  }, [open]);

  async function handlePickFolder() {
    setPicking(true);
    setError(null);
    const signal = { aborted: false };
    try {
      const start = await startPickFolder();
      setTaskId(start.task_id);
      await pollUntilDone(start.task_id, signal);
    } catch (e) {
      setError("调起资源管理器失败：" + (e as Error).message);
    } finally {
      signal.aborted = true;
      setPicking(false);
    }
  }

  /**
   * 轮询直到 status != "running"；超时后切换到 2s 间隔。
   * - "done" → 填路径 + 自动 basename
   * - "cancelled" → 静默（用户主动取消）
   * - "timeout" / "error" / "invalid_path" → 显示错误
   */
  async function pollUntilDone(id: string, signal: { aborted: boolean }): Promise<void> {
    const start = Date.now();
    while (Date.now() - start < POLL_SWITCH_AFTER_MS + 5 * 60 * 1000) {
      if (signal.aborted) return;
      try {
        const status = await pollPickFolder(id);
        if (signal.aborted) return;
        if (status.status === "done" && status.path) {
          setSelectedPath(status.path);
          setWorkspaceName((cur) => cur || basenameOf(status.path!));
          try {
            localStorage.setItem(STORAGE_KEY, status.path);
          } catch {
            /* ignore */
          }
          setTaskId(null);
          return;
        }
        if (status.status === "cancelled") {
          setTaskId(null);
          return;
        }
        if (status.status === "timeout") {
          setError("操作超时，请重试");
          setTaskId(null);
          return;
        }
        if (status.status === "invalid_path") {
          setError("选定路径无效");
          setTaskId(null);
          return;
        }
        if (status.status === "error") {
          setError("操作失败：" + (status.reason ?? "未知"));
          setTaskId(null);
          return;
        }
        if (status.status === "unknown") {
          setTaskId(null);
          return;
        }
      } catch {
        // 网络错误：继续轮询
      }
      const interval = Date.now() - start < POLL_SWITCH_AFTER_MS ? POLL_FAST_MS : POLL_SLOW_MS;
      await new Promise((r) => setTimeout(r, interval));
    }
  }

  async function handleReveal() {
    setError(null);
    try {
      await callReveal();
    } catch (e) {
      setError("reveal 失败：" + (e as Error).message);
    }
  }

  function handleOverlayClick() {
    if (taskId) void cancelPickFolder(taskId);
    onClose();
  }

  async function handleSubmit() {
    const name = workspaceName.trim();
    if (!selectedPath || !name || !NAME_RE.test(name) || name.length > 64) {
      setError("工作区名称非法");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await onSubmit(name, selectedPath);
      onClose();
    } catch (e) {
      setError((e as Error).message ?? "未知错误");
    } finally {
      setSubmitting(false);
    }
  }

  const canSubmit =
    !!selectedPath &&
    !!workspaceName.trim() &&
    NAME_RE.test(workspaceName.trim()) &&
    workspaceName.trim().length <= 64 &&
    !submitting;

  if (!open) return null;

  return (
    <div
      className={styles.wpOverlay}
      ref={overlayRef}
      onClick={(e) => {
        if (e.target === overlayRef.current) handleOverlayClick();
      }}
      role="dialog"
      aria-modal="true"
      aria-label="选择工作区目录"
    >
      <div className={styles.wpModal}>
        <header className={styles.wpHeader}>
          <span className={styles.wpTitle}>Select Workspace Directory</span>
          <button
            type="button"
            className={styles.wpIconButton}
            onClick={() => {
              if (taskId) void cancelPickFolder(taskId);
              onClose();
            }}
            aria-label="关闭"
          >
            <X size={16} />
          </button>
        </header>

        {error && <div className={styles.wpErrorBanner}>{error}</div>}

        <div className={styles.wpBody}>
          <div className={styles.wpRow}>
            <label className={styles.wpLabel}>文件夹：</label>
            <div className={styles.wpPathRow}>
              <input
                className={styles.wpPathInput}
                value={selectedPath}
                onChange={(e) => setSelectedPath(e.target.value)}
                placeholder="点击右侧按钮调起资源管理器选择文件夹..."
                aria-label="文件夹路径"
                data-testid="wp-path-input"
              />
              <button
                type="button"
                className={styles.wpPickButton}
                onClick={handlePickFolder}
                disabled={picking}
                data-testid="wp-pick-folder"
              >
                <FolderSearch size={14} />
                {picking ? "选择中..." : "选择文件夹..."}
              </button>
              <button
                type="button"
                className={styles.wpRevealButton}
                onClick={handleReveal}
                title="在资源管理器中显示（reveal 父目录后手动定位）"
                data-testid="wp-reveal"
              >
                <ExternalLink size={14} />
              </button>
            </div>
          </div>

          <div className={styles.wpRow}>
            <label className={styles.wpLabel}>工作区名称：</label>
            <input
              className={styles.wpNameInput}
              value={workspaceName}
              onChange={(e) => setWorkspaceName(e.target.value)}
              placeholder="md-main"
              aria-label="工作区名称"
              data-testid="wp-name-input"
            />
          </div>
        </div>

        <footer className={styles.wpFooter}>
          <button type="button" className={styles.wpCancel} onClick={handleOverlayClick}>
            取消
          </button>
          <button
            type="button"
            className={styles.wpSubmit}
            onClick={handleSubmit}
            disabled={!canSubmit}
            data-testid="wp-submit"
          >
            {submitting ? "创建中..." : "选择此目录"}
          </button>
        </footer>
      </div>
    </div>
  );
}

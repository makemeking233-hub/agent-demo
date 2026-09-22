/**
 * WorkspacePickerModal（picker-async + align-dsh-workspace）。
 *
 * <p>DSH 单 action：用户只选 path，name + title 由后端从 basename(path) 派生。
 *
 * <p>异步 picker 流程：
 *  1. 点"选择文件夹..." → POST /api/workspaces/pick-folder → 立即返回 202 + task_id
 *  2. 启动 polling（500ms 间隔，30s 后切到 2s 间隔）
 *  3. status="done" → 调 onSubmit(path) → 关闭
 *  4. status="cancelled" / "timeout" → 不提示 / 提示超时
 *  5. 关闭 modal → DELETE task_id（destroy OS process）
 *
 * <p>另提供"在资源管理器中显示"按钮 → 调 /api/settings/reveal（复用现有端点）。
 */

import { ExternalLink, FolderSearch, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { cancelPickFolder, pollPickFolder, startPickFolder } from "../api/workspace";

const STORAGE_KEY = "agent-demo.workspace-picker.last-path";
const POLL_FAST_MS = 500;
const POLL_SLOW_MS = 2000;
const POLL_SWITCH_AFTER_MS = 30_000;
/** picking 状态超过此秒数仍无 done/error/cancelled 时显示"对话框可能未显示"提示（fix-picker-hint） */
const PICKING_HINT_AFTER_MS = 3000;

async function callReveal(): Promise<void> {
  const r = await fetch("/api/settings/reveal", { method: "POST" });
  if (!r.ok) throw new Error(`reveal ${r.status}`);
}

export interface WorkspacePickerModalProps {
  open: boolean;
  onClose: () => void;
  /** align-dsh-workspace v2: 只传 path（name + title 由后端派生） */
  onSubmit: (path: string) => Promise<void>;
}

export function WorkspacePickerModal({ open, onClose, onSubmit }: WorkspacePickerModalProps) {
  const [selectedPath, setSelectedPath] = useState<string>("");
  const [picking, setPicking] = useState(false);
  /** picking=true 持续 PICKING_HINT_AFTER_MS 后置 true；picking=false 时立即清掉（fix-picker-hint） */
  const [pickingHint, setPickingHint] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [taskId, setTaskId] = useState<string | null>(null);
  const overlayRef = useRef<HTMLDivElement | null>(null);
  const taskIdRef = useRef<string | null>(null);

  useEffect(() => {
    taskIdRef.current = taskId;
  }, [taskId]);

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

  // 打开时恢复上次路径；关闭时重置
  useEffect(() => {
    if (!open) {
      setSelectedPath("");
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

  // picking 持续过久提示（fix-picker-hint）
  useEffect(() => {
    if (!picking) {
      setPickingHint(false);
      return;
    }
    const t = setTimeout(() => setPickingHint(true), PICKING_HINT_AFTER_MS);
    return () => clearTimeout(t);
  }, [picking]);

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

  async function pollUntilDone(id: string, signal: { aborted: boolean }): Promise<void> {
    const start = Date.now();
    while (Date.now() - start < POLL_SWITCH_AFTER_MS + 5 * 60 * 1000) {
      if (signal.aborted) return;
      try {
        const status = await pollPickFolder(id);
        if (signal.aborted) return;
        if (status.status === "done" && status.path) {
          setSelectedPath(status.path);
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

  async function handleSubmit() {
    const path = selectedPath.trim();
    if (!path) {
      setError("请先选择文件夹");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await onSubmit(path);
      onClose();
    } catch (e) {
      setError((e as Error).message ?? "未知错误");
    } finally {
      setSubmitting(false);
    }
  }

  const canSubmit = !!selectedPath && !submitting;

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-foreground/45"
      ref={overlayRef}
      onClick={(e) => {
        if (e.target === overlayRef.current) {
          if (taskId) void cancelPickFolder(taskId);
          onClose();
        }
      }}
      role="dialog"
      aria-modal="true"
      aria-label="选择工作区目录"
    >
      <div className="flex max-w-[calc(100vw-48px)] flex-col overflow-hidden rounded-xl bg-background text-foreground shadow-[0_20px_60px_rgba(0,0,0,0.25)] w-[640px]">
        <header className="flex items-center justify-between border-b border-border px-5 py-4">
          <span className="text-[15px] font-semibold">Select Workspace Directory</span>
          <button
            type="button"
            className="inline-flex h-7 w-7 cursor-pointer items-center justify-center rounded-md border-none bg-transparent text-inherit hover:bg-foreground/[0.05]"
            onClick={() => {
              if (taskId) void cancelPickFolder(taskId);
              onClose();
            }}
            aria-label="关闭"
          >
            <X size={16} />
          </button>
        </header>

        {error && (
          <div className="border-b border-destructive/20 bg-destructive/[0.08] p-2.5 text-[13px] text-destructive">
            {error}
          </div>
        )}

        {pickingHint && (
          <div className="border-b border-primary/20 bg-primary/[0.08] p-2.5 text-[13px] leading-relaxed text-primary" data-testid="wp-pick-hint">
            💡 已发送 OS 文件夹选择请求。如果 5 秒内没有看到弹窗，请检查任务栏 / Alt+Tab；
            或直接点下方"取消"后在路径框里手动输入工作区目录。
          </div>
        )}

        <div className="flex flex-col gap-[18px] px-5 py-6">
          <div className="flex items-center gap-4">
            <label className="w-24 shrink-0 text-sm font-medium">文件夹：</label>
            <div className="flex flex-1 gap-2">
              <input
                className="flex-1 rounded-md border border-border bg-background px-3 py-2 text-[13px] text-foreground"
                value={selectedPath}
                onChange={(e) => setSelectedPath(e.target.value)}
                placeholder="点击右侧按钮调起资源管理器选择文件夹，或直接键入路径..."
                aria-label="文件夹路径"
                data-testid="wp-path-input"
              />
              <button
                type="button"
                className="inline-flex cursor-pointer items-center gap-1.5 whitespace-nowrap rounded-md border border-primary bg-primary px-3.5 py-2 text-[13px] font-medium text-primary-foreground hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60"
                onClick={handlePickFolder}
                disabled={picking}
                data-testid="wp-pick-folder"
              >
                <FolderSearch size={14} />
                {picking ? "选择中..." : "选择文件夹..."}
              </button>
              <button
                type="button"
                className="inline-flex h-9 w-9 cursor-pointer items-center justify-center rounded-md border border-border bg-background text-foreground hover:bg-foreground/[0.05]"
                onClick={handleReveal}
                title="在资源管理器中显示（reveal 父目录后手动定位）"
                data-testid="wp-reveal"
              >
                <ExternalLink size={14} />
              </button>
            </div>
          </div>
        </div>

        <footer className="flex justify-end gap-2 border-t border-border bg-foreground/[0.02] px-5 py-3.5">
          <button
            type="button"
            className="cursor-pointer rounded-md border border-border bg-background px-4 py-2 text-[13px] text-foreground"
            onClick={() => {
              if (taskId) void cancelPickFolder(taskId);
              onClose();
            }}
          >
            取消
          </button>
          <button
            type="button"
            className="cursor-pointer rounded-md border border-primary bg-primary px-4 py-2 text-[13px] font-medium text-primary-foreground disabled:cursor-not-allowed disabled:opacity-50"
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
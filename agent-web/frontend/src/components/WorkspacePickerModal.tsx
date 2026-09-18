/**
 * WorkspacePickerModal（picker-reveal-only）。
 *
 * 路径输入 + reveal 快速备选（无 OS dialog）：
 *  - 路径输入框：直接键入/粘贴
 *  - 「在资源管理器中显示」按钮：调 /api/settings/reveal（explorer /select, ~50ms）
 *  - 路径变化自动填 basename 为 name（用户改过 name 后不再覆盖）
 *  - 提交：调 onSubmit(name, dir)
 *
 * <p>不再调 OS FolderBrowserDialog（PowerShell 3-5s 启动太慢）。
 */

import { ExternalLink, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import styles from "./WorkspacePickerModal.module.css";

const STORAGE_KEY = "agent-demo.workspace-picker.last-path";
const NAME_RE = /^[A-Za-z0-9._-]+$/;

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
  const [nameTouched, setNameTouched] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const overlayRef = useRef<HTMLDivElement | null>(null);

  // Esc 关闭
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  // 打开时恢复上次路径；关闭时重置
  useEffect(() => {
    if (!open) {
      setSelectedPath("");
      setWorkspaceName("");
      setNameTouched(false);
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

  // 路径变化自动填 basename 为 name（仅当 name 还没被用户手动改过）
  useEffect(() => {
    if (nameTouched) return;
    if (!selectedPath) {
      setWorkspaceName("");
      return;
    }
    setWorkspaceName(basenameOf(selectedPath));
  }, [selectedPath, nameTouched]);

  async function handleReveal() {
    setError(null);
    try {
      await callReveal();
    } catch (e) {
      setError("reveal 失败：" + (e as Error).message);
    }
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
      try {
        localStorage.setItem(STORAGE_KEY, selectedPath);
      } catch {
        /* ignore */
      }
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
        if (e.target === overlayRef.current) onClose();
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
            onClick={onClose}
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
                placeholder="输入绝对路径（点击右侧图标调起资源管理器复制）..."
                aria-label="文件夹路径"
                data-testid="wp-path-input"
              />
              <button
                type="button"
                className={styles.wpRevealButton}
                onClick={handleReveal}
                title="在资源管理器中显示（explorer /select 当前文件）"
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
              onChange={(e) => {
                setWorkspaceName(e.target.value);
                setNameTouched(true);
              }}
              placeholder="md-main"
              aria-label="工作区名称"
              data-testid="wp-name-input"
            />
          </div>
        </div>

        <footer className={styles.wpFooter}>
          <button type="button" className={styles.wpCancel} onClick={onClose}>
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

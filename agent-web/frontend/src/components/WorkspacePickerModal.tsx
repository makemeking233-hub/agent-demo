/**
 * WorkspacePickerModal（native-folder-picker）。
 *
 * 简化版：点"选择文件夹..."按钮 → 调后端 /api/workspaces/pick-folder →
 * 弹 OS 原生文件夹选择对话框 → 选完后展示已选路径 + 工作区名称输入 + 确认。
 *
 * <p>不再使用浏览器内嵌目录树（已删除 listDir/mkdir 客户端调用）。
 */

import { FolderSearch, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import styles from "./WorkspacePickerModal.module.css";

const STORAGE_KEY = "agent-demo.workspace-picker.last-path";
const NAME_RE = /^[A-Za-z0-9._-]+$/;

export interface WorkspacePickerModalProps {
  open: boolean;
  onClose: () => void;
  onSubmit: (name: string, dir: string) => Promise<void>;
}

interface PickFolderResponse {
  path: string;
  reason?: string;
}

function basenameOf(p: string): string {
  if (!p) return "";
  const m = p.match(/[^\\/]+$/);
  return m ? m[0] : "";
}

async function callPickFolder(): Promise<PickFolderResponse> {
  const r = await fetch("/api/workspaces/pick-folder", { method: "POST" });
  if (!r.ok) throw new Error(`pick-folder ${r.status}`);
  return (await r.json()) as PickFolderResponse;
}

export function WorkspacePickerModal({ open, onClose, onSubmit }: WorkspacePickerModalProps) {
  const [selectedPath, setSelectedPath] = useState<string>("");
  const [workspaceName, setWorkspaceName] = useState<string>("");
  const [picking, setPicking] = useState(false);
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

  // 打开时恢复上次路径 + 关闭时重置
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
    try {
      const result = await callPickFolder();
      if (result.path) {
        setSelectedPath(result.path);
        setWorkspaceName((cur) => cur || basenameOf(result.path));
        try {
          localStorage.setItem(STORAGE_KEY, result.path);
        } catch {
          /* ignore */
        }
      } else if (result.reason === "timeout") {
        setError("操作超时，请重试");
      }
      // reason === "cancelled" 不提示
    } catch (e) {
      setError("调起资源管理器失败：" + (e as Error).message);
    } finally {
      setPicking(false);
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

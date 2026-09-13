/**
 * PwaUpdatePrompt（add-pwa-support）。
 *
 * 检测到新版本时顶部弹 Snackbar，提示用户"立即刷新"。
 */

import { RefreshCw, X } from "lucide-react";
import { usePwaUpdate } from "../hooks/usePwaUpdate";
import styles from "./PwaUpdatePrompt.module.css";

export function PwaUpdatePrompt(): JSX.Element | null {
  const { needRefresh, update } = usePwaUpdate();

  if (!needRefresh) return null;

  return (
    <div className={styles.prompt} role="alert" aria-live="assertive">
      <RefreshCw size={14} className={styles.icon} />
      <span className={styles.text}>检测到新版本</span>
      <button
        type="button"
        className={styles.updateButton}
        onClick={() => {
          void update();
        }}
        aria-label="立即刷新"
      >
        立即刷新
      </button>
      <button
        type="button"
        className={styles.closeButton}
        onClick={() => {
          // 仅关闭提示（用户可在下次访问时自然更新）
          // 不调 updateServiceWorker → 等下次访问
          const event = new CustomEvent("pwa-prompt-dismiss");
          window.dispatchEvent(event);
        }}
        aria-label="稍后"
      >
        <X size={12} />
      </button>
    </div>
  );
}
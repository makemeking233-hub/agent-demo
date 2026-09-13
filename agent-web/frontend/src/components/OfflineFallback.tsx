/**
 * OfflineFallback（add-pwa-support）。
 *
 * 路由级 fallback 组件。检测到 offline 时替换路由内容显示。
 * 用法：
 *   function ChatPanel() {
 *     const { isOnline, retry } = useOnline();
 *     if (!isOnline) return <OfflineFallback onRetry={retry} />;
 *     ...
 *   }
 */

import { RefreshCw, WifiOff } from "lucide-react";
import styles from "./OfflineFallback.module.css";

export interface OfflineFallbackProps {
  onRetry?: () => void;
  /** 自定义标题（默认"网络已断开"） */
  title?: string;
}

export function OfflineFallback({
  onRetry,
  title = "网络已断开",
}: OfflineFallbackProps): JSX.Element {
  return (
    <div className={styles.fallback} role="status" aria-live="polite">
      <WifiOff size={32} className={styles.icon} />
      <h3 className={styles.title}>{title}</h3>
      <p className={styles.hint}>
        请检查网络连接，或稍后再试。会话数据已保留在本地，重连后可继续。
      </p>
      {onRetry && (
        <button
          type="button"
          className={styles.retryButton}
          onClick={onRetry}
          aria-label="重试连接"
        >
          <RefreshCw size={14} />
          重试
        </button>
      )}
    </div>
  );
}
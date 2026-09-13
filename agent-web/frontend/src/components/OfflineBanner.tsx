/**
 * OfflineBanner（add-pwa-support）。
 *
 * 顶部 6px 高度 Snackbar + "网络已断开" + 重试按钮。订阅 useOnline Context，
 * 离线时显示、在线时隐藏。
 */

import { RefreshCw, WifiOff } from "lucide-react";
import { useOnline } from "../hooks/useOnline";
import styles from "./OfflineBanner.module.css";

export function OfflineBanner(): JSX.Element | null {
  const { isOnline, retry } = useOnline();
  if (isOnline) return null;

  return (
    <div className={styles.banner} role="status" aria-live="polite">
      <WifiOff size={14} className={styles.icon} />
      <span className={styles.text}>网络已断开</span>
      <button
        type="button"
        className={styles.retryButton}
        onClick={retry}
        aria-label="重试连接"
      >
        <RefreshCw size={12} />
        重试
      </button>
    </div>
  );
}
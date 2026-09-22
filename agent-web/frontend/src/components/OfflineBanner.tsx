/**
 * OfflineBanner（add-pwa-support
 * → shadcn-components-p2: 从 CSS Modules 迁到 Tailwind utility）。
 *
 * 顶部 Snackbar + "网络已断开" + 重试按钮。订阅 useOnline Context，
 * 离线时显示、在线时隐藏。
 */

import { RefreshCw, WifiOff } from "lucide-react";
import { useOnline } from "../hooks/useOnline";

export function OfflineBanner(): JSX.Element | null {
  const { isOnline, retry } = useOnline();
  if (isOnline) return null;

  return (
    <div
      className="flex h-8 shrink-0 items-center gap-2 border-b border-destructive/25 bg-destructive/10 px-3 text-xs font-medium text-destructive"
      role="status"
      aria-live="polite"
    >
      <WifiOff size={14} className="shrink-0" />
      <span className="flex-1">网络已断开</span>
      <button
        type="button"
        className="inline-flex shrink-0 cursor-pointer items-center gap-1 rounded-sm border border-current bg-transparent px-2.5 py-[3px] text-[11px] text-inherit hover:bg-destructive/20"
        onClick={retry}
        aria-label="重试连接"
      >
        <RefreshCw size={12} />
        重试
      </button>
    </div>
  );
}
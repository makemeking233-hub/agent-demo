/**
 * OfflineFallback（add-pwa-support
 * → shadcn-components-p2: 从 CSS Modules 迁到 Tailwind utility）。
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
    <div
      className="flex min-h-[240px] flex-col items-center justify-center px-6 py-12 text-center text-muted-foreground"
      role="status"
      aria-live="polite"
    >
      <WifiOff size={32} className="mb-3 text-destructive" />
      <h3 className="mt-0 mb-2 text-base font-semibold text-foreground">{title}</h3>
      <p className="mt-0 mb-5 max-w-[360px] text-[13px] leading-normal">
        请检查网络连接，或稍后再试。会话数据已保留在本地，重连后可继续。
      </p>
      {onRetry && (
        <button
          type="button"
          className="inline-flex cursor-pointer items-center gap-1.5 rounded-sm border border-primary bg-primary px-4 py-1.5 text-[13px] text-primary-foreground hover:brightness-110"
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
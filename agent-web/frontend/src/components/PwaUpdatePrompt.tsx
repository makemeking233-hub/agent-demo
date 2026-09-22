/**
 * PwaUpdatePrompt（add-pwa-support
 * → shadcn-components-p2: 从 CSS Modules 迁到 Tailwind utility）。
 *
 * 检测到新版本时顶部弹 Snackbar，提示用户"立即刷新"。
 */

import { RefreshCw, X } from "lucide-react";
import { usePwaUpdate } from "../hooks/usePwaUpdate";

export function PwaUpdatePrompt(): JSX.Element | null {
  const { needRefresh, update } = usePwaUpdate();

  if (!needRefresh) return null;

  return (
    <div
      className="flex h-8 shrink-0 items-center gap-2 border-b border-primary/30 bg-primary/[0.14] px-3 text-xs font-medium text-primary"
      role="alert"
      aria-live="assertive"
    >
      <RefreshCw size={14} className="shrink-0" />
      <span className="flex-1">检测到新版本</span>
      <button
        type="button"
        className="shrink-0 cursor-pointer rounded-sm border border-current bg-transparent px-3 py-[3px] text-[11px] font-medium text-inherit hover:bg-primary/[0.18]"
        onClick={() => {
          void update();
        }}
        aria-label="立即刷新"
      >
        立即刷新
      </button>
      <button
        type="button"
        className="inline-flex h-[22px] w-[22px] shrink-0 cursor-pointer items-center justify-center rounded-sm border-none bg-transparent text-inherit hover:bg-primary/[0.18]"
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
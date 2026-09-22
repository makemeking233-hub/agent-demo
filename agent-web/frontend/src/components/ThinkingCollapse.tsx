/**
 * ThinkingCollapse（add-reasoning-thinking-streaming
 * → shadcn-components-p2: 从 CSS Modules 迁到 Tailwind utility）。
 *
 * 可折叠组件：默认折叠，标题"思考过程 (N token)"，超 2000 token 显示"查看更多"。
 */

import { ChevronDown, ChevronRight, Brain } from "lucide-react";
import { useState } from "react";

export interface ThinkingCollapseProps {
  /** 完整 thinking 文本（已累积） */
  text: string;
  /** thinking 估算 token 数（默认按 text.length / 4 估算） */
  tokens?: number;
  /** 长文本阈值；超过则显示"查看更多"按钮 */
  longThreshold?: number;
}

export function ThinkingCollapse({
  text,
  tokens,
  longThreshold = 2000,
}: ThinkingCollapseProps) {
  const [open, setOpen] = useState(false);
  if (!text) return null;
  const estimatedTokens = tokens ?? Math.ceil(text.length / 4);
  const isLong = text.length > longThreshold;
  const preview = isLong ? text.slice(0, longThreshold) + "…" : text;
  const displayText = open || !isLong ? text : preview;

  return (
    <details
      className="mb-2 rounded-sm border border-border bg-secondary text-xs [&::-webkit-details-marker]:hidden"
      open={open}
      onToggle={(e) => setOpen((e.target as HTMLDetailsElement).open)}
    >
      <summary className="flex cursor-pointer list-none items-center gap-1.5 px-2.5 py-1.5 text-muted-foreground select-none">
        <span className="inline-flex shrink-0">
          {open ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
        </span>
        <Brain size={12} className="shrink-0 text-primary" />
        <span className="flex-1 font-medium text-foreground">
          思考过程 ({estimatedTokens} token)
        </span>
        {isLong && !open && (
          <button
            type="button"
            className="shrink-0 cursor-pointer rounded-sm border border-primary bg-transparent px-2.5 py-0.5 text-[11px] text-primary hover:bg-accent-subtle"
            onClick={(e) => {
              e.preventDefault();
              setOpen(true);
            }}
          >
            查看更多
          </button>
        )}
      </summary>
      <pre className="m-0 max-h-[400px] overflow-y-auto border-t border-border px-3 pt-2 pb-3 font-mono text-[11px] leading-relaxed break-words whitespace-pre-wrap text-foreground">
        {displayText}
      </pre>
    </details>
  );
}
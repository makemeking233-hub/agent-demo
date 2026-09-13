/**
 * ThinkingCollapse（add-reasoning-thinking-streaming）。
 *
 * 可折叠组件：默认折叠，标题"思考过程 (N token)"，超 2000 token 显示"查看更多"。
 */

import { ChevronDown, ChevronRight, Brain } from "lucide-react";
import { useState } from "react";
import styles from "./ThinkingCollapse.module.css";

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
      className={styles.collapse}
      open={open}
      onToggle={(e) => setOpen((e.target as HTMLDetailsElement).open)}
    >
      <summary className={styles.summary}>
        <span className={styles.icon}>
          {open ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
        </span>
        <Brain size={12} className={styles.brain} />
        <span className={styles.title}>思考过程 ({estimatedTokens} token)</span>
        {isLong && !open && (
          <button
            type="button"
            className={styles.expandBtn}
            onClick={(e) => {
              e.preventDefault();
              setOpen(true);
            }}
          >
            查看更多
          </button>
        )}
      </summary>
      <pre className={styles.body}>{displayText}</pre>
    </details>
  );
}
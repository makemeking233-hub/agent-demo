/**
 * MessageActionRow（add-message-actions P1 + P2）。
 *
 * <p>assistant / user 消息底部的操作栏：copy + 可选 per-message clock + 可选 extraActions（赞踩）。
 *
 * <p>copy 语义对齐 DSH `MessageIconActions`：
 * - 写剪贴板成功后 1s 内显示 ✓
 * - `copyPending` ref 防重入（✓ 窗口内重复点击不重复写）
 * - `copyEpoch` ref 防 unmount 后 setState
 * - `navigator.clipboard` 不可用 → 隐藏 textarea + `document.execCommand('copy')` 降级
 */

import { Check, Copy, ThumbsDown, ThumbsUp } from "lucide-react";
import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import type { Rating } from "../api/feedback";
import { formatClock, type MessageClock } from "../lib/message-clock";
import styles from "./MessageActionRow.module.css";

export interface MessageActionRowProps {
  /** 复制到剪贴板的纯文本 */
  text: string;
  /** 可选：per-message 读数（P2：时间 + Ran for + TTFT + tok/s）；null/缺失则不渲染 clock */
  meta?: MessageClock | null;
  /**
   * 可选：当前反馈（add-message-feedback）。`undefined` = 不渲染赞踩按钮（无 uuid / 无会话）；
   * `null` = 渲染按钮但未选中。
   */
  rating?: Rating | null;
  /** 可选：点击赞踩回调；与 `rating` 同时提供才渲染按钮 */
  onRate?: (rating: Rating) => void;
  /** 可选：额外按钮（插在赞踩之后） */
  children?: ReactNode;
  className?: string;
}

export function MessageActionRow({ text, meta, rating, onRate, children, className }: MessageActionRowProps) {
  const [copied, setCopied] = useState(false);
  const copyPending = useRef(false);
  const copyTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const copyEpoch = useRef(0);
  const clockText = formatClock(meta);

  // unmount 清理：递增 epoch 让 pending 的 promise 回调失效；清 timer
  useEffect(
    () => () => {
      copyEpoch.current += 1;
      copyPending.current = false;
      if (copyTimer.current !== null) clearTimeout(copyTimer.current);
    },
    [],
  );

  const onCopy = useCallback(() => {
    if (copied || copyPending.current) return;
    const epoch = copyEpoch.current;
    copyPending.current = true;
    void writeClipboard(text).then((ok) => {
      if (epoch !== copyEpoch.current) return; // 组件已卸载
      copyPending.current = false;
      if (!ok) return;
      setCopied(true);
      copyTimer.current = window.setTimeout(() => {
        copyTimer.current = null;
        setCopied(false);
      }, 1000);
    });
  }, [copied, text]);

  return (
    <div
      className={className ? `${styles.row} ${className}` : styles.row}
      data-testid="message-action-row"
    >
      <button
        type="button"
        className={styles.action}
        aria-label={copied ? "已复制" : "复制"}
        title={copied ? "已复制" : "复制"}
        onClick={onCopy}
        data-testid="msg-copy"
      >
        {copied ? <Check size={13} /> : <Copy size={13} />}
      </button>
      {/* add-message-feedback：👍/👎 两态（rating === undefined 时整组不渲染） */}
      {rating !== undefined && onRate && (
        <>
          <button
            type="button"
            className={styles.action}
            aria-label={rating === "up" ? "取消点赞" : "点赞"}
            aria-pressed={rating === "up"}
            title={rating === "up" ? "取消点赞" : "点赞"}
            onClick={() => onRate("up")}
            data-testid="msg-up"
          >
            <ThumbsUp size={13} />
          </button>
          <button
            type="button"
            className={styles.action}
            aria-label={rating === "down" ? "取消点踩" : "点踩"}
            aria-pressed={rating === "down"}
            title={rating === "down" ? "取消点踩" : "点踩"}
            onClick={() => onRate("down")}
            data-testid="msg-down"
          >
            <ThumbsDown size={13} />
          </button>
        </>
      )}
      {children}
      {clockText && (
        <span className={styles.clock} data-testid="msg-clock" title={clockText}>
          {clockText}
        </span>
      )}
    </div>
  );
}

/**
 * 写剪贴板：优先 `navigator.clipboard.writeText`，失败降级 `document.execCommand('copy')`。
 *
 * @param text 待写入文本
 * @returns 是否成功
 */
export async function writeClipboard(text: string): Promise<boolean> {
  try {
    if (typeof navigator !== "undefined" && navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {
    // 降级到 legacy 路径（非 HTTPS / 权限拒绝）
  }
  try {
    const ta = document.createElement("textarea");
    ta.value = text;
    ta.setAttribute("readonly", "");
    ta.style.position = "fixed";
    ta.style.top = "-9999px";
    ta.style.opacity = "0";
    document.body.appendChild(ta);
    ta.select();
    const ok = document.execCommand("copy");
    document.body.removeChild(ta);
    return ok;
  } catch {
    return false;
  }
}
/**
 * MessageActionRow（add-message-actions P1 + P2
 * → shadcn-components-p2: 从 CSS Modules 迁到 Tailwind utility）。
 *
 * <p>assistant / user 消息底部的操作栏：copy + 可选 per-message clock + 可选 extraActions（赞踩）。
 *
 * <p>copy 语义对齐 DSH `MessageIconActions`：
 * - 写剪贴板成功后 1s 内显示 ✓
 * - `copyPending` ref 防重入（✓ 窗口内重复点击不重复写）
 * - `copyEpoch` ref 防 unmount 后 setState
 * - `navigator.clipboard` 不可用 → 隐藏 textarea + `document.execCommand('copy')` 降级
 */

import { Check, Copy } from "lucide-react";
import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";

export interface MessageActionRowProps {
  /** 复制到剪贴板的纯文本 */
  text: string;
  /** 可选：per-message clock 读数（P2：时间 + Ran for + TTFT + tok/s） */
  clock?: ReactNode;
  /** 可选：额外按钮（P3：赞踩） */
  children?: ReactNode;
  className?: string;
}

export function MessageActionRow({ text, clock, children, className }: MessageActionRowProps) {
  const [copied, setCopied] = useState(false);
  const copyPending = useRef(false);
  // 用 number 而非 ReturnType<typeof setTimeout>：装了 @types/node 后后者会解析成
  // NodeJS.Timeout，与 window.setTimeout 返回的 number 不兼容（tsc TS2322）。
  const copyTimer = useRef<number | null>(null);
  const copyEpoch = useRef(0);

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
      className={`mt-1 flex items-center gap-1 opacity-55 transition-opacity hover:opacity-100 ${
        className ?? ""
      }`}
      data-testid="message-action-row"
    >
      <button
        type="button"
        className="inline-flex h-[22px] w-[22px] cursor-pointer items-center justify-center rounded border-none bg-transparent p-0 text-muted-foreground hover:bg-secondary hover:text-foreground aria-pressed:text-primary"
        aria-label={copied ? "已复制" : "复制"}
        title={copied ? "已复制" : "复制"}
        onClick={onCopy}
        data-testid="msg-copy"
      >
        {copied ? <Check size={13} /> : <Copy size={13} />}
      </button>
      {children}
      {clock}
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
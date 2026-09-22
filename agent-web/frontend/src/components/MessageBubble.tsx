import { User } from "lucide-react";
import { MarkdownContent } from "./MarkdownContent";
import { MessageActionRow } from "./MessageActionRow";
import { ThinkingCollapse } from "./ThinkingCollapse";
import { ToolCallCard } from "./ToolCallCard";
import type { MessageClock } from "../lib/message-clock";
import type { Rating } from "../api/feedback";

/**
 * MessageBubble（→ shadcn-components-p2: 从 CSS Modules 迁到 Tailwind utility）。
 *
 * 用户消息靠右（row-reverse + 浅强调底色），助手消息靠左（卡片底色 + 边框）。
 */
export type InlineTool = {
  id: string;
  name: string;
  status: "running" | "ok" | "fail";
  text?: string;
  durationMs?: number;
};

export function MessageBubble(props: {
  role: "user" | "assistant";
  text: string;
  tools?: InlineTool[];
  // add-reasoning-thinking-streaming: 推理过程（assistant 消息可携带）
  thinking?: string;
  reasoningTokens?: number;
  /** add-message-actions P2: per-message 读数（assistant finalize 后才有） */
  meta?: MessageClock | null;
  /**
   * add-message-feedback: 当前反馈；`undefined` = 不渲染赞踩按钮（无 uuid / 无 session），
   * `null` = 渲染但未选中。
   */
  rating?: Rating | null;
  /** add-message-feedback: 点击赞踩回调 */
  onRate?: (rating: Rating) => void;
  /** add-message-actions P3: 额外按钮（extraActions slot） */
  actions?: React.ReactNode;
}) {
  const isUser = props.role === "user";
  const tools = props.tools ?? [];
  // add-message-actions: 流式中（无 text 且无 tool）不显示 action row
  const showActions = !!props.text;
  return (
    <div
      className={`mx-auto flex max-w-[800px] items-start gap-2 px-4 py-2 ${
        isUser ? "flex-row-reverse" : ""
      }`}
    >
      <div className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-neutral-200 text-[11px] font-semibold text-muted-foreground">
        {isUser ? <User size={16} /> : <span className="text-[10px] tracking-[0.05em]">AI</span>}
      </div>
      <div
        className={
          isUser
            ? "max-w-[70%] rounded-xl bg-accent-subtle px-3 py-2"
            : "max-w-[85%] rounded-xl border border-border bg-card px-3 py-2"
        }
      >
        {/* add-reasoning-thinking-streaming: 思考过程在文本之前展示（默认折叠） */}
        {!isUser && props.thinking && (
          <ThinkingCollapse text={props.thinking} tokens={props.reasoningTokens} />
        )}
        {props.text ? (
          <MarkdownContent text={props.text} />
        ) : (
          !isUser && tools.length === 0 && !props.thinking && (
            <span className="animate-pulse italic text-muted-foreground">…</span>
          )
        )}
        {tools.map((t) => (
          <ToolCallCard key={t.id} name={t.name} status={t.status} text={t.text} durationMs={t.durationMs} />
        ))}
        {/* add-message-actions P1: action row（copy + 可选 clock/赞踩）；流式中不显示 */}
        {showActions && (
          <MessageActionRow
            text={props.text}
            meta={isUser ? undefined : props.meta}
            rating={isUser ? undefined : props.rating}
            onRate={isUser ? undefined : props.onRate}
          >
            {isUser ? undefined : props.actions}
          </MessageActionRow>
        )}
      </div>
    </div>
  );
}
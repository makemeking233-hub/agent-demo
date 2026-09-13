import { User } from "lucide-react";
import styles from "./MessageBubble.module.css";
import { MarkdownContent } from "./MarkdownContent";
import { ThinkingCollapse } from "./ThinkingCollapse";
import { ToolCallCard } from "./ToolCallCard";

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
}) {
  const isUser = props.role === "user";
  const tools = props.tools ?? [];
  return (
    <div className={`${styles.row} ${isUser ? styles.rowUser : styles.rowAssistant}`}>
      <div className={styles.avatar}>
        {isUser ? <User size={16} /> : <span className={styles.botAvatar}>AI</span>}
      </div>
      <div className={isUser ? styles.bubbleUser : styles.bubbleAssistant}>
        {/* add-reasoning-thinking-streaming: 思考过程在文本之前展示（默认折叠） */}
        {!isUser && props.thinking && (
          <ThinkingCollapse text={props.thinking} tokens={props.reasoningTokens} />
        )}
        {props.text ? (
          <MarkdownContent text={props.text} />
        ) : (
          !isUser && tools.length === 0 && !props.thinking && (
            <span className={styles.cursor}>…</span>
          )
        )}
        {tools.map((t) => (
          <ToolCallCard key={t.id} name={t.name} status={t.status} text={t.text} durationMs={t.durationMs} />
        ))}
      </div>
    </div>
  );
}
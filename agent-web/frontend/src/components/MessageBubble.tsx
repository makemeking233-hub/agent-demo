import { User } from "lucide-react";
import ReactMarkdown from "react-markdown";
import styles from "./MessageBubble.module.css";
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
}) {
  const isUser = props.role === "user";
  const tools = props.tools ?? [];
  return (
    <div className={`${styles.row} ${isUser ? styles.rowUser : styles.rowAssistant}`}>
      <div className={styles.avatar}>
        {isUser ? <User size={16} /> : <span className={styles.botAvatar}>AI</span>}
      </div>
      <div className={isUser ? styles.bubbleUser : styles.bubbleAssistant}>
        {props.text ? (
          <div className={styles.markdown}>
            <ReactMarkdown>{props.text}</ReactMarkdown>
          </div>
        ) : (
          !isUser && tools.length === 0 && <span className={styles.cursor}>…</span>
        )}
        {tools.map((t) => (
          <ToolCallCard key={t.id} name={t.name} status={t.status} text={t.text} durationMs={t.durationMs} />
        ))}
      </div>
    </div>
  );
}

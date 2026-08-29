import { User } from "lucide-react";
import ReactMarkdown from "react-markdown";
import styles from "./MessageBubble.module.css";

export function MessageBubble(props: { role: "user" | "assistant"; text: string }) {
  const isUser = props.role === "user";
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
          <span className={styles.cursor}>…</span>
        )}
      </div>
    </div>
  );
}

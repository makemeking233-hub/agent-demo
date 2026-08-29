import { ShieldAlert, ShieldCheck } from "lucide-react";
import styles from "./PermissionCard.module.css";

export function PermissionCard(props: {
  toolName: string;
  reason: string;
  choices: ("yes" | "no" | "always")[];
  onChoose: (decision: "yes" | "no" | "always") => void;
}) {
  if (props.choices.length === 0) {
    return (
      <div className={styles.resolved}>
        <ShieldCheck size={14} />
        <span>权限已处理: {props.toolName}</span>
      </div>
    );
  }
  return (
    <div className={styles.card}>
      <div className={styles.title}>
        <ShieldAlert size={14} />
        <span>权限请求: {props.toolName}</span>
      </div>
      <div className={styles.reason}>{props.reason}</div>
      <div className={styles.actions}>
        {props.choices.map((c) => (
          <button key={c} type="button" className={styles.button} onClick={() => props.onChoose(c)}>
            {c}
          </button>
        ))}
      </div>
    </div>
  );
}

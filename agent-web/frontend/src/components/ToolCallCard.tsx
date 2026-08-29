import { AlertCircle, CheckCircle2, Loader2, Terminal } from "lucide-react";
import styles from "./ToolCallCard.module.css";

export function ToolCallCard(props: {
  name: string;
  status: "running" | "ok" | "fail";
  text?: string;
  durationMs?: number;
}) {
  const config = STATUS_CONFIG[props.status];
  const Icon = config.icon;
  return (
    <div className={`${styles.card} ${config.cardClass}`}>
      <div className={styles.header}>
        <span className={config.titleClass}>
          <Icon size={14} className={props.status === "running" ? styles.spin : ""} />
          <Terminal size={12} />
          <span>{props.name}</span>
        </span>
        <span className={config.metaClass}>
          {`${config.label}${props.durationMs != null ? ` · ${props.durationMs}ms` : ""}`}
        </span>
      </div>
      {props.text && <pre className={styles.output}>{props.text}</pre>}
    </div>
  );
}

const STATUS_CONFIG = {
  running: {
    label: "执行中",
    icon: Loader2,
    cardClass: "cardRunning",
    titleClass: "titleRunning",
    metaClass: "metaRunning",
  },
  ok: {
    label: "完成",
    icon: CheckCircle2,
    cardClass: "cardOk",
    titleClass: "titleOk",
    metaClass: "metaOk",
  },
  fail: {
    label: "失败",
    icon: AlertCircle,
    cardClass: "cardFail",
    titleClass: "titleFail",
    metaClass: "metaFail",
  },
} as const;

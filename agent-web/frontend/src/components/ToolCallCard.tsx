import { AlertCircle, CheckCircle2, ChevronDown, ChevronRight, Loader2, Terminal } from "lucide-react";
import { useState } from "react";
import styles from "./ToolCallCard.module.css";

export function ToolCallCard(props: {
  name: string;
  status: "running" | "ok" | "fail";
  text?: string;
  durationMs?: number;
}) {
  const config = STATUS_CONFIG[props.status];
  const Icon = config.icon;
  // 默认折叠：工具调用明细收起，点击 header 展开/收起
  const [collapsed, setCollapsed] = useState(true);
  const hasOutput = props.text != null && props.text !== "";
  const Chevron = collapsed ? ChevronRight : ChevronDown;

  return (
    <div className={`${styles.card} ${config.cardClass}`}>
      <button
        type="button"
        className={styles.header}
        onClick={() => setCollapsed((c) => !c)}
        aria-expanded={!collapsed}
        aria-label={`${props.name} 工具调用${collapsed ? "（已折叠）" : "（已展开）"}`}
      >
        <span className={config.titleClass}>
          <Icon size={14} className={props.status === "running" ? styles.spin : ""} />
          <Terminal size={12} />
          <span>{props.name}</span>
        </span>
        <span className={config.metaClass}>
          {hasOutput && <Chevron size={12} className={styles.chevron} />}
          {`${config.label}${props.durationMs != null ? ` · ${props.durationMs}ms` : ""}`}
        </span>
      </button>
      {!collapsed && hasOutput && <pre className={styles.output}>{props.text}</pre>}
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

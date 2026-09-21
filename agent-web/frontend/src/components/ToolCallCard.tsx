import { AlertCircle, ArrowUpCircle, CheckCircle2, ChevronDown, ChevronRight, Loader2, ShieldAlert, Terminal } from "lucide-react";
import { useState } from "react";
import styles from "./ToolCallCard.module.css";

/** sandbox denial 结构化信息（rewrite-permission-mode-dsh T10.3；对齐后端 PermissionDenialResponse） */
export interface SandboxDenial {
  kind: string;
  currentMode: string;
  suggestedMode: string | null;
  marker: string;
}

export function ToolCallCard(props: {
  name: string;
  status: "running" | "ok" | "fail";
  text?: string;
  durationMs?: number;
  /** sandbox policy 拒绝信息（存在时渲染 marker + 升级按钮） */
  denial?: SandboxDenial;
  /** 点击"升级到 X"时回调（由 ChatPanel 调 POST /permission with escalate=true） */
  onEscalate?: (targetMode: string) => void;
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
      {props.denial && <DenialBanner denial={props.denial} onEscalate={props.onEscalate} />}
    </div>
  );
}

/**
 * sandbox 拒绝横幅（rewrite-permission-mode-dsh T10.3；spec §"模型侧 marker 渲染"）。
 *
 * <p>渲染 {@code [sandbox: ... under <mode> mode]} marker；{@code suggestedMode} 非 null 时
 * 显示"升级到 <mode>"按钮（点击调 POST /permission with escalate=true）。
 */
function DenialBanner(props: { denial: SandboxDenial; onEscalate?: (mode: string) => void }) {
  const { denial, onEscalate } = props;
  return (
    <div className={styles.denial} data-testid="sandbox-denial">
      <div className={styles.denialHead}>
        <ShieldAlert size={13} />
        <span data-testid="sandbox-denial-marker">{denial.marker}</span>
      </div>
      <div className={styles.denialMeta}>
        <span>当前模式：{denial.currentMode}</span>
        <span>拒绝原因：{denial.kind}</span>
      </div>
      {denial.suggestedMode && onEscalate && (
        <button
          type="button"
          className={styles.escalateBtn}
          data-testid="sandbox-escalate-button"
          onClick={() => onEscalate(denial.suggestedMode as string)}
        >
          <ArrowUpCircle size={13} />
          {`升级到 ${denial.suggestedMode}`}
        </button>
      )}
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

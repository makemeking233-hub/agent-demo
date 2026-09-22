import { AlertCircle, ArrowUpCircle, CheckCircle2, ChevronDown, ChevronRight, Loader2, ShieldAlert, Terminal } from "lucide-react";
import { useState } from "react";

/**
 * ToolCallCard（→ shadcn-components-p2: 从 CSS Modules 迁到 Tailwind utility）。
 *
 * 三种状态（running / ok / fail）各自决定边框、底色、标题与元信息颜色；
 * 默认折叠，点击 header 展开输出。
 *
 * rewrite-permission-mode-dsh T10.3：新增 sandbox denial 横幅（marker + 升级按钮），
 * 样式沿用 Tailwind utility（与 shadcn-components-p2 迁移一致，不再用 CSS Module）。
 */

/** 各状态的 Tailwind 类组合（原为 CSS Module 的三个 class）。 */
const STATUS_CONFIG = {
  running: {
    label: "执行中",
    icon: Loader2,
    cardClass: "border-primary bg-accent-subtle",
    titleClass: "text-primary",
    metaClass: "text-primary",
  },
  ok: {
    label: "完成",
    icon: CheckCircle2,
    cardClass: "border-success bg-success/10",
    titleClass: "text-success",
    metaClass: "text-success",
  },
  fail: {
    label: "失败",
    icon: AlertCircle,
    cardClass: "border-destructive bg-destructive/10",
    titleClass: "text-destructive",
    metaClass: "text-destructive",
  },
} as const;

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
    <div className={`my-2 rounded-md border px-3 py-2 text-[13px] ${config.cardClass}`}>
      <button
        type="button"
        className="flex w-full cursor-pointer items-center justify-between gap-2 border-none bg-transparent p-0 text-left font-[inherit] text-inherit"
        onClick={() => setCollapsed((c) => !c)}
        aria-expanded={!collapsed}
        aria-label={`${props.name} 工具调用${collapsed ? "（已折叠）" : "（已展开）"}`}
      >
        <span
          className={`inline-flex items-center gap-1 font-mono text-xs font-semibold ${config.titleClass}`}
        >
          <Icon size={14} className={props.status === "running" ? "animate-spin" : ""} />
          <Terminal size={12} />
          <span>{props.name}</span>
        </span>
        <span className={`font-mono text-[11px] ${config.metaClass}`}>
          {hasOutput && <Chevron size={12} className="shrink-0 align-middle" />}
          {`${config.label}${props.durationMs != null ? ` · ${props.durationMs}ms` : ""}`}
        </span>
      </button>
      {!collapsed && hasOutput && (
        <pre className="mt-2 max-h-[240px] overflow-auto rounded-sm bg-foreground/[0.05] px-2.5 py-2 font-mono text-xs break-words whitespace-pre-wrap text-foreground">
          {props.text}
        </pre>
      )}
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
    <div
      className="mt-2 rounded-sm border border-destructive bg-destructive/10 px-2.5 py-2 text-xs"
      data-testid="sandbox-denial"
    >
      <div className="flex items-center gap-1.5 font-mono font-semibold break-words text-destructive">
        <ShieldAlert size={13} />
        <span data-testid="sandbox-denial-marker">{denial.marker}</span>
      </div>
      <div className="mt-1 flex gap-3 text-[11px] text-muted-foreground">
        <span>当前模式：{denial.currentMode}</span>
        <span>拒绝原因：{denial.kind}</span>
      </div>
      {denial.suggestedMode && onEscalate && (
        <button
          type="button"
          className="mt-1.5 inline-flex cursor-pointer items-center gap-1 rounded-sm border border-destructive bg-transparent px-2.5 py-1 text-xs text-destructive hover:bg-destructive hover:text-white"
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

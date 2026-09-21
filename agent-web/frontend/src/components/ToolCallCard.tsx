import { AlertCircle, CheckCircle2, ChevronDown, ChevronRight, Loader2, Terminal } from "lucide-react";
import { useState } from "react";

/**
 * ToolCallCard（→ shadcn-components-p2: 从 CSS Modules 迁到 Tailwind utility）。
 *
 * 三种状态（running / ok / fail）各自决定边框、底色、标题与元信息颜色；
 * 默认折叠，点击 header 展开输出。
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
    </div>
  );
}
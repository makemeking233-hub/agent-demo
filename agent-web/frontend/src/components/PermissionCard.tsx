import { ShieldAlert, ShieldCheck } from "lucide-react";

/**
 * PermissionCard（→ shadcn-components-p2: 从 CSS Modules 迁到 Tailwind utility）。
 *
 * 权限请求卡片：标题 + 原因 + 选项按钮；choices 为空时显示"已处理"。
 */
export function PermissionCard(props: {
  toolName: string;
  reason: string;
  choices: ("yes" | "no" | "always")[];
  onChoose: (decision: "yes" | "no" | "always") => void;
}) {
  if (props.choices.length === 0) {
    return (
      <div className="my-2 inline-flex items-center gap-1.5 rounded-sm bg-success/10 px-2.5 py-1 text-xs text-success">
        <ShieldCheck size={14} />
        <span>权限已处理: {props.toolName}</span>
      </div>
    );
  }
  return (
    <div className="my-2 rounded-md border border-warning bg-warning/10 p-3 text-[13px]">
      <div className="flex items-center gap-1.5 font-semibold text-warning">
        <ShieldAlert size={14} />
        <span>权限请求: {props.toolName}</span>
      </div>
      <div className="mt-1.5 mb-2 text-[13px] leading-normal text-foreground">
        {props.reason}
      </div>
      <div className="flex gap-2">
        {props.choices.map((c) => (
          <button
            key={c}
            type="button"
            className="cursor-pointer rounded-sm border border-border bg-background px-3 py-1 font-mono text-xs text-foreground hover:bg-secondary"
            onClick={() => props.onChoose(c)}
          >
            {c}
          </button>
        ))}
      </div>
    </div>
  );
}
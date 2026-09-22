/**
 * EnterBehaviorSelect (add-settings-general-items M2).
 *
 * 3 选项下拉：send / queue / newSession。
 */

import { useSettingsStore } from "../hooks/useSettingsStore";

export type EnterBehavior = "send" | "queue" | "newSession";

const OPTIONS: { value: EnterBehavior; label: string }[] = [
  { value: "send", label: "发送（繁忙时丢弃）" },
  { value: "queue", label: "排队发送" },
  { value: "newSession", label: "新建会话" },
];

export function EnterBehaviorSelect() {
  const value =
    (useSettingsStore(
      (s) => (s.snapshot?.general?.enterBehavior as { mode?: EnterBehavior } | undefined)?.mode,
    ) ?? "send") as EnterBehavior;
  const patch = useSettingsStore((s) => s.patch);

  return (
    <div className="mb-6 flex flex-col gap-2">
      <div className="text-sm font-medium text-foreground">繁忙时 Enter 键行为</div>
      <div className="flex items-center gap-4 py-2">
        <div className="flex-1">
          <select
            className="min-w-[200px] rounded-md border border-border bg-background px-3 py-1.5 text-sm text-foreground"
            value={value}
            onChange={(e) => void patch("general.enterBehavior.mode", e.target.value)}
            data-testid="enter-behavior-select"
          >
            {OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
          <div className="mt-1 text-xs text-muted-foreground">
            仅在智能体运行时生效；Cmd/Ctrl+Enter 使用另一行为
          </div>
        </div>
      </div>
    </div>
  );
}

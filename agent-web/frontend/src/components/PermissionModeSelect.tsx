/**
 * PermissionModeSelect (add-settings-general-items M2).
 *
 * 4 选项下拉：plan / ask / danger-full / dontAsk。
 */

import { useSettingsStore } from "../hooks/useSettingsStore";

export type PermissionMode = "plan" | "ask" | "danger-full" | "dontAsk";

const OPTIONS: { value: PermissionMode; label: string }[] = [
  { value: "plan", label: "Plan（只读计划）" },
  { value: "ask", label: "Ask（每次询问）" },
  { value: "danger-full", label: "Danger Full（危险权限）" },
  { value: "dontAsk", label: "Don't Ask（不询问）" },
];

export function PermissionModeSelect() {
  const value =
    (useSettingsStore(
      (s) => (s.snapshot?.general?.permission as { mode?: PermissionMode } | undefined)?.mode,
    ) ?? "ask") as PermissionMode;
  const patch = useSettingsStore((s) => s.patch);

  return (
    <div className="mb-6 flex flex-col gap-2">
      <div className="text-sm font-medium text-foreground">权限模式</div>
      <div className="flex items-center gap-4 py-2">
        <div className="flex-1">
          <select
            className="min-w-[200px] rounded-md border border-border bg-background px-3 py-1.5 text-sm text-foreground"
            value={value}
            onChange={(e) => void patch("general.permission.mode", e.target.value)}
            data-testid="permission-mode-select"
          >
            {OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
          <div className="mt-1 text-xs text-muted-foreground">
            下次新会话生效（session 创建时由后端读取此值）
          </div>
        </div>
      </div>
    </div>
  );
}

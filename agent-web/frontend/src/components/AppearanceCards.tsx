/**
 * AppearanceCards (add-settings-general-items M2 + polish-theme-toggle
 * + shadcn-components-p1: 加 high-contrast 第四项).
 *
 * 四卡片单选：浅色 / 深色 / 跟随系统 / 高对比度。
 * - compact: 用于 TopBar（旧 API 兼容，新 ThemeToggle 不再用）
 * - onAfterChange: 选中后回调（用于 ThemePopover 自动关闭）
 */

import { Contrast, Monitor, Moon, Sun } from "lucide-react";
import { useSettingsStore } from "../hooks/useSettingsStore";

export type AppearancePreference = "light" | "dark" | "system" | "hc";

interface AppearanceCardsProps {
  /** compact: 旧 API 兼容（不再使用，但保留类型） */
  compact?: boolean;
  value?: AppearancePreference;
  onChange?: (value: AppearancePreference) => void;
  /** 选中后回调（不修改 value） */
  onAfterChange?: () => void;
}

const CUBES: { id: AppearancePreference; label: string; Icon: typeof Sun }[] = [
  { id: "light", label: "浅色", Icon: Sun },
  { id: "dark", label: "深色", Icon: Moon },
  { id: "system", label: "跟随系统", Icon: Monitor },
  { id: "hc", label: "高对比度", Icon: Contrast },
];

export function AppearanceCards({
  compact: _compact = false,
  value: propValue,
  onChange: propOnChange,
  onAfterChange,
}: AppearanceCardsProps) {
  const storeValue = useSettingsStore(
    (s) => (s.snapshot?.general?.appearance as { preference?: AppearancePreference } | undefined)?.preference ?? "system",
  );
  const patch = useSettingsStore((s) => s.patch);
  const value = propValue ?? storeValue;
  const onChange = propOnChange ?? ((v: AppearancePreference) => {
    void patch("general.appearance.preference", v);
  });

  function handleClick(id: AppearancePreference) {
    onChange(id);
    onAfterChange?.();
  }

  return (
    <div data-testid="appearance-cards-wrapper">
      <div className="text-sm font-medium text-foreground">外观</div>
      <div className="flex gap-2.5" data-testid="appearance-cards">
        {CUBES.map(({ id, label, Icon }) => (
          <button
            key={id}
            type="button"
            className={
              value === id
                ? "flex min-w-0 flex-1 cursor-pointer flex-col items-center gap-1.5 rounded-xl border border-primary bg-primary/[0.10] px-4 py-3.5 text-[13px] font-medium text-primary shadow-[0_0_0_3px_rgba(59,130,246,0.15),0_2px_6px_rgba(59,130,246,0.10)] transition-all hover:bg-primary/[0.14]"
                : "flex min-w-0 flex-1 cursor-pointer flex-col items-center gap-1.5 rounded-xl border border-border bg-background px-4 py-3.5 text-[13px] font-medium text-foreground shadow-[0_1px_2px_rgba(0,0,0,0.04)] transition-all hover:-translate-y-px hover:border-neutral-400 hover:bg-foreground/[0.04] hover:shadow-[0_2px_6px_rgba(0,0,0,0.06)] active:translate-y-0"
            }
            aria-pressed={value === id}
            onClick={() => handleClick(id)}
            data-testid={`appearance-card-${id}`}
          >
            <Icon size={18} />
            <span>{label}</span>
          </button>
        ))}
      </div>
    </div>
  );
}

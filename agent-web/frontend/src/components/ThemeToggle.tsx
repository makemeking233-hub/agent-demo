/**
 * ThemeToggle (polish-theme-toggle + shadcn-components-p1: 加 high-contrast).
 *
 * 单图标按钮（Sun/Moon/Monitor/Contrast 动态）+ Popover。
 * TopBar 上只占 32px，按需展开。
 */

import { Contrast, Monitor, Moon, Sun } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useSettingsStore } from "../hooks/useSettingsStore";
import { ThemePopover } from "./ThemePopover";
import type { AppearancePreference } from "./AppearanceCards";

function iconFor(pref: AppearancePreference | undefined): typeof Sun {
  if (pref === "dark") return Moon;
  if (pref === "light") return Sun;
  if (pref === "hc") return Contrast;
  return Monitor;
}

function labelFor(pref: AppearancePreference | undefined): string {
  if (pref === "dark") return "深色";
  if (pref === "light") return "浅色";
  if (pref === "hc") return "高对比度";
  return "跟随系统";
}

export function ThemeToggle() {
  const preference = useSettingsStore(
    (s) => (s.snapshot?.general?.appearance as { preference?: AppearancePreference } | undefined)?.preference,
  );
  const [open, setOpen] = useState(false);
  const triggerRef = useRef<HTMLButtonElement | null>(null);

  const Icon = iconFor(preference);
  const label = labelFor(preference);

  // 关闭后焦点回归触发按钮
  useEffect(() => {
    if (!open) {
      // 给关闭动画留时间
      const t = setTimeout(() => triggerRef.current?.focus(), 0);
      return () => clearTimeout(t);
    }
    return undefined;
  }, [open]);

  return (
    <div className="relative inline-flex">
      <button
        ref={triggerRef}
        type="button"
        className="inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-lg border-none bg-transparent text-inherit transition-colors hover:bg-foreground/[0.06] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
        onClick={() => setOpen(!open)}
        aria-label="切换主题"
        aria-expanded={open}
        aria-haspopup="dialog"
        title={label}
        data-testid="theme-toggle-trigger"
      >
        <Icon size={16} />
      </button>
      {open && <ThemePopover onClose={() => setOpen(false)} />}
    </div>
  );
}

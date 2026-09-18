/**
 * ThemeToggle (polish-theme-toggle).
 *
 * 单图标按钮（Sun/Moon/Monitor 动态）+ Popover。
 * TopBar 上只占 32px，按需展开。
 */

import { Monitor, Moon, Sun } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useSettingsStore } from "../hooks/useSettingsStore";
import { ThemePopover } from "./ThemePopover";
import type { AppearancePreference } from "./AppearanceCards";
import styles from "./ThemePopover.module.css";

function iconFor(pref: AppearancePreference | undefined): typeof Sun {
  if (pref === "dark") return Moon;
  if (pref === "light") return Sun;
  return Monitor;
}

function labelFor(pref: AppearancePreference | undefined): string {
  if (pref === "dark") return "深色";
  if (pref === "light") return "浅色";
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
    <div className={styles.wrapper}>
      <button
        ref={triggerRef}
        type="button"
        className={styles.trigger}
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

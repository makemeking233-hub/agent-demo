/**
 * ThemePopover (polish-theme-toggle).
 *
 * TopBar ThemeToggle 的弹层：3 张外观卡片 + 关闭逻辑。
 * - 点击外部关闭
 * - Esc 关闭
 * - 选中卡片后自动关闭
 * - 焦点回归触发按钮
 */

import { useEffect, useRef } from "react";
import { AppearanceCards } from "./AppearanceCards";
import styles from "./ThemePopover.module.css";

interface ThemePopoverProps {
  onClose: () => void;
}

export function ThemePopover({ onClose }: ThemePopoverProps) {
  const popoverRef = useRef<HTMLDivElement | null>(null);

  // 点击外部关闭
  useEffect(() => {
    function onMouseDown(e: MouseEvent) {
      if (popoverRef.current && !popoverRef.current.contains(e.target as Node)) {
        onClose();
      }
    }
    document.addEventListener("mousedown", onMouseDown);
    return () => document.removeEventListener("mousedown", onMouseDown);
  }, [onClose]);

  // Esc 关闭
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") {
        e.preventDefault();
        onClose();
      }
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div ref={popoverRef} className={styles.popover} role="dialog" aria-label="外观选择" data-testid="theme-popover">
      <AppearanceCards onAfterChange={onClose} />
    </div>
  );
}

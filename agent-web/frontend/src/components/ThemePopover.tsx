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
    <div
      ref={popoverRef}
      className="absolute top-[calc(100%+8px)] right-0 z-[1000] min-w-[260px] rounded-xl border border-border bg-popover p-4 text-popover-foreground shadow-[0_8px_24px_rgba(0,0,0,0.12),0_2px_8px_rgba(0,0,0,0.06)]"
      role="dialog"
      aria-label="外观选择"
      data-testid="theme-popover"
    >
      <AppearanceCards onAfterChange={onClose} />
    </div>
  );
}

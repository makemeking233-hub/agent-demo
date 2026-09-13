import { ChevronDown } from "lucide-react";
import { useEffect, useRef, useState, type ReactNode } from "react";
import styles from "./Dropdown.module.css";

/**
 * 通用下拉组件（add-models-dropdown-v0）。
 *
 * <p>对齐 dsh web 自定义下拉风格：trigger 按钮 + 弹出列表 + 键盘导航（↑↓ Enter Esc）。
 *
 * @param trigger 自定义 trigger 节点（默认渲染 "value 对应 label + ChevronDown"）
 * @param options 选项列表（label 显示 / value 提交）
 * @param value 当前选中值
 * @param onChange 选中回调
 * @param placeholder 当 value 为空时的占位文字
 * @param ariaLabel trigger 的 aria-label（无障碍）
 * @param align 弹出层水平对齐：left / right（默认 left）
 */
export interface DropdownProps {
  trigger?: ReactNode;
  options: { label: string; value: string }[];
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  ariaLabel?: string;
  align?: "left" | "right";
  disabled?: boolean;
}

export function Dropdown({
  trigger,
  options,
  value,
  onChange,
  placeholder = "选择…",
  ariaLabel,
  align = "left",
  disabled = false,
}: DropdownProps) {
  const [open, setOpen] = useState(false);
  const [focusIndex, setFocusIndex] = useState(-1);
  const containerRef = useRef<HTMLDivElement>(null);

  const current = options.find((o) => o.value === value);

  // 点击外部关闭
  useEffect(() => {
    if (!open) return;
    const onDocMouseDown = (e: MouseEvent) => {
      if (!containerRef.current) return;
      if (!containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", onDocMouseDown);
    return () => document.removeEventListener("mousedown", onDocMouseDown);
  }, [open]);

  // 键盘导航（trigger focus 时）
  const onTriggerKeyDown = (e: React.KeyboardEvent) => {
    if (disabled) return;
    if (e.key === "Enter" || e.key === " " || e.key === "ArrowDown") {
      e.preventDefault();
      setOpen(true);
      setFocusIndex(value ? options.findIndex((o) => o.value === value) : 0);
    }
  };

  // 列表项键盘导航
  const onListKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Escape") {
      e.preventDefault();
      setOpen(false);
      return;
    }
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setFocusIndex((i) => (i + 1) % options.length);
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setFocusIndex((i) => (i <= 0 ? options.length - 1 : i - 1));
    } else if (e.key === "Enter") {
      e.preventDefault();
      if (focusIndex >= 0 && focusIndex < options.length) {
        onChange(options[focusIndex].value);
        setOpen(false);
      }
    }
  };

  return (
    <div
      ref={containerRef}
      className={styles.container}
      data-testid="dropdown"
      data-open={open ? "true" : "false"}
    >
      {trigger ? (
        <div
          className={styles.trigger}
          onClick={() => !disabled && setOpen((o) => !o)}
          aria-haspopup="listbox"
          aria-expanded={open}
          aria-label={ariaLabel}
          tabIndex={disabled ? -1 : 0}
          onKeyDown={onTriggerKeyDown}
        >
          {trigger}
        </div>
      ) : (
        <button
          type="button"
          className={styles.trigger}
          onClick={() => setOpen((o) => !o)}
          disabled={disabled}
          aria-haspopup="listbox"
          aria-expanded={open}
          aria-label={ariaLabel}
          onKeyDown={onTriggerKeyDown}
        >
          <span className={current ? styles.value : styles.placeholder}>
            {current ? current.label : placeholder}
          </span>
          <ChevronDown size={14} className={styles.chevron} />
        </button>
      )}
      {open && (
        <ul
          className={`${styles.menu} ${align === "right" ? styles.alignRight : ""}`}
          role="listbox"
          onKeyDown={onListKeyDown}
          tabIndex={-1}
        >
          {options.map((o, idx) => {
            const selected = o.value === value;
            const focused = idx === focusIndex;
            return (
              <li
                key={o.value}
                role="option"
                aria-selected={selected}
                className={`${styles.option} ${selected ? styles.selected : ""} ${
                  focused ? styles.focused : ""
                }`}
                onClick={() => {
                  onChange(o.value);
                  setOpen(false);
                }}
                onMouseEnter={() => setFocusIndex(idx)}
              >
                {o.label}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
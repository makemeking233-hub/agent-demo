/**
 * OpenConfigButton (add-settings-general-items M2).
 *
 * 主按钮：在文件管理器中显示 settings.yaml。
 * Dropdown：复制路径。
 */

import { ChevronDown, Copy, FolderOpen } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { SettingsApi } from "../api/settings";
import styles from "./SettingsRows.module.css";

interface OpenConfigButtonProps {
  onToast?: (msg: string) => void;
}

const api = new SettingsApi();

export function OpenConfigButton({ onToast }: OpenConfigButtonProps) {
  const [open, setOpen] = useState(false);
  const [path, setPath] = useState<string | null>(null);
  const dropdownRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!open) return;
    function onClick(e: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", onClick);
    return () => document.removeEventListener("mousedown", onClick);
  }, [open]);

  async function handleReveal() {
    setOpen(false);
    try {
      const p = path ?? (await api.getFilePath());
      setPath(p);
      await api.reveal();
      onToast?.(`已请求在文件管理器中显示：${p}`);
    } catch (e) {
      onToast?.("reveal 失败: " + (e as Error).message);
    }
  }

  async function handleCopy() {
    setOpen(false);
    try {
      const p = path ?? (await api.getFilePath());
      setPath(p);
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(p);
      } else {
        // Fallback: 旧浏览器 execCommand
        const ta = document.createElement("textarea");
        ta.value = p;
        document.body.appendChild(ta);
        ta.select();
        document.execCommand("copy");
        document.body.removeChild(ta);
      }
      onToast?.(`已复制路径到剪贴板：${p}`);
    } catch (e) {
      onToast?.("复制失败: " + (e as Error).message);
    }
  }

  return (
    <div className={styles.headerRow}>
      <div className={styles.dropdown} ref={dropdownRef}>
        <button
          type="button"
          className={styles.openConfigButton}
          onClick={handleReveal}
          data-testid="open-config-reveal"
        >
          <FolderOpen size={14} />
          <span>在文件管理器中显示</span>
        </button>
        <button
          type="button"
          className={styles.openConfigButton}
          onClick={() => setOpen(!open)}
          aria-label="打开配置文件更多操作"
          data-testid="open-config-dropdown-trigger"
        >
          <ChevronDown size={14} />
        </button>
        {open && (
          <div className={styles.dropdownMenu}>
            <button
              type="button"
              className={styles.dropdownItem}
              onClick={handleCopy}
              data-testid="open-config-copy"
            >
              <Copy size={14} />
              <span>复制路径</span>
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

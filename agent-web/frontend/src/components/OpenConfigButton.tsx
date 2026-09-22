/**
 * OpenConfigButton (add-settings-general-items M2).
 *
 * 主按钮：在文件管理器中显示 settings.yaml。
 * Dropdown：复制路径。
 */

import { ChevronDown, Copy, FolderOpen } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { SettingsApi } from "../api/settings";

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
    <div className="flex items-center gap-2">
      <div className="relative" ref={dropdownRef}>
        <button
          type="button"
          className="inline-flex cursor-pointer items-center gap-1 rounded-md border border-border bg-background px-3 py-1.5 text-[13px] text-foreground hover:bg-foreground/[0.05]"
          onClick={handleReveal}
          data-testid="open-config-reveal"
        >
          <FolderOpen size={14} />
          <span>在文件管理器中显示</span>
        </button>
        <button
          type="button"
          className="inline-flex cursor-pointer items-center gap-1 rounded-md border border-border bg-background px-3 py-1.5 text-[13px] text-foreground hover:bg-foreground/[0.05]"
          onClick={() => setOpen(!open)}
          aria-label="打开配置文件更多操作"
          data-testid="open-config-dropdown-trigger"
        >
          <ChevronDown size={14} />
        </button>
        {open && (
          <div className="absolute top-[calc(100%+4px)] right-0 z-10 min-w-[180px] rounded-md border border-border bg-popover py-1 shadow-[0_4px_12px_rgba(0,0,0,0.1)]">
            <button
              type="button"
              className="flex w-full cursor-pointer items-center gap-2 border-none bg-transparent px-3 py-2 text-left text-[13px] text-inherit hover:bg-foreground/[0.05]"
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

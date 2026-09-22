import { Settings, Sparkles } from "lucide-react";
import type { ChatApi, ModelSelection } from "../api/chat";
import { ModelSelect } from "./ModelSelect";
import { ThemeToggle } from "./ThemeToggle";

/**
 * TopBar（→ shadcn-components-p2: 从 CSS Modules 迁到 Tailwind utility）。
 */
interface TopBarProps {
  api: ChatApi;
  /** add-provider-catalog-abstract task 9.1：完整 ModelSelection（provider + model + effort） */
  selection: ModelSelection;
  onSelectionChange?: (next: ModelSelection) => void;
  onOpenSettings: () => void;
}

export function TopBar({ api, selection, onSelectionChange, onOpenSettings }: TopBarProps) {
  return (
    <header className="flex h-12 items-center justify-between border-b border-border bg-card px-4">
      <div className="flex items-center gap-2">
        <Sparkles size={18} className="text-primary" />
        <span className="text-sm font-semibold">agent-demo</span>
        <span className="text-xs text-muted-foreground">v0.1</span>
      </div>
      <div className="flex items-center gap-1">
        {/* add-provider-catalog-abstract task 9: 两层模型菜单（provider / model / effort） */}
        <ModelSelect api={api} value={selection} onChange={onSelectionChange} />
        <ThemeToggle />
        <button
          type="button"
          className="flex cursor-pointer items-center gap-1.5 rounded-sm border border-transparent bg-transparent px-2.5 py-1.5 text-[13px] text-foreground hover:bg-secondary"
          onClick={onOpenSettings}
          aria-label="设置"
        >
          <Settings size={16} />
        </button>
      </div>
    </header>
  );
}
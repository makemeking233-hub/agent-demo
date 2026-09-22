import { Settings, Sparkles } from "lucide-react";
import type { ChatApi, ModelSelection } from "../api/chat";
import { ModelSelect } from "./ModelSelect";
import { ThemeToggle } from "./ThemeToggle";
import styles from "./TopBar.module.css";

interface TopBarProps {
  api: ChatApi;
  /** add-provider-catalog-abstract task 9.1：完整 ModelSelection（provider + model + effort） */
  selection: ModelSelection;
  onSelectionChange?: (next: ModelSelection) => void;
  onOpenSettings: () => void;
}

export function TopBar({ api, selection, onSelectionChange, onOpenSettings }: TopBarProps) {
  return (
    <header className={styles.topbar}>
      <div className={styles.brand}>
        <Sparkles size={18} className={styles.brandIcon} />
        <span className={styles.title}>agent-demo</span>
        <span className={styles.subtitle}>v0.1</span>
      </div>
      <div className={styles.actions}>
        {/* add-provider-catalog-abstract task 9: 两层模型菜单（provider / model / effort） */}
        <ModelSelect api={api} value={selection} onChange={onSelectionChange} />
        <ThemeToggle />
        <button type="button" className={styles.action} onClick={onOpenSettings} aria-label="设置">
          <Settings size={16} />
        </button>
      </div>
    </header>
  );
}

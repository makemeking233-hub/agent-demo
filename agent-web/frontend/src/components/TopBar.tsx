import { Settings, Sparkles } from "lucide-react";
import type { ChatApi } from "../api/chat";
import { ModelSelect } from "./ModelSelect";
import { ThemeToggle } from "./ThemeToggle";
import styles from "./TopBar.module.css";

interface TopBarProps {
  api: ChatApi;
  model: string;
  onModelChange?: (modelId: string) => void;
  onOpenSettings: () => void;
}

export function TopBar({ api, model, onModelChange, onOpenSettings }: TopBarProps) {
  return (
    <header className={styles.topbar}>
      <div className={styles.brand}>
        <Sparkles size={18} className={styles.brandIcon} />
        <span className={styles.title}>agent-demo</span>
        <span className={styles.subtitle}>v0.1</span>
      </div>
      <div className={styles.actions}>
        {/* add-models-dropdown-v0: 模型下拉框 */}
        <ModelSelect api={api} value={model} onChange={onModelChange} />
        <ThemeToggle />
        <button type="button" className={styles.action} onClick={onOpenSettings} aria-label="设置">
          <Settings size={16} />
        </button>
      </div>
    </header>
  );
}


import { Plus, Settings, Sparkles } from "lucide-react";
import { ThemeToggle } from "./ThemeToggle";
import styles from "./TopBar.module.css";

interface TopBarProps {
  onNewSession: () => void;
  onOpenSettings: () => void;
}

export function TopBar({ onNewSession, onOpenSettings }: TopBarProps) {
  return (
    <header className={styles.topbar}>
      <div className={styles.brand}>
        <Sparkles size={18} className={styles.brandIcon} />
        <span className={styles.title}>agent-demo</span>
        <span className={styles.subtitle}>v0.1</span>
      </div>
      <div className={styles.actions}>
        <button type="button" className={styles.action} onClick={onNewSession} aria-label="新建会话">
          <Plus size={16} />
          <span>新建会话</span>
        </button>
        <ThemeToggle />
        <button type="button" className={styles.action} onClick={onOpenSettings} aria-label="设置">
          <Settings size={16} />
        </button>
      </div>
    </header>
  );
}

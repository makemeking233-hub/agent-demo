/**
 * SettingsNav (add-settings-foundation M1).
 *
 * 4 项菜单: 通用设置 / 模型 / 插件 / Agent 预设.
 * 当前项 aria-current="true".
 */

import styles from './SettingsModal.module.css';

export interface SettingsNavItem {
  id: string;
  label: string;
}

interface SettingsNavProps {
  items: SettingsNavItem[];
  activeId: string;
  onSelect: (id: string) => void;
}

export function SettingsNav({ items, activeId, onSelect }: SettingsNavProps) {
  return (
    <>
      {items.map((item) => (
        <button
          key={item.id}
          type="button"
          className={`${styles.navItem} ${item.id === activeId ? styles.active : ''}`}
          aria-current={item.id === activeId ? 'true' : undefined}
          onClick={() => onSelect(item.id)}
          data-testid={`settings-nav-${item.id}`}
        >
          {item.label}
        </button>
      ))}
    </>
  );
}

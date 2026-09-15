/**
 * SettingsContent (add-settings-foundation M1).
 *
 * M1: 4 个菜单都显示占位文字（M2 接入具体设置项 / M3 接入模型 + 插件占位 / Agent 预设占位）。
 */

import styles from './SettingsModal.module.css';

interface SettingsContentProps {
  activeId: string;
}

const PLACEHOLDERS: Record<string, string> = {
  'general': '通用设置内容将在 add-settings-general-items 接入',
  'models': '模型设置将在 add-settings-menu-placeholders 接入',
  'plugins': '插件设置将在 add-settings-menu-placeholders 接入',
  'agent-presets': 'Agent 预设将在后续版本接入',
};

export function SettingsContent({ activeId }: SettingsContentProps) {
  const text = PLACEHOLDERS[activeId] ?? PLACEHOLDERS['general'];
  return (
    <div className={styles.placeholder} data-testid={`settings-content-${activeId}`}>
      {text}
    </div>
  );
}

/**
 * SettingsContent (add-settings-foundation M1 + add-settings-general-items M2 + add-settings-menu-placeholders M3).
 *
 * 路由 4 个菜单：通用设置 / 模型 / 插件 / Agent 预设.
 * - 通用设置：M2 接入 4 个设置组件
 * - 模型 / 插件 / Agent 预设：M3 占位
 */

import { AppearanceCards } from "./AppearanceCards";
import { EnterBehaviorSelect } from "./EnterBehaviorSelect";
import { LanguageSelect } from "./LanguageSelect";
import { PermissionModeSelect } from "./PermissionModeSelect";
import styles from "./SettingsModal.module.css";

interface SettingsContentProps {
  activeId: string;
}

function Placeholder({ text, testId }: { text: string; testId: string }) {
  return (
    <div className={styles.placeholder} data-testid={testId}>
      {text}
    </div>
  );
}

export function SettingsContent({ activeId }: SettingsContentProps) {
  if (activeId === "general") {
    return (
      <div data-testid="settings-content-general">
        <AppearanceCards />
        <PermissionModeSelect />
        <LanguageSelect />
        <EnterBehaviorSelect />
      </div>
    );
  }
  if (activeId === "models") {
    return (
      <Placeholder
        text="模型设置将在 add-settings-menu-placeholders 接入"
        testId="settings-content-models"
      />
    );
  }
  if (activeId === "plugins") {
    return (
      <Placeholder
        text="插件设置将在 add-settings-menu-placeholders 接入"
        testId="settings-content-plugins"
      />
    );
  }
  if (activeId === "agent-presets") {
    return (
      <Placeholder
        text="Agent 预设将在后续版本接入"
        testId="settings-content-agent-presets"
      />
    );
  }
  return <Placeholder text="未知菜单" testId="settings-content-unknown" />;
}

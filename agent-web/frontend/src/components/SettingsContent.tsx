/**
 * SettingsContent (add-settings-foundation M1 + add-settings-general-items M2 + add-settings-menu-placeholders M3).
 *
 * 路由 4 个菜单：通用设置 / 模型 / 插件 / Agent 预设.
 *
 * <p>add-provider-catalog-abstract task 9/10：模型菜单的 prop 升级为 `selection: ModelSelection`
 * + `reasoningEfforts: ReasoningEffort[]`（供 ModelsSection 内的两个子组件消费）。
 */

import { ChatApi, type ModelSelection, type ReasoningEffort } from "../api/chat";
import { AgentPresetsSection } from "./AgentPresetsSection";
import { AppearanceCards } from "./AppearanceCards";
import { EnterBehaviorSelect } from "./EnterBehaviorSelect";
import { LanguageSelect } from "./LanguageSelect";
import { ModelsSection } from "./ModelsSection";
import { PermissionModeSelect } from "./PermissionModeSelect";
import { PluginsSection } from "./PluginsSection";

interface SettingsContentProps {
  activeId: string;
  api: ChatApi;
  selection: ModelSelection;
  reasoningEfforts: ReasoningEffort[];
  onSelectionChange: (next: ModelSelection) => void;
  onReasoningEffortChange: (effort: string) => void;
}

export function SettingsContent({
  activeId,
  api,
  selection,
  reasoningEfforts,
  onSelectionChange,
  onReasoningEffortChange,
}: SettingsContentProps) {
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
      <ModelsSection
        api={api}
        selection={selection}
        reasoningEfforts={reasoningEfforts}
        onSelectionChange={onSelectionChange}
        onReasoningEffortChange={onReasoningEffortChange}
      />
    );
  }
  if (activeId === "plugins") {
    return <PluginsSection />;
  }
  if (activeId === "agent-presets") {
    return <AgentPresetsSection />;
  }
  return (
    <div data-testid="settings-content-unknown">
      <p>未知菜单</p>
    </div>
  );
}

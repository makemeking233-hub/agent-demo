/**
 * SettingsContent (add-settings-foundation M1 + add-settings-general-items M2 + add-settings-menu-placeholders M3).
 *
 * 路由 4 个菜单：通用设置 / 模型 / 插件 / Agent 预设.
 */

import { ChatApi, type ModelEntry } from "../api/chat";
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
  model: string;
  currentModelEntry: ModelEntry | null;
  reasoningEffort: string;
  onModelChange: (modelId: string) => void;
  onReasoningEffortChange: (effort: string) => void;
}

export function SettingsContent({
  activeId,
  api,
  model,
  currentModelEntry,
  reasoningEffort,
  onModelChange,
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
        model={model}
        currentModelEntry={currentModelEntry}
        reasoningEffort={reasoningEffort}
        onModelChange={onModelChange}
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

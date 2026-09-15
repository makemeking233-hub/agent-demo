/**
 * AgentPresetsSection (add-settings-menu-placeholders M3).
 *
 * 占位页。
 */

import { User } from "lucide-react";
import { SettingsEmpty } from "./SettingsEmpty";

export function AgentPresetsSection() {
  return (
    <SettingsEmpty
      icon={User}
      title="Agent 预设"
      description="预设常用配置组合"
      testId="settings-content-agent-presets"
    />
  );
}

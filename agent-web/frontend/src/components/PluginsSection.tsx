/**
 * PluginsSection (add-settings-menu-placeholders M3).
 *
 * 占位页。
 */

import { Plug } from "lucide-react";
import { SettingsEmpty } from "./SettingsEmpty";

export function PluginsSection() {
  return (
    <SettingsEmpty
      icon={Plug}
      title="插件"
      description="安装与管理 MCP 插件"
      testId="settings-content-plugins"
    />
  );
}

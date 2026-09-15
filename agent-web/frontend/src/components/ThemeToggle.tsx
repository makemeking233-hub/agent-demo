/**
 * ThemeToggle (add-settings-general-items M2 重构).
 *
 * 改造为 AppearanceCards 的 compact 模式壳子，复用 settings store。
 * 保留原 API 以兼容 TopBar 调用点。
 */

import { AppearanceCards } from "./AppearanceCards";

export function ThemeToggle() {
  return <AppearanceCards compact />;
}

/**
 * SettingsEmpty (add-settings-menu-placeholders M3
 * → shadcn-components-p2: 从 CSS Modules 迁到 Tailwind utility).
 *
 * 通用占位：图标 + 标题 + 描述 + "将在后续版本接入"。
 */

import type { ComponentType, SVGProps } from "react";

interface SettingsEmptyProps {
  // 用宽松类型接受 lucide 图标（ForwardRefExoticComponent）
  icon: ComponentType<SVGProps<SVGSVGElement> & { size?: number | string }>;
  title: string;
  description: string;
  testId?: string;
}

export function SettingsEmpty({ icon: Icon, title, description, testId }: SettingsEmptyProps) {
  return (
    <div
      className="flex h-full min-h-[240px] flex-col items-center justify-center gap-3 px-6 py-12 text-center text-muted-foreground"
      data-testid={testId}
    >
      <Icon size={32} className="text-muted-foreground/70" />
      <h3 className="m-0 text-base font-medium text-foreground">{title}</h3>
      <p className="m-0 text-[13px] text-muted-foreground">{description}</p>
      <p className="m-0 text-xs text-muted-foreground/70">将在后续版本接入</p>
    </div>
  );
}
/**
 * SettingsEmpty (add-settings-menu-placeholders M3).
 *
 * 通用占位：图标 + 标题 + 描述 + "将在后续版本接入"。
 */

import type { ComponentType, SVGProps } from "react";
import styles from "./SettingsEmpty.module.css";

interface SettingsEmptyProps {
  // 用宽松类型接受 lucide 图标（ForwardRefExoticComponent）
  icon: ComponentType<SVGProps<SVGSVGElement> & { size?: number | string }>;
  title: string;
  description: string;
  testId?: string;
}

export function SettingsEmpty({ icon: Icon, title, description, testId }: SettingsEmptyProps) {
  return (
    <div className={styles.empty} data-testid={testId}>
      <Icon size={32} className={styles.icon} />
      <h3 className={styles.title}>{title}</h3>
      <p className={styles.description}>{description}</p>
      <p className={styles.hint}>将在后续版本接入</p>
    </div>
  );
}

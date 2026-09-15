/**
 * AppearanceCards (add-settings-general-items M2).
 *
 * 三卡片单选：浅色 / 深色 / 跟随系统。compact 模式不带标题（用于 TopBar）。
 */

import { Monitor, Moon, Sun } from "lucide-react";
import { useSettingsStore } from "../hooks/useSettingsStore";
import styles from "./SettingsRows.module.css";

export type AppearancePreference = "light" | "dark" | "system";

interface AppearanceCardsProps {
  /** compact: 用于 TopBar；normal: 用于设置面板 */
  compact?: boolean;
  value?: AppearancePreference;
  onChange?: (value: AppearancePreference) => void;
}

const CUBES: { id: AppearancePreference; label: string; Icon: typeof Sun }[] = [
  { id: "light", label: "浅色", Icon: Sun },
  { id: "dark", label: "深色", Icon: Moon },
  { id: "system", label: "跟随系统", Icon: Monitor },
];

export function AppearanceCards({ compact = false, value: propValue, onChange: propOnChange }: AppearanceCardsProps) {
  const storeValue = useSettingsStore(
    (s) => (s.snapshot?.general?.appearance as { preference?: AppearancePreference } | undefined)?.preference ?? "system",
  );
  const patch = useSettingsStore((s) => s.patch);
  const value = propValue ?? storeValue;
  const onChange = propOnChange ?? ((v: AppearancePreference) => {
    void patch("general.appearance.preference", v);
  });

  return (
    <div className={compact ? styles.compactGroup : styles.group}>
      {!compact && <div className={styles.title}>外观</div>}
      <div className={styles.cubeRow} data-testid="appearance-cards">
        {CUBES.map(({ id, label, Icon }) => (
          <button
            key={id}
            type="button"
            className={`${styles.cube} ${value === id ? styles.selected : ""}`}
            aria-pressed={value === id}
            onClick={() => onChange(id)}
            data-testid={`appearance-card-${id}`}
          >
            <Icon size={18} />
            <span>{label}</span>
          </button>
        ))}
      </div>
    </div>
  );
}

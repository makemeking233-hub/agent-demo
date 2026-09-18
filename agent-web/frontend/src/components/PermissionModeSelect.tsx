/**
 * PermissionModeSelect (add-settings-general-items M2).
 *
 * 4 选项下拉：plan / ask / danger-full / dontAsk。
 */

import { useSettingsStore } from "../hooks/useSettingsStore";
import styles from "./SettingsRows.module.css";

export type PermissionMode = "plan" | "ask" | "danger-full" | "dontAsk";

const OPTIONS: { value: PermissionMode; label: string }[] = [
  { value: "plan", label: "Plan（只读计划）" },
  { value: "ask", label: "Ask（每次询问）" },
  { value: "danger-full", label: "Danger Full（危险权限）" },
  { value: "dontAsk", label: "Don't Ask（不询问）" },
];

export function PermissionModeSelect() {
  const value =
    (useSettingsStore(
      (s) => (s.snapshot?.general?.permission as { mode?: PermissionMode } | undefined)?.mode,
    ) ?? "ask") as PermissionMode;
  const patch = useSettingsStore((s) => s.patch);

  return (
    <div className={styles.group}>
      <div className={styles.title}>权限模式</div>
      <div className={styles.row}>
        <div className={styles.rowControl}>
          <select
            className={styles.select}
            value={value}
            onChange={(e) => void patch("general.permission.mode", e.target.value)}
            data-testid="permission-mode-select"
          >
            {OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
          <div className={styles.hint}>
            下次新会话生效（session 创建时由后端读取此值）
          </div>
        </div>
      </div>
    </div>
  );
}

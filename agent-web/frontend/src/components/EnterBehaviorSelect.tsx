/**
 * EnterBehaviorSelect (add-settings-general-items M2).
 *
 * 3 选项下拉：send / queue / newSession。
 */

import { useSettingsStore } from "../hooks/useSettingsStore";
import styles from "./SettingsRows.module.css";

export type EnterBehavior = "send" | "queue" | "newSession";

const OPTIONS: { value: EnterBehavior; label: string }[] = [
  { value: "send", label: "发送（繁忙时丢弃）" },
  { value: "queue", label: "排队发送" },
  { value: "newSession", label: "新建会话" },
];

export function EnterBehaviorSelect() {
  const value =
    (useSettingsStore(
      (s) => (s.snapshot?.general?.enterBehavior as { mode?: EnterBehavior } | undefined)?.mode,
    ) ?? "send") as EnterBehavior;
  const patch = useSettingsStore((s) => s.patch);

  return (
    <div className={styles.group}>
      <div className={styles.title}>繁忙时 Enter 键行为</div>
      <div className={styles.row}>
        <div className={styles.rowControl}>
          <select
            className={styles.select}
            value={value}
            onChange={(e) => void patch("general.enterBehavior.mode", e.target.value)}
            data-testid="enter-behavior-select"
          >
            {OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
          <div className={styles.hint}>
            仅在智能体运行时生效；Cmd/Ctrl+Enter 使用另一行为
          </div>
        </div>
      </div>
    </div>
  );
}

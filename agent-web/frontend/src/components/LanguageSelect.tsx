/**
 * LanguageSelect (add-settings-general-items M2).
 *
 * 仅 UI 占位 + localStorage 写入，不接 i18n。
 */

import { useEffect, useState } from "react";
import styles from "./SettingsRows.module.css";

type Language = "zh" | "en";

const STORAGE_KEY = "agent-demo:language-preference";
const OPTIONS: { value: Language; label: string }[] = [
  { value: "zh", label: "中文" },
  { value: "en", label: "English" },
];

function readInitial(): Language {
  if (typeof window === "undefined") return "zh";
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (raw === "zh" || raw === "en") return raw;
  } catch {
    /* ignore */
  }
  return "zh";
}

export function LanguageSelect() {
  const [value, setValue] = useState<Language>(readInitial);

  useEffect(() => {
    try {
      window.localStorage.setItem(STORAGE_KEY, value);
    } catch {
      /* ignore */
    }
  }, [value]);

  return (
    <div className={styles.group}>
      <div className={styles.title}>语言</div>
      <div className={styles.row}>
        <div className={styles.rowControl}>
          <select
            className={styles.select}
            value={value}
            onChange={(e) => setValue(e.target.value as Language)}
            data-testid="language-select"
          >
            {OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
          <div className={styles.hint}>
            语言切换将在后续版本启用完整 i18n 支持
          </div>
        </div>
      </div>
    </div>
  );
}

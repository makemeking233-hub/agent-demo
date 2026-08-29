import { Moon, Sun } from "lucide-react";
import { useEffect, useState } from "react";
import { toggleTheme } from "../lib/theme";
import styles from "./ThemeToggle.module.css";

export function ThemeToggle() {
  const [dark, setDark] = useState<boolean>(() =>
    typeof document !== "undefined" && document.body.dataset.dsDarkTheme === ""
  );

  useEffect(() => {
    const next = document.body.dataset.dsDarkTheme === "";
    setDark(next);
  }, []);

  return (
    <button
      type="button"
      className={styles.toggle}
      onClick={() => setDark(toggleTheme() === "dark")}
      aria-label={dark ? "切换为亮色主题" : "切换为暗色主题"}
    >
      {dark ? <Sun size={16} /> : <Moon size={16} />}
    </button>
  );
}

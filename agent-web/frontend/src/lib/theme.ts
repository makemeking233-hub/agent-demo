/**
 * 主题切换 (T2.3 ThemeToggle 用).
 * 复用 harness 风格: data-ds-dark-theme 挂 body, localStorage 持久化.
 */

const STORAGE_KEY = "agent-demo:theme";
type Theme = "light" | "dark";

export function getStoredTheme(): Theme | null {
  try {
    const v = localStorage.getItem(STORAGE_KEY);
    return v === "dark" || v === "light" ? v : null;
  } catch {
    return null;
  }
}

export function detectInitialTheme(): Theme {
  const stored = getStoredTheme();
  if (stored) return stored;
  if (typeof window !== "undefined" && window.matchMedia("(prefers-color-scheme: dark)").matches) {
    return "dark";
  }
  return "light";
}

export function applyTheme(theme: Theme): void {
  const body = document.body;
  if (theme === "dark") body.dataset.dsDarkTheme = "";
  else delete body.dataset.dsDarkTheme;
}

export function persistTheme(theme: Theme): void {
  try {
    localStorage.setItem(STORAGE_KEY, theme);
  } catch {}
}

export function initTheme(): void {
  applyTheme(detectInitialTheme());
}

export function toggleTheme(): Theme {
  const current: Theme = document.body.dataset.dsDarkTheme === "" ? "dark" : "light";
  const next: Theme = current === "dark" ? "light" : "dark";
  applyTheme(next);
  persistTheme(next);
  return next;
}

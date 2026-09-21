/**
 * useThemeApplication (add-settings-general-items M2
 * + shadcn-components-p1: 支持 high-contrast).
 *
 * 监听 settings store 的 appearance.preference，写入 <html data-theme>。
 * preference=system 时跟随 prefers-color-scheme。
 * preference=hc 时写 "hc"（高对比度，颜色变量定义在 src/index.css）。
 */

import { useEffect } from "react";
import { useSettingsStore } from "../hooks/useSettingsStore";

type ResolvedTheme = "light" | "dark" | "hc";

export function useThemeApplication() {
  const preference = useSettingsStore(
    (s) => (s.snapshot?.general?.appearance as { preference?: string } | undefined)?.preference ?? "system",
  );

  useEffect(() => {
    const mql = window.matchMedia("(prefers-color-scheme: dark)");
    function resolve(): ResolvedTheme {
      if (preference === "system") return mql.matches ? "dark" : "light";
      if (preference === "hc") return "hc";
      return preference === "dark" ? "dark" : "light";
    }
    function apply() {
      document.documentElement.setAttribute("data-theme", resolve());
    }
    apply();
    if (preference === "system") {
      mql.addEventListener("change", apply);
      return () => mql.removeEventListener("change", apply);
    }
    return undefined;
  }, [preference]);
}

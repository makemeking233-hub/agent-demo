/**
 * PWA Manifest 字段断言（add-pwa-support）。
 *
 * 读 public/manifest.webmanifest 静态文件（vite.config.ts 配 manifest: false 让 VitePWA 不接管），
 * 校验 4 个核心 Requirement。
 */
import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const manifestPath = resolve(__dirname, "../public/manifest.webmanifest");
const manifest = JSON.parse(readFileSync(manifestPath, "utf-8")) as {
  name: string;
  short_name: string;
  display: string;
  theme_color: string;
  icons: { sizes: string; purpose?: string }[];
};

describe("PWA manifest.webmanifest", () => {
  it("name + short_name 必填", () => {
    expect(manifest.name).toBe("Agent-Demo");
    expect(manifest.short_name).toBe("Agent");
  });

  it("icons 含三套（192x192 + 512x512 + maskable 512x512）", () => {
    expect(manifest.icons).toHaveLength(3);
    const sizes = manifest.icons.map((i) => i.sizes);
    expect(sizes).toContain("192x192");
    expect(sizes).toContain("512x512");
    const maskable = manifest.icons.find((i) => i.purpose === "maskable");
    expect(maskable).toBeTruthy();
  });

  it("display: standalone（隐藏地址栏）", () => {
    expect(manifest.display).toBe("standalone");
  });

  it("theme_color = #0969da（与现有主题色一致）", () => {
    expect(manifest.theme_color).toBe("#0969da");
  });
});
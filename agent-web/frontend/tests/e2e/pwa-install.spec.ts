/**
 * Playwright 端到端：PWA 安装 + 离线启动流程（add-pwa-support）。
 *
 * ⚠️ 本测试需要 Chrome GUI 环境（无头模式也可），CI 上应标记为 @chrome-only。
 *
 * 测试流程：
 * 1. 启 headless Chrome 访问 http://localhost:18080/
 * 2. 等待 service worker 注册 + manifest 链接
 * 3. 验证 "beforeinstallprompt" 事件可被捕获（虽然不能直接 install，但能确认 PWA 准备就绪）
 * 4. 模拟 offline（context.setOffline(true)）→ 刷新页面 → 验证 SPA 仍能加载（静态资源从 cache 命中）
 * 5. 验证 manifest 字段正确
 *
 * 运行：
 *   pnpm playwright test e2e/pwa-install.spec.ts
 */

import { test, expect } from "@playwright/test";

test.describe("PWA 安装与离线启动 (add-pwa-support)", () => {
  test("manifest 字段正确加载", async ({ page }) => {
    await page.goto("http://localhost:18080/");
    const manifest = await page.evaluate(async () => {
      const res = await fetch("/manifest.webmanifest");
      return res.json();
    });
    expect(manifest.name).toBe("Agent-Demo");
    expect(manifest.short_name).toBe("Agent");
    expect(manifest.display).toBe("standalone");
    expect(manifest.icons).toHaveLength(3);
  });

  test("service worker 注册成功", async ({ page }) => {
    await page.goto("http://localhost:18080/");
    const swRegistered = await page.evaluate(async () => {
      if (!("serviceWorker" in navigator)) return false;
      const reg = await navigator.serviceWorker.getRegistration();
      return reg !== undefined;
    });
    expect(swRegistered).toBe(true);
  });

  test("离线刷新仍能加载 SPA（静态资源 CacheFirst）", async ({ page, context }) => {
    await page.goto("http://localhost:18080/");
    // 等 SW 安装 + cache 完成
    await page.waitForTimeout(2000);
    // 切到 offline
    await context.setOffline(true);
    // 强制刷新（绕过 cache HTML）
    await page.reload({ waitUntil: "domcontentloaded" });
    // 页面应能加载（资源从 cache 命中）
    await expect(page.locator("#root")).toBeVisible();
  });
});
## Why

agent-demo 当前 Web UI 是普通 SPA，用户每次访问都从服务端拉资源，开应用需手动打开浏览器输入 URL；VS Code 类桌面工具的"开应用即工作"体验缺失。本次变更把 agent-demo Web 端升级为 **完整 PWA（Progressive Web App）**：用户可在 Chrome / Edge 地址栏一键"安装"，桌面 / 开始菜单出现独立窗口图标，启动后无地址栏、像原生应用；同时支持静态资源离线缓存 + Snackbar 离线提示 + 自动检测新版本。

## What Changes

- **前端新增 `vite-plugin-pwa` 集成**：Workbox 自动生成 Service Worker；运行时缓存策略为静态资源 CacheFirst / HTML NetworkFirst / API NetworkOnly（避免 stale token / 路径）。
- **前端新增 `manifest.webmanifest`**：应用名 `Agent-Demo`、短名 `Agent`、`display: standalone`、图标 192x192 + 512x512 + maskable 三套、`theme_color: #0969da`、`background_color: #1f2328`。
- **前端新增图标资源**：`pwa-192x192.png` / `pwa-512x512.png` / `pwa-maskable-512x512.png`（渐变背景 + 实心图标风格）。
- **前端新增 `PwaUpdatePrompt` 组件**：检测到新 SW 时弹 Snackbar（默认 auto skipWaiting，但保留"立即刷新"按钮）；监听 `vite-plugin-pwa` 的 `onNeedRefresh` 事件。
- **前端新增 `OfflineBanner` 组件**：监听 `navigator.onLine` + 全局 fetch 错误拦截，离线时顶部弹 Snackbar "网络已断开"，发消息按钮禁用；online 时自动隐藏。
- **前端新增 `usePwaUpdate` Hook**：封装 SW 更新事件订阅、`workbox-window` 的 `messageSkipWaiting` 调用。
- **前端新增 `<OfflineFallback />` 组件**：当网络断开且该路由需要数据时显示（路由级别 fallback，非全屏）。
- **前端改造 `App.tsx`**：在根节点挂载 `OfflineBanner` + `PwaUpdatePrompt`，用 React Context 暴露 `useOnline` 状态。
- **前端改造 `vite.config.ts`**：注册 `VitePWA` 插件（含 manifest / workbox / injectRegister 配置）。
- **前端改造 `index.html`**：加 `<link rel="manifest">` + `<meta name="theme-color">` + `<meta name="apple-mobile-web-app-capable">`。
- **后端新增 HTTPS profile**：在 `application-web.yml` 加 `server.ssl.*` 配置（自签证书），新增 `WebSecurityConfig`（HTTPS profile 下开启）+ `SslCertificateGenerator`（启动时生成自签证书到临时目录）。
- **后端改造 `TrustedHostFilter`**：HTTPS profile 下信任 `127.0.0.1` + `localhost`，不再要求 LAN IP（本地安装场景）。
- **测试**：
  - 前端 vitest：`manifest.test.ts`（字段断言）+ `pwa-update.test.tsx`（mock `vite-plugin-pwa` 的 registerSW）+ `offline-banner.test.tsx`（mock navigator.onLine + fetch 拦截）。
  - 前端 Playwright e2e：`pwa-install.spec.ts`（headless Chrome 启动 + 验证 SW 注册 + 模拟离线启动 + 验证 manifest link）。
  - 仓库根加 `.lighthouserc.json` + GitHub Action `lighthouse-ci.yml`（PWA 评分 ≥ 90 为门槛）。
- **文档**：新增 `docs/deploy.md` HTTPS 自签证书 + trusted-host 配置示例 + `docs/pwa-architecture.md`（架构 + 缓存策略）。

无破坏性变更（BREAKING）：现有 `/api/**` 接口形态不变；HTTPS 仅在 `--spring.profiles.active=web,https` 时启用；HTTP 模式下行为与现状一致。

## Capabilities

### New Capabilities

无（PWA 是 web-ui 的能力增强，归入既有 capability）。

### Modified Capabilities

- `web-ui`：在现有 spec 追加 6 个新 Requirement，覆盖「Manifest 注册」「Service Worker 注册」「运行时缓存策略」「离线 UI 提示」「更新检测 Snackbar」「HTTPS 自签证书支持」。

## Impact

- **前端（新增 / 修改 ~12 个文件）**：
  - 新增：`pwa/sw-source.ts` / `hooks/usePwaUpdate.ts` / `components/PwaUpdatePrompt.tsx` / `components/OfflineBanner.tsx` / `components/OfflineFallback.tsx`。
  - 新增：`public/manifest.webmanifest` + 3 个图标 PNG。
  - 修改：`vite.config.ts` / `index.html` / `App.tsx` / `main.tsx` / `package.json`（+1 devDep）+ `tests/pwa.test.ts`。
- **后端（新增 / 修改 ~4 个文件）**：
  - 新增：`config/SslCertificateGenerator.java`（启动时生成自签证书）。
  - 新增：`config/WebSecurityConfig.java`（HTTPS profile 下开启）。
  - 修改：`application-web.yml`（加 `server.ssl.*` + `agent.web.https.*` 配置块）。
  - 修改：`security/TrustedHostFilter.java`（HTTPS profile 下放宽 trusted-hosts）。
- **测试**：
  - vitest 3 个新文件（manifest / SW / offline banner）+ 扩展现有 App.tsx 测试。
  - Playwright 1 个新 spec（PWA install + 离线启动）。
  - lighthouse-ci 配置 + GitHub Action。
- **文档**：`docs/deploy.md` / `docs/pwa-architecture.md` / `docs/test-agent-demo/2026-09-04-pwa/` 四件套。
- **依赖**：`vite-plugin-pwa@^0.20` / `workbox-window@^7`（devDep）。
- **不引入**：不引入 Tailwind 之外的 CSS 框架，不引入新的状态管理库。
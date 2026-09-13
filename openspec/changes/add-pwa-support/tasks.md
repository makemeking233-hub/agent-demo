## 1. 前端基础设施

- [x] 1.1 `agent-web/frontend/package.json` 加 `vite-plugin-pwa@^0.20` + `workbox-window@^7` devDep；`npm install` 成功
- [x] 1.2 设计 + 导出 3 个图标 PNG（192x192 + 512x512 + 512x512 maskable，渐变背景 `#0969da → #6610f2` + 实心图标）放到 `agent-web/frontend/public/`
- [x] 1.3 新增 `agent-web/frontend/public/manifest.webmanifest`（name `Agent-Demo` / short `Agent` / display `standalone` / icons 三套 / theme `#0969da` / background `#1f2328`）
- [x] 1.4 `agent-web/frontend/vite.config.ts` 注册 `VitePWA` 插件：`registerType: 'autoUpdate'` + `strategies: 'generateSW'` + `injectRegister: 'auto'` + manifest + workbox runtimeCaching（`/assets/*` CacheFirst + `/index.html` NetworkFirst + `/api/**` NetworkOnly + fonts CacheFirst）
- [x] 1.5 `agent-web/frontend/index.html` 加 `<link rel="manifest">` + `<meta name="theme-color">` + `<meta name="apple-mobile-web-app-capable">` + favicon 调整

## 2. 前端组件

- [x] 2.1 新增 `frontend/src/hooks/useOnline.ts`：监听 `window.online/offline` 事件 + Context Provider；返回 `{ isOnline, retry }`
- [x] 2.2 新增 `frontend/src/components/OfflineBanner.tsx`：Snackbar 组件（顶部 6px 高度条 + "网络已断开" + 重试按钮），订阅 `useOnline` Context；`vitest` mock `navigator.onLine` 触发 online/offline 事件，断言渲染/隐藏
- [x] 2.3 新增 `frontend/src/hooks/usePwaUpdate.ts`：封装 `virtual:pwa-register`（vite-plugin-pwa 自动生成）+ `workbox-window` 的 `messageSkipWaiting`，返回 `{ needRefresh, update, offlineReady }`
- [x] 2.4 新增 `frontend/src/components/PwaUpdatePrompt.tsx`：Snackbar 组件（"检测到新版本 + 立即刷新"），订阅 `usePwaUpdate`；`vitest` mock `usePwaUpdate` 验证点击 `update()` 调 `messageSkipWaiting`
- [x] 2.5 新增 `frontend/src/components/OfflineFallback.tsx`：路由级 fallback（"网络已断开 + 重试"按钮），用 `useOnline` 决定渲染；`vitest` 覆盖
- [x] 2.6 改造 `frontend/src/App.tsx`：用 `<OnlineProvider>` 包裹整个 app + 在根节点挂载 `<OfflineBanner + PwaUpdatePrompt>`；`<Composer />` 订阅 `useOnline` 决定禁用发送按钮
- [x] 2.7 改造 `frontend/src/components/Composer.tsx`：订阅 `useOnline`，离线时禁用发送按钮 + 灰色提示

## 3. 后端 HTTPS 支持

- [x] 3.1 新增 `agent-web/.../config/SslCertificateGenerator.java`：启动监听器，检测 `https` profile + 生成 RSA 2048 自签证书（SHA256withRSA / 365 天）到 `${java.io.tmpdir}/agent-demo-cert/{cert,key}.pem`
- [x] 3.2 `agent-web/.../config/WebSecurityConfig.java`：HTTPS profile 下启用 `server.ssl.key-store` / `key-store-password` / `key-alias` / `key-store-type` 从 PEM 文件读取
- [x] 3.3 `agent-web/src/main/resources/application-web.yml` 加 `agent.web.https.enabled: false` + `spring.profiles.include: web,https` 注释说明
- [x] 3.4 `agent-web/.../security/TrustedHostFilter.java`：检测 `agent.web.https.enabled=true` 时放宽到 `127.0.0.1 + localhost`，不再强制 LAN IP

## 4. 测试 + 文档 + 收尾

- [ ] 4.1 新增 `frontend/tests/manifest.test.ts`：读 manifest 内容断言字段（name / short_name / display / icons 三套）；`vitest run` 全绿
- [ ] 4.2 扩 `frontend/tests/pwa-update.test.tsx`：mock `usePwaUpdate`，断言 `<PwaUpdatePrompt>` 点击触发 `messageSkipWaiting`；`vitest run` 全绿
- [ ] 4.3 扩 `frontend/tests/offline-banner.test.tsx`：mock `navigator.onLine`，断言 Snackbar 渲染/隐藏 + `<Composer>` 禁用按钮；`vitest run` 全绿
- [ ] 4.4 新增 `agent-web/src/test/e2e/pwa-install.spec.ts`：Playwright 启 headless Chrome + `context.installApp()` 安装 PWA + 验证 SW 注册 + offline 模式启动验证（Playwright `@chrome-only`，CI 跳）
- [ ] 4.5 仓库根加 `.lighthouserc.json`：PWA 类别 ≥ 90 分为门槛；`.github/workflows/lighthouse-ci.yml` PR 触发
- [ ] 4.6 文档：`docs/deploy.md` 加 HTTPS 自签证书 + trusted-host 配置示例；新增 `docs/pwa-architecture.md`（架构 + 缓存策略）
- [ ] 4.7 写四件套：`docs/test-agent-demo/2026-09-04-pwa/{test-design,test-cases,test-report,test-review}.md` + 更新 `test-guide.md` §2.8
- [ ] 4.8 `mvn -pl agent-web verify -DskipNpm=true`（跳过 E2E）BUILD SUCCESS + jacoco 门禁通过；`npx vitest run` 全绿；`openspec validate add-pwa-support --type change --strict` 通过 + `openspec archive add-pwa-support --yes` + commit + push
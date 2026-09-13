## Context

agent-demo 当前 Web UI 是 Vite + React 18 SPA（`agent-web/frontend/`），输出到 `src/main/resources/static/` 由 Spring Boot 托管。用户需手动打开浏览器输入 URL；缺少桌面工具常见的"开应用即工作"体验。本次变更升级为完整 PWA：Chrome/Edge 90+ 用户可一键安装到桌面，独立窗口启动 + 静态资源离线缓存 + 网络断开提示 + 新版本自动检测。

约束：
- 后端 HTTP（`127.0.0.1:18080` + trusted-host filter）—— PWA 要求 HTTPS（localhost 例外），本次新增 HTTPS profile 自签证书支持。
- Vite 6.4 + React 18 + Tailwind v4（无 Workbox）—— 引入 `vite-plugin-pwa` 自动生成 SW。
- 现有 trusted-host filter 不动 LAN 场景；HTTPS profile 下放宽到 `127.0.0.1 + localhost`。

## Goals / Non-Goals

**Goals：**

- 前端集成 `vite-plugin-pwa`，Workbox 自动生成 Service Worker。
- 运行时缓存：静态资源 CacheFirst + HTML NetworkFirst + API NetworkOnly（避免 stale token / 路径）。
- 离线 UI：`OfflineBanner` 顶部 Snackbar 提示 + 发消息按钮禁用；路由级 `<OfflineFallback />` 组件。
- 更新策略：`registerType: 'autoUpdate'` 默认 auto；检测到 `onNeedRefresh` 时弹 `<PwaUpdatePrompt />` 提示"立即刷新"。
- Manifest：name `Agent-Demo` / short `Agent` / standalone / icons 192+512+maskable / theme `#0969da`。
- 后端 HTTPS profile：`application-web.yml` 加 `server.ssl.*` 配置；`SslCertificateGenerator` 启动时生成自签证书（覆盖 `https` profile）。
- 测试：vitest + Playwright + lighthouse-ci 三层覆盖。
- 文档：`docs/deploy.md` + `docs/pwa-architecture.md` + 四件套 `2026-09-04-pwa/`。

**Non-Goals：**

- 不打包成 Tauri / Electron 桌面应用（保持 Web 应用形态）。
- 不引入推送通知（manifest v3 可选，本次不做）。
- 不实现完整离线编辑（API 必须联网，仅静态资源可离线）。
- 不强制 HTTPS 默认开启（仅 `--spring.profiles.active=web,https` 时启用）。
- 不引入 Workbox 自定义 SW（用 `vite-plugin-pwa` 默认 `generateSW` 模式）。
- 不做后台同步（Background Sync API，本次不做）。

## Decisions

### D1：用 `vite-plugin-pwa` 的 `generateSW` 而非 `injectManifest`

**理由**：
- `generateSW` 自动生成 SW，零手写代码；`runtimeCaching` 配置覆盖 90% 需求。
- `injectManifest` 需要手写 `sw.ts`，灵活但维护成本高，对当前规模过设计。
- 社区主流（4k+ star），与 Vite 6.x 兼容良好。

**实现**：`vite-plugin-pwa` 配置：
```ts
VitePWA({
  registerType: 'autoUpdate',
  strategies: 'generateSW',
  injectRegister: 'auto',  // 自动注册 + workbox-window
  manifest: { name, short_name, start_url: '/', display: 'standalone', icons, theme_color, background_color },
  workbox: {
    navigateFallback: '/index.html',
    runtimeCaching: [
      { urlPattern: /\/assets\//, handler: 'CacheFirst', options: { cacheName: 'static-assets-v1' } },
      { urlPattern: ({ url }) => url.pathname === '/index.html' || url.pathname === '/', handler: 'NetworkFirst', options: { cacheName: 'html-v1', networkTimeoutSeconds: 3 } },
      { urlPattern: /\/api\//, handler: 'NetworkOnly' },
      { urlPattern: /^https:\/\/fonts\./, handler: 'CacheFirst', options: { cacheName: 'google-fonts-v1' } }
    ]
  }
})
```

**考虑过**：`injectManifest` 手写 SW。否决：当前需求 generateSW 已足够；后续若需复杂跨页面逻辑再升级。

### D2：API 一律 `NetworkOnly`，永不缓存

**理由**：
- `/api/chat/send` / `/api/chat/stream/{id}` 是流式端点，缓存无意义。
- `/api/fs/*` 涉及用户文件系统路径，缓存会泄露历史。
- LLM API key 在请求头中，缓存策略错误会泄露凭证。
- 简单规则胜过复杂规则：所有 `/api/**` 不缓存，降低误判风险。

**实现**：Workbox `runtimeCaching` 加 `{ urlPattern: /\/api\//, handler: 'NetworkOnly' }`。

**考虑过**：按 API 分类分别用 CacheFirst / NetworkFirst / NetworkOnly。否决：粒度过细维护成本高，且多数 API 不该缓存。

### D3：图标"渐变背景 + 实心图标"风格，统一 SVG → PNG

**理由**：用户决策 #10 选渐变背景 + 实心图标风格。实心图标在 PWA 安装图标场景下视觉冲击强（OS launcher / Dock 图标都偏实心），渐变背景避免单调。

**实现**：
- 设计工具：Figma / Sketch / 在线工具（如 https://www.pwabuilder.com/imageGenerator）。
- 输出 3 个 PNG：192x192 / 512x512 / 512x512 maskable（Android adaptive icon）。
- 颜色：渐变 `#0969da → #6610f2`（项目蓝 → 紫蓝，呼应 dsh）。
- 图标内容：Agent 机器人头像 + 闪电符号（表示 AI Coding 加速）。

**考虑过**：复用现有 lucide Sparkles SVG → 转换 PNG。否决：Sparkles 是线性图标，做安装图标视觉太轻，不符合"实心"风格。

### D4：Snackbar 离线提示而非全屏遮罩

**理由**：用户决策 #6。Snackbar 不打断当前会话浏览（用户还能看历史 / 切换会话），符合 Material Design 离线模式最佳实践。

**实现**：
- `OfflineBanner` 组件：监听 `online` / `offline` 事件 + 全局 `window.addEventListener('unhandledrejection', e => ...)`。
- 顶栏下方 6px 高度条 + 文字"网络已断开" + "重试"按钮。
- 全局 Context `useOnline()`：暴露当前是否在线。
- `<Composer />` 在离线时禁用发送按钮 + 灰色提示。

**考虑过**：全屏 Modal 跳转"无法连接服务端"。否决：体验过重，用户在浏览历史时不该被强制打断。

### D5：路由级 `<OfflineFallback />` 组件而非全屏 offline.html

**理由**：用户决策 #9。SPA 路由式 fallback 保持应用壳在线，仅把"需要数据"的部分换成 fallback；导航 / UI 仍可用。

**实现**：
- `OfflineFallback` 组件：`<div className={styles.offline}><h3>网络已断开</h3><p>请检查网络后重试</p><button onClick={retry}>重试</button></div>`
- 在路由层（如 ChatPanel 内）检查 `useOnline()`，false 时渲染 `<OfflineFallback />` 替换内容。
- `<Sidebar />` 仍可点击（虽然会话列表拉不到，但 localStorage 可能缓存上次会话名）。

**考虑过**：独立 `static/offline.html`。否决：失去 SPA 体验，URL 跳离线页后返回需要额外路由处理。

### D6：后端 HTTPS 自签证书，profile 触发

**理由**：用户决策 #8。生产自签证书可让 PWA 在内网/LAN 环境直接走 HTTPS（满足 PWA install 要求）；同时不破坏现有 HTTP 模式。

**实现**：
- `application-web.yml` 加 `agent.web.https.*` 配置块 + `spring.profiles: web,https` 触发 `server.ssl.*`。
- `SslCertificateGenerator` 启动时用 Java 内置 `KeyPairGenerator` + `X500Name` 生成自签证书（RSA 2048 / SHA256withRSA / 365 天有效期），写入 `${java.io.tmpdir}/agent-demo-cert/`。
- `WebSecurityConfig` 在 HTTPS profile 下自动启用。
- `TrustedHostFilter` 检测 `ssl.enabled` 配置，HTTPS profile 下放宽到 `127.0.0.1 + localhost`。

**考虑过**：用 `keytool` 生成证书 + 外部 `.p12` 文件。否决：增加部署复杂度；运行时生成更灵活。

### D7：lighthouse-ci + Playwright 双层质量门禁

**理由**：用户决策 #7"全都要"。lighthouse-ci 守 PWA 评分（综合指标），Playwright 守实际安装/离线流程（行为验证），vitest 守单元逻辑。

**实现**：
- `.lighthouserc.json`：PWA 类别 ≥ 90 分为门槛，低于则 GitHub Action 失败。
- GitHub Action `.github/workflows/lighthouse-ci.yml`：每次 PR 触发，跑 lighthouse-ci 上传结果。
- `tests/e2e/pwa-install.spec.ts`：Playwright 启 headless Chrome + `context.installApp()` 安装 PWA + 验证桌面图标 + 关闭 Chrome + 重启验证离线启动。

**考虑过**：仅 lighthouse-ci 或仅 Playwright。否决：前者只能查 manifest / SW 是否注册，不能验证离线行为；后者不能量化 PWA 综合分。

## Risks / Trade-offs

### R1：HTTPS 自签证书会被浏览器标记为"不安全"

[Accepted] 自签证书浏览器会弹警告。生产部署需用户自行配置受信 CA（如内部 PKI）或用 Let's Encrypt。本 change 仅提供本地 + 内网测试用，文档需明确警告。

### R2：Service Worker 缓存可能显示旧版本（用户感知滞后）

[Mitigation] `registerType: 'autoUpdate'` + skipWaiting，新 SW 激活后立即接管；同时 `<PwaUpdatePrompt />` 弹"立即刷新"按钮让用户主动确认。

### R3：图标生成阻塞 PWA 实现

[Mitigation] 设计一个简版图标（蓝紫渐变背景 + 闪电 + Agent 头）作为 v0.x 起步；后续 polish 用专业设计。

### R4：lighthouse-ci GitHub Action 拉环境慢（~5min/次）

[Accepted] 仅 PR 触发，不阻塞开发；merge 后忽略 lighthouse 检查。

### R5：Playwright 端到端需要 Chrome GUI 环境

[Accepted] 标记为 `@chrome-only`，CI 上跳 e2e；本地开发可手动跑 `pnpm playwright test -- e2e/pwa-install.spec.ts`。

### R6：vite-plugin-pwa 自动生成的 SW 不能直接被 vitest 测

[Mitigation] vitest 只测 manifest 字段 + 注册逻辑 + 离线 banner 状态机；SW 内部路由行为由 Playwright 验证。

## Migration Plan

无破坏性变更：
- 现有 HTTP 模式不变（`--spring.profiles.active=web`）。
- HTTPS 仅在 `--spring.profiles.active=web,https` 时启用。
- 所有 `/api/**` 接口契约不变。

部署步骤：
1. `mvn clean package`（前端构建产物含 SW + manifest）。
2. 启动后端（HTTP 模式不变）：`mvn -pl agent-web spring-boot:run -Dspring-boot.run.profiles=web`。
3. 启用 HTTPS（可选）：`-Dspring-boot.run.profiles=web,https`，启动时自动生成自签证书到 `${java.io.tmpdir}/agent-demo-cert/`。
4. 用户在 Chrome/Edge 地址栏看到 📥 → 点击安装。

回滚：单 commit revert；删除 `public/manifest.webmanifest` + 移除 vite-plugin-pwa 插件即可恢复普通 SPA。

## Open Questions

无（10 个核心决策 + 5 个补充决策全部敲定；下个里程碑若发现 Lighthouse 评分 < 90 再开新 change 调整缓存策略）。
## ADDED Requirements

### Requirement: 注册 Web App Manifest

Web UI SHALL 在 `index.html` 引用 `/manifest.webmanifest`，manifest 包含 name / short_name / start_url / display: standalone / icons 三套（192x192 + 512x512 + maskable 512x512）/ theme_color / background_color。

#### Scenario: Manifest 链接就位

- **WHEN** 用户访问 `http://localhost:18080/`
- **THEN** `<link rel="manifest" href="/manifest.webmanifest">` 在 `<head>` 中
- **AND** manifest 内容含 `name: "Agent-Demo"` / `short_name: "Agent"` / `display: "standalone"` / `theme_color: "#0969da"`

#### Scenario: icons 三套就位

- **WHEN** manifest 被加载
- **THEN** icons 数组含 3 项（192x192 + 512x512 + maskable 512x512）
- **AND** 每项含 src / sizes / type / purpose="maskable"（maskable 项）

### Requirement: 注册 Service Worker

Web UI SHALL 在 `main.tsx` 自动注册 Workbox 生成的 Service Worker（`/sw.js`），采用 `registerType: 'autoUpdate'` + `injectRegister: 'auto'` 配置。

#### Scenario: SW 注册成功

- **WHEN** 应用启动（首次访问）
- **THEN** `navigator.serviceWorker.register('/sw.js')` 被调用
- **AND** 注册成功后 SW 状态变为 `activated`

#### Scenario: 离线时 SW 不报错

- **WHEN** SW 注册期间网络断开
- **THEN** 不抛未捕获异常，应用正常加载

### Requirement: 运行时缓存策略

Service Worker SHALL 对以下路径应用对应缓存策略：

| URL 模式 | Handler | 缓存名 | 备注 |
|---|---|---|---|
| `/assets/*` | CacheFirst | `static-assets-v1` | 静态资源永久缓存 |
| `/index.html` 或 `/` | NetworkFirst（3s 超时） | `html-v1` | 保证拿到新版本 |
| `/api/**` | NetworkOnly | — | 永不缓存（避免 stale token / 路径） |
| `https://fonts.*` | CacheFirst | `google-fonts-v1` | Google Fonts 缓存 |

#### Scenario: 静态资源 CacheFirst

- **WHEN** 用户首次访问 `/assets/index-abc.js`
- **THEN** SW 从网络拉取并缓存到 `static-assets-v1`
- **AND** 后续访问直接返回缓存（无网络）

#### Scenario: HTML NetworkFirst

- **WHEN** 用户访问 `/index.html` 且在线
- **THEN** SW 从网络拉取最新版本
- **AND** 网络失败（>3s 超时）时回退到 `html-v1` 缓存

#### Scenario: API 永不缓存

- **WHEN** 用户调 `/api/chat/send`
- **THEN** SW 不拦截，直接走网络
- **AND** 响应不被任何 Cache Storage 缓存

### Requirement: 离线 UI 提示

Web UI SHALL 在网络断开时显示 `<OfflineBanner />` 顶部 Snackbar 提示，且 `<Composer />` 发送按钮禁用 + 灰色提示"网络已断开"。

#### Scenario: 离线触发 Snackbar

- **WHEN** `window` 触发 `offline` 事件
- **THEN** `<OfflineBanner />` 在 200ms 内显示 Snackbar 文字"网络已断开"

#### Scenario: 在线隐藏 Snackbar

- **WHEN** `window` 触发 `online` 事件
- **THEN** `<OfflineBanner />` 在 200ms 内隐藏

#### Scenario: 离线时发送按钮禁用

- **WHEN** `useOnline()` 返回 false
- **THEN** `<Composer />` 发送按钮 `disabled=true` + 显示灰色提示"网络已断开"

#### Scenario: 离线时路由 fallback

- **WHEN** 用户在 `/sessions/:id` 路由且 `useOnline()` 返回 false
- **THEN** `<ChatPanel />` 渲染 `<OfflineFallback />` 组件（含"网络已断开 + 重试"按钮）

### Requirement: PWA 更新检测

Web UI SHALL 检测新版本 Service Worker，并在 `<PwaUpdatePrompt />` 弹 Snackbar 提示用户"有新版本，是否立即刷新"。

#### Scenario: 检测到新 SW

- **WHEN** 后端部署新版本后用户再次访问
- **AND** 浏览器检测到 SW 更新（`vite-plugin-pwa` 的 `onNeedRefresh` 触发）
- **THEN** `<PwaUpdatePrompt />` 在 1s 内显示 Snackbar 文字"检测到新版本" + "立即刷新"按钮

#### Scenario: 默认 auto skipWaiting

- **WHEN** 新 SW 安装完成且用户未点击"立即刷新"
- **AND** 满足 auto 策略条件（如关闭所有 tab 后下次访问）
- **THEN** 新 SW 自动激活并接管

#### Scenario: 用户点"立即刷新"

- **WHEN** 用户点击"立即刷新"按钮
- **THEN** SW `messageSkipWaiting` 被调用
- **AND** 页面立即刷新（白屏 → 新版本）

### Requirement: HTTPS 自签证书支持

后端 SHALL 在 `https` profile 启动时生成自签证书，启用 Spring Boot HTTPS，让 PWA 在非 localhost 环境也能 install。

#### Scenario: HTTPS profile 启动

- **WHEN** 启动参数 `--spring.profiles.active=web,https`
- **THEN** `SslCertificateGenerator` 启动时生成 RSA 2048 自签证书（365 天有效）
- **AND** 证书写入 `${java.io.tmpdir}/agent-demo-cert/{cert.pem, key.pem}`
- **AND** Spring Boot HTTPS 监听器在 `8443` 端口启动

#### Scenario: HTTP profile 行为不变

- **WHEN** 启动参数 `--spring.profiles.active=web`（无 https）
- **THEN** `SslCertificateGenerator` 不触发
- **AND** Spring Boot 继续监听 `18080` HTTP 端口

#### Scenario: TrustedHost 在 HTTPS 下放宽

- **WHEN** `agent.web.https.enabled=true`
- **THEN** `TrustedHostFilter` 自动信任 `127.0.0.1` + `::1` + `localhost`，不再强制 LAN IP

#### Scenario: HTTPS 自签证书被浏览器标记

- **WHEN** 用户访问 `https://localhost:8443/`
- **THEN** 浏览器显示"您的连接不是私密连接"警告
- **AND** 用户需手动点击"高级 → 继续前往"（文档说明）
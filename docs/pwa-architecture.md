# PWA 架构（add-pwa-support）

## 1. 目标

agent-demo Web 端升级为**完整 PWA**：

- Chrome/Edge 90+ 用户可一键安装到桌面，独立窗口启动（无地址栏）
- 静态资源（HTML / CSS / JS / 图标）首次访问后离线可访问
- 网络断开时显示 Snackbar 提示，发消息按钮禁用
- 新版本部署后浏览器自动检测 + 用户确认"立即刷新"

约束：Web 应用（不能调原生 API）、HTTPS profile 下自签证书支持生产部署。

## 2. 三件套架构

```
┌──────────────────────────────────────────────────────────────────┐
│                     浏览器 (Chrome / Edge)                        │
│                                                                  │
│  ┌────────────────────┐                                          │
│  │  manifest.webmanifest │ ← 应用元数据（name / icons / theme）│
│  └────────┬───────────┘                                          │
│           │ <link rel="manifest">                                │
│  ┌────────▼───────────┐  ┌────────────────────┐                  │
│  │     index.html      │  │  Service Worker    │                  │
│  │  <div id=root>     │◄─┤  /sw.js (Workbox)  │                  │
│  │  <script src=...>  │  │                    │                  │
│  └────────────────────┘  └─────────┬──────────┘                  │
│                                      │                          │
│  ┌────────────────────┐             │ Cache                     │
│  │   React 18 SPA      │             ▼                          │
│  │  (useOnline / Pwa   │  ┌────────────────────┐                 │
│  │   UpdatePrompt)     │  │  Cache Storage     │                 │
│  │                     │  │  - static-assets-v1│ CacheFirst       │
│  │                     │  │  - html-v1         │ NetworkFirst    │
│  │                     │  │  - (no /api/**)    │ NetworkOnly     │
│  └────────────────────┘  └────────────────────┘                 │
│           │                                                     │
└───────────┼─────────────────────────────────────────────────────┘
            │ HTTPS / fetch
            ▼
┌──────────────────────────────────────────────────────────────────┐
│                  Spring Boot (WebFlux)                          │
│                                                                  │
│  ┌────────────────────┐                                          │
│  │  TrustedHostFilter  │ ← HTTPS profile: 放行 127.0.0.1        │
│  │  (LAN allowlist)    │                                          │
│  └────────────────────┘                                          │
│                                                                  │
│  ┌────────────────────┐  ┌────────────────────┐                  │
│  │ /api/fs/* (FsCtrl)  │  │ /api/chat/* (ChatS)│                  │
│  │ NetworkOnly (永远   │  │ NetworkOnly (流式) │                  │
│  │ 不缓存)            │  │                    │                  │
│  └────────────────────┘  └────────────────────┘                  │
│                                                                  │
│  [https profile] SSL: 8443 + SslCertificateGenerator 启动时生成   │
│  [http profile] HTTP: 18080                                      │
└──────────────────────────────────────────────────────────────────┘
```

## 3. 缓存策略

### 3.1 路由策略（Workbox runtimeCaching）

| URL Pattern | Handler | Cache Name | TTL | 理由 |
|---|---|---|---|---|
| `/assets/*` | **CacheFirst** | `static-assets-v1` | 30 天 | 静态资源带 hash，部署后自动失效；离线必需 |
| `/` 或 `/index.html` | **NetworkFirst** (3s 超时) | `html-v1` | 浏览器关闭 | 优先新版本；网络差时回退到缓存 |
| `/api/**` | **NetworkOnly** | — | — | 永不远缓存（避免 stale token / 路径 / 凭证） |
| `https://fonts.*` | **CacheFirst** | `google-fonts-v1` | 1 年 | （项目未使用 Google Fonts，保留备用） |

### 3.2 API 永不缓存的理由

- `/api/chat/send` / `/api/chat/stream/{id}` 是流式端点，缓存无意义
- `/api/fs/*` 涉及用户文件系统路径，缓存会泄露历史
- LLM API key 在请求头中，缓存策略错误会泄露凭证
- 简单规则胜过复杂规则

### 3.3 HTML NetworkFirst 3s 超时

- 优点：保证用户拿到新版本（部署后无需手动 Ctrl+F5）
- 缺点：网络 3s 不通时回退到缓存（用户可能看到 3-5s 加载慢）
- 替代方案：StaleWhileRevalidate（先返回缓存 + 后台刷新）；当前规模下 NetworkFirst 已够用

## 4. 离线 UI 状态机

```
┌──────────┐  online event  ┌──────────┐
│  online  │──────────────►│  offline │
│          │◄──────────────│          │
│          │  offline event │          │
└──────────┘                └──────────┘
       │                          │
       │                          │
       ▼                          ▼
   UI 正常                Snackbar 弹
                       "网络已断开"
                       发消息按钮禁用
                       (composer 灰色)
                       路由级 OfflineFallback
```

**关键约束**：App 启动时 navigator.onLine 不一定准确（桌面环境 navigator.onLine 默认 true 但实际可能不通）。所以 offline 状态靠 `navigator.onLine` + 真实 fetch 失败来确认。

v0.x 仅监听 `online/offline` 事件，不监听 fetch 失败（避免误判）；v0.2 可加 fetch 拦截器。

## 5. SW 更新策略

### 5.1 默认 auto skipWaiting

```
用户访问 v1 → SW 缓存 v1
后端部署 v2
用户再次访问 → SW 后台下载 v2 + 安装
                ↓
        workbox-window waiting 事件
                ↓
        needRefresh = true → <PwaUpdatePrompt /> 弹"检测到新版本"
                ↓
   用户点击"立即刷新" → messageSkipWaiting → 页面 reload（v2 接管）
   用户没点击 → 关闭所有 tab 后下次访问自动激活
```

### 5.2 为什么需要 skipWaiting

默认 Workbox SW 行为是"等所有 tab 关闭再激活"（避免运行时不一致）。但这意味着用户必须关掉所有 tab 才能拿到新版本，UX 差。`skipWaiting` 强制立即激活。

## 6. HTTPS profile 架构

### 6.1 双协议监听

Spring Boot WebFlux 在 `https` profile 下同时监听：
- HTTP 18080（兼容旧部署）
- HTTPS 8443（PWA install 需要）

生产部署可以禁用 HTTP：去掉 `spring.profiles.active=web`，只留 `https`。

### 6.2 证书生命周期

```
启动 @Profile("https")
  ↓
SslCertificateGenerator.onApplicationEvent
  ↓
检测 agent.web.https.enabled=true
  ↓
检查 ${cert-dir}/keystore.p12
  ├─ 存在 → 跳过（重用）
  └─ 不存在 → keytool -genkeypair → 365 天有效
```

### 6.3 自签证书 + trusted-hosts 放宽

`TrustedHostFilter` 在 HTTPS profile 下自动接受 `127.0.0.1` / `::1` / `localhost`（不再强制 LAN IP），让本地 PWA install 流程不需要 trusted-hosts 配置。

## 7. 性能与安全权衡

| 维度 | 选择 | 理由 |
|---|---|---|
| SW 注册策略 | autoUpdate | 用户免感知；breaking change 才需手动 |
| 缓存版本管理 | cacheName 带 v1 标签 | 部署时升 v2 自动失效旧缓存 |
| 离线范围 | 仅静态资源 | LLM API 必须联网；离线价值有限 |
| 错误回退 | HTML NetworkFirst 3s 超时 | 平衡新版本 vs 离线体验 |
| 证书 | 自签 + 365 天 | 内网测试方便；生产需替换 |

## 8. 监控

- **Lighthouse CI**（`.github/workflows/lighthouse-ci.yml`）：每次 PR 跑 PWA 评分 ≥ 90 门槛
- **Playwright E2E**（`tests/e2e/pwa-install.spec.ts`）：验证 manifest / SW / 离线启动
- **vitest**（`tests/manifest.test.ts` 等）：单元测试 4 个核心 Requirement

## 9. 未来工作（v0.2+）

- 后台同步（Background Sync API）：离线发送消息排队 + 重连后批量提交
- Push 通知（manifest v3 push API）
- 多语言 manifest（zh-CN / en-US 切换）
- 缓存策略按用户角色分级（开发 / 普通 / 管理员）
- Workbox 升级到 v8（当前 7.x）
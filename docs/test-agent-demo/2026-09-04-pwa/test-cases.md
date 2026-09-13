# `2026-09-04-pwa/` — 测试用例清单

## 1. 前端：tests/manifest.test.ts（4 用例）

| 编号 | 场景 | 期望 |
|---|---|---|
| M-01 | name + short_name 必填 | name === "Agent-Demo", short_name === "Agent" |
| M-02 | icons 三套（192x192 + 512x512 + maskable 512x512）| icons.length === 3，含 192/512 + maskable |
| M-03 | display: standalone | display === "standalone" |
| M-04 | theme_color = #0969da | theme_color === "#0969da" |

## 2. 前端：tests/pwa-update.test.tsx（3 用例）

| 编号 | 场景 | 期望 |
|---|---|---|
| PU-01 | needRefresh=false 不渲染 | container.firstChild === null |
| PU-02 | needRefresh=true 显示 Snackbar | "检测到新版本" + role="alert" |
| PU-03 | 点击"立即刷新"触发 update() | update() called 1 次 |

## 3. 前端：tests/offline-banner.test.tsx（6 用例）

| 编号 | 场景 | 期望 |
|---|---|---|
| OB-01 | 初始 navigator.onLine 反映到 useOnline | Probe 显示 "online" |
| OB-02 | dispatch offline 事件后 isOnline 切到 false | Probe 显示 "offline" |
| OB-03 | 在线时 OfflineBanner 不渲染 | container.firstChild === null |
| OB-04 | 离线时 OfflineBanner 显示 Snackbar | "网络已断开" + role="status" |
| OB-05 | 在线时 Composer placeholder "输入消息或 /help..." | textarea 找到 |
| OB-06 | 离线时 Composer placeholder "网络已断开" + disabled | textarea disabled |

## 4. 端到端：tests/e2e/pwa-install.spec.ts（3 用例，需 Chrome）

| 编号 | 场景 | 期望 |
|---|---|---|
| E2E-01 | manifest 字段正确加载 | name/short_name/display/icons 全对 |
| E2E-02 | service worker 注册成功 | navigator.serviceWorker.getRegistration() 不为 undefined |
| E2E-03 | 离线刷新仍能加载 SPA | context.setOffline(true) + reload 仍能渲染 #root |

## 5. 后端：TrustHostFilter 现有测试（自动覆盖）

`TrustedHostFilter` 加了 `isHttpsLocalhost` 分支，但现有测试在 `https profile` 下未覆盖（需要 HTTPS 测试环境）。v0.x 接受此覆盖 gap；v0.2 添加 HttpsTrustHostFilterTest 单独覆盖。
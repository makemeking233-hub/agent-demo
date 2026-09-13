# `2026-09-04-pwa/` — 测试复盘

## 1. 流程回顾

按 OpenSpec `explore → propose → apply` 流程：
- explore：brainstorming + 10 个核心决策 + 5 个补充决策
- propose：1 个 change（add-pwa-support）+ proposal/design/specs/tasks
- apply：24 task，分 3 个 commit 全部 push
- archive：1 个 commit 收尾

## 2. 做得好的

- **vite-plugin-pwa v1.3.0 升级**：v0.20 不支持 Vite 6，升级后默认 autoUpdate + generateSW 模式零配置
- **useOnline + OnlineProvider 模式**：Context + Provider 让 Composer 内部能用 `useOnline()` 而不污染其他组件
- **Composer 拆 ComposerInner + 外层 OnlineProvider 包裹**：测试 render(<Composer />) 自动有 provider，不用改 7 个测试
- **API 永不缓存规则**（Workbox `NetworkOnly`）：简单规则胜过复杂规则；避免 stale token / 路径 / 凭证
- **HTTPS 自签证书走 keytool 子进程**：避免 sun.security.x509.* 反射不稳定 API

## 3. 可改进

- **e2e Playwright + lighthouse-ci 本地无法跑**：只配了文件，没在 CI 上验证；可能首次 PR 触发会暴露问题
- **vitest config 用 `**/tests/e2e/**` exclude**：vitest 默认 include 包含 `tests/`，但 `tests/e2e/` 是 Playwright 专用，需要手动 exclude
- **TrustedHostFilter isHttpsLocalhost 分支未单测**：现有测试都是 http 场景；v0.2 应补 HttpsTrustHostFilterTest
- **SslCertificateGenerator 启动时调 keytool 阻塞 ~5s**：可在启动期延后到第一次 https 请求时再生成

## 4. 风险与遗留

- **自签证书浏览器警告**：用户需手动"高级 → 继续前往"；生产需替换为受信证书（已写 deploy.md）
- **HTTPS 双协议监听**：Spring Boot WebFlux HTTP 18080 + HTTPS 8443 同时跑；部分部署只想 HTTPS，需配 `server.port=8443` 关 HTTP
- **vite-plugin-pwa 缓存版本**：cacheName v1 不会自动失效；下次重大版本需手动升 v2

## 5. 交付物

- 13 个新文件（5 配置 + 3 组件 + 2 hook + 1 图标生成 + 1 manifest + 1 .lighthouserc + 1 lighthouse-ci + ...）
- 4 个修改（App.tsx / Composer.tsx / WebProperties.java / TrustedHostFilter.java）
- 3 个 commit 全部 push 到 origin/main（bff39ad / d8a0742 / cecc42d）+ 1 个 archive commit
- 95/95 前端 vitest 全绿 + 153/153 后端 mvn test 全绿
- 测试文档四件套
- `docs/deploy.md` + `docs/pwa-architecture.md`

## 6. 归档状态

✅ change `add-pwa-support` 已 archive 到 `openspec/changes/archive/`。
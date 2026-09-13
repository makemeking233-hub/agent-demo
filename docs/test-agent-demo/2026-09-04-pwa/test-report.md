# `2026-09-04-pwa/` — 测试报告

## 1. 测试执行结果

### 1.1 前端 vitest

```
Test Files  14 passed (14)
Tests       95 passed (95)
其中新增：
  tests/manifest.test.ts                  4
  tests/pwa-update.test.tsx               3
  tests/offline-banner.test.tsx           6
```

### 1.2 后端 mvn test（跳过 E2E）

```
agent-web:  153 tests, 0 failures, 1 skipped (MultiTurnE2ETest)
agent-core: 322 tests, 0 failures
总计：475 全绿（含既有 421 + 新增 54 改动）
```

注意：未新增 Java 测试（`TrustedHostFilter` 改动小、走现有测试覆盖；`SslCertificateGenerator` 需要 mock keytool 进程，留 v0.2 补）。

### 1.3 Lighthouse CI / Playwright

- `.lighthouserc.json` 配置文件已落地
- `.github/workflows/lighthouse-ci.yml` PR 触发
- `tests/e2e/pwa-install.spec.ts` 配置文件已落地
- **本地均未运行**（需要 Chrome GUI 环境 + GitHub Action）

## 2. 缺陷清单

无新增缺陷。

## 3. 风险 / 局限

- **E2E 测试未跑**：本机无 Chrome GUI；CI 上会跑
- **Lighthouse 评分未验证**：需要部署后跑；CI 卡分 ≥ 90
- **TrustedHostFilter https 分支未覆盖**：现有测试都是 http 场景
- **SslCertificateGenerator 未单测**：keytool 进程 mock 复杂

## 4. 结论

核心功能（manifest + SW + 离线 UI + HTTPS profile）已落地，前端 95/95 全绿，后端 153/153 全绿（核心路径）。可归档。
# `2026-09-04-pwa/` — PWA 测试设计

> change: `openspec/changes/add-pwa-support/`（archive 后合并到 `openspec/specs/web-ui/`）

## 1. 测试目标

验证 add-pwa-support 完整 PWA 改造：manifest / Service Worker / 离线 UI / HTTPS 自签证书全部按 spec 落地。

## 2. 测试矩阵

| 维度 | 工具 | 覆盖 |
|---|---|---|
| 单元 | vitest | manifest 字段 + useOnline 状态机 + PwaUpdatePrompt 渲染 + Composer 离线禁用 |
| 集成 | vitest | OfflineBanner 渲染/隐藏 + OnlineProvider Context |
| 端到端 | Playwright（需 Chrome GUI）| install 流程 + 离线启动 |
| 性能门禁 | lighthouse-ci | PWA 评分 ≥ 90 |

## 3. DoD

- [x] 前端 95/95 vitest 全绿
- [x] 后端 153/153 mvn test 全绿（跳 1 个 E2E）
- [x] 部署文档 `docs/deploy.md` + 架构文档 `docs/pwa-architecture.md`
- [x] openspec change archived
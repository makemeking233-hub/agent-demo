# `2026-09-04-workspace-picker-v2/` — WorkspacePickerModal DSH 风格测试设计

> change: `openspec/changes/polish-workspace-picker-dsh-style/`（archive 后合并到 `openspec/specs/web-ui/`）
> 对应规范：`AGENTS.md §2.6 测试文档组织规范`

## 1. 测试目标

对 `polish-workspace-picker-dsh-style` change 做完整验证：把 `WorkspacePickerModal` 从单栏条目录表重写为 DSH 风格（顶部 ←/→/↑ + 左导航树 + 右列表 + 底部路径框），新增后端 `/api/fs/quick-access` 接口。

## 2. 环境与策略

- **后端**：JDK 17 + Spring Boot 3.2 + Maven 3.9；`@TempDir` 注入 home。
- **前端**：vitest + jsdom；mock `../api/fs`（含 `getQuickAccess`）。
- **策略**：TDD（测试先红 → 实现 → 转绿）；commit 即 push；`mvn verify` jacoco 门禁通过。

## 3. 测试矩阵

| 维度 | 后端 Java | 前端 vitest |
|---|---|---|
| 单元 | `FsControllerTest` 4 个新增（quickAccess） | `fs.test.ts` 4 个新增（getQuickAccess） |
| 集成 | 直接构造 controller | `WorkspacePickerModal.test.tsx` 6 个新增（aria-label / 导航树 / history / 列头排序 / 路径框非法 / 显示隐藏） |
| 回归 | 既有 149 个 | 既有 76 个（14 Modal + 12 fs + 50 其他） |

## 4. DoD

- [x] 后端 `mvn -pl agent-web -am test` 全绿（155 个，含新增 4）
- [x] 前端 `npx vitest run` 全绿（82 个，含新增 10）
- [x] `mvn -pl agent-web verify` jacoco 门禁通过（"All coverage checks have been met"）
- [x] openspec change archived 到 `openspec/changes/archive/`

# `2026-09-04-workspace-picker/` — WorkspacePickerModal 测试设计

> change: `openspec/changes/add-workspace-picker-modal/`（archived 完成后合并到 `openspec/specs/web-ui/`）
> 对应规范：`AGENTS.md §2.6 测试文档组织规范`（每次测试一个时间戳子目录 + 四件套）

## 1. 测试目标

对 `add-workspace-picker-modal` change 做一次完整验证，覆盖：
- 后端 fs API（`/api/fs/home` / `/api/fs/list` / `/api/fs/mkdir` / `/api/fs/drives`）功能与安全边界；
- 前端 `WorkspacePickerModal` 仿 DSH 文件选择器交互；
- Sidebar 嵌入 Modal 后的端到端链路（点 `+` → 弹 Modal → 浏览 → 选中 → 改 name → 提交 → `onCreateWorkspace`）；
- jacoco 门禁（LINE≥80% / BRANCH≥70%）通过。

## 2. 环境与策略

- **后端**：JDK 17 + Spring Boot 3.2 + Maven 3.9；`@TempDir` 注入家目录；`new FsController(new HomePathGuard(home))` 直接构造（与 WorkspaceControllerTest 风格一致）。
- **前端**：vitest 4.1 + @testing-library/react 16 + jsdom；mock `../api/fs`（用 `vi.hoisted` 让 mock 引用对 vi.mock 可见）让 Modal 初始化不依赖真实 fetch。
- **策略**：
  - TDD：测试先红 → 实现 → 转绿（每个 task 内执行）；
  - 每个 task 完成后 commit + push（per AGENTS.md §2.2）；
  - 最终 `mvn -pl agent-web verify`（含 jacoco）全绿。

## 3. 测试矩阵

| 维度 | 后端 Java | 前端 vitest |
|---|---|---|
| 单元 | `HomePathGuardTest` 13 个 + `FsControllerTest` 15 个 = 28 | `fs.test.ts` 12 个 |
| 集成 | （直接构造，不走 Spring context） | `WorkspacePickerModal.test.tsx` 14 个 + `Sidebar.test.tsx` 9 个 = 23 |
| 端到端 | `WebIntegrationTest` 既有 14 个回归 | "点 + → 弹 Modal → 浏览 → 选中 → 改 name → 提交 → 调 onCreateWorkspace" |

## 4. DoD（Definition of Done）

- [x] 后端 `mvn -pl agent-web -am test` 全绿（149 个，含新增 28）
- [x] 前端 `npx vitest run` 全绿（72 个，含新增 35）
- [x] `mvn -pl agent-web verify` jacoco 门禁通过
- [x] 所有改动 commit + push 到 origin/main
- [x] openspec change archived 到 `openspec/changes/archive/`

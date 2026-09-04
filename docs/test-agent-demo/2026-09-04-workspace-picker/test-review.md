# `2026-09-04-workspace-picker/` — 测试复盘

## 1. 流程回顾

按 `openspec-explore → openspec-propose → openspec-apply-change` 标准流程：

1. **explore 阶段**：跟用户讨论，明确 4 个核心决策（路线 🅱 / name basename 默认可编辑 / 锁家目录 / 路径可编辑跳转）+ 5 个次要决策（新建文件夹 / 自动切换 / localStorage 记忆 / 隐藏文件 / 盘符层）。
2. **propose 阶段**：用 `openspec new change add-workspace-picker-modal` 创建目录，按 proposal / design / specs / tasks 四件套铺齐。
3. **apply 阶段**：14 个 task 按 TDD + commit + push 节奏逐项实现；前 9 个 task 跑完停下来给用户汇报 + 询问，用户确认后继续 task 3.1-4.2。
4. **archive 阶段**（本批次后）：`openspec archive-change add-workspace-picker-modal` + delta spec 合并到 `openspec/specs/web-ui/spec.md`。

## 2. 做得好的

- **路径安全边界用 `toRealPath() + 前缀校验`**：单一职责 `HomePathGuard`，避免字符串拼接绕过；`..` / symlink / 大小写 / 非绝对四种越界都被挡住。
- **DI 注入 `HomePathGuard`**：`FsController(HomePathGuard guard)` 允许测试时传入 `@TempDir` 构造的 fake home，与 `WorkspaceController` 风格一致。
- **`refreshCounter` 设计**：让 React effect 在 refresh 时（同路径）也能重新拉列表，避免 stale state。
- **`vi.hoisted` 解决 mock 引用**：发现 `vi.mock` factory 不能引用顶层 var，第一时间改用 `vi.hoisted` 拿到正确引用。
- **`within(dialog)` 限定查找范围**：集成测试中 Modal 与 Sidebar 工作区列表都含 "agent-demo"，用 `within(dialog).getByText` 避免歧义。

## 3. 可改进

- **`mkdir 模式嵌套上溯逻辑**首次漏写**：第一版只校验直接父目录，测试 `mkdirCreatesNestedDirectories` 失败才发现需要沿 parent 链向上找第一个 existing 祖先。修复后新增用例 `mkdirCreatesNestedDirectories` 覆盖。
- **PowerShell `-DskipNpm` 解析问题**：前端跳过 npm build 的属性被 pwsh 误解析，浪费一轮排查；后续可以用 `cmd.exe /c` 默认绕过。
- **`HomePathGuard` 的 `path_invalid` code 未被 controller 使用**：spec 列了 `path_invalid` 但实现里没用到（mkdir 模式无根目录的情况）。算作 over-engineering 风险低，但应记录在 design.md 里。
- **端到端浏览器手动验证缺失**：task 3.3 计划的"启动 mvn spring-boot:run + 浏览器"无法在无人值守环境跑，改用 vitest "点 + → 弹 Modal → 完整链路"集成测试覆盖。真正的浏览器手测留给用户本地执行（npm run dev + http://localhost:5173）。

## 4. 风险与遗留

- **Modal 性能**：单目录条目数超过 1000 时没有虚拟滚动，渲染可能卡顿。当前规模可接受，未来条目数增长再考虑 `react-window`。
- **hidden file 过滤规则**：Windows 用 `Files.isHidden()`（DOS hidden 属性），与 `.` 开头双规则叠加。Mac/Linux 上 `Files.isHidden()` 主要识别 `.` 开头。边界条件未来跨平台测试时需要重新核验。
- **localStorage 持久化**：在隐私模式下 localStorage 抛错时静默吞掉，fallback 行为正确但无告警；可加 console.warn 便于排查。

## 5. 交付物

- 6 个新文件 + 1 个修改（HomePathGuard mkdir 嵌套）
- 5 个前端新文件 + 1 个修改（Sidebar.tsx 内联表单移除）
- 28 Java 单测 + 23 vitest 用例（其中 1 个端到端集成）
- 5 个 commit 全部 push 到 origin/main
- 测试文档四件套（test-design.md / test-cases.md / test-report.md / test-review.md）
- test-guide.md §1/§2 登记追加

## 6. 归档状态

✅ change `add-workspace-picker-modal` 已 archive 到 `openspec/changes/archive/`，delta spec 合并到 `openspec/specs/web-ui/spec.md`。

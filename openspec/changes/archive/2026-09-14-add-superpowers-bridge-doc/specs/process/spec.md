## ADDED Requirements

### Requirement: OpenSpec 与 Superpowers 职责切分

OpenSpec SHALL 负责产出变更契约:探索(`/opsx:explore`)、提案(`/opsx:propose` 出 proposal/design/specs/tasks)、验证(`openspec validate`)、归档(`/opsx:archive` 把 delta 并入 `openspec/specs/`)。

Superpowers SHALL 负责执行纪律:worktree 隔离、writing-plans 拆微任务、subagent-driven-development 派子代理、test-driven-development 红绿循环、requesting-code-review 两阶段 review、finishing-a-development-branch 收尾。

#### Scenario: 简单 bug 修复只走 OpenSpec

- **WHEN** 用户提交两行代码 bug 修复请求
- **THEN** agent 走 `/opsx:propose` + `/opsx:apply` 直接完成
- **AND** **不**叠加 Superpowers 流,避免流程过重
- **AND** `process/spec.md` 主文档明示该豁免场景

#### Scenario: 中等复杂 change 走双流

- **WHEN** 用户提交需要多文件改动 + 子代理并行 + TDD 覆盖的中等功能
- **THEN** agent 先 `/opsx:propose` 出四件套
- **AND** 用 Superpowers `writing-plans` 把 OpenSpec `tasks.md` 拆成 2-5 分钟微任务,落到 `docs/superpowers/plans/<YYYY-MM-DD>-<change-id>.md`
- **AND** 派子代理按 plan 逐任务实施,每个任务完成后回写 OpenSpec `tasks.md` 勾选
- **AND** `openspec validate --strict` 通过后 `openspec archive` 收尾

#### Scenario: 大型 change 强制双流

- **WHEN** OpenSpec `tasks.md` task 数 > 30 或单 task > 4h
- **THEN** 必须用 Superpowers `writing-plans` 拆 plan(否则 OpenSpec 强制拒绝 archive: `tasks.md` 颗粒度违规)
- **AND** plan 必须每个 task 含 `<task>` 子代理上下文片段 + TDD 红绿测试步骤

### Requirement: 防冲突规则

双流协作时 MUST 遵守以下边界规则,违反任一 SHALL 触发 OpenSpec delta spec 回写(不起第二份设计)。

#### Scenario: 需求探索以 OpenSpec 为主

- **WHEN** agent 接到新需求需调研
- **THEN** 用 `/opsx:explore` 出"设计方向"
- **AND** **不**用 Superpowers `brainstorming` 重做一遍调研
- **AND** OpenSpec `proposal.md` 引用 `brainstorming` 输出(如有)作为输入之一

#### Scenario: TDD 以 Superpowers 为主

- **WHEN** agent 实施 OpenSpec task
- **THEN** 用 Superpowers `test-driven-development` 红绿循环
- **AND** 测试失败 commit(`test: add failing test for X`)与实现 commit(`feat: implement X`)分开
- **AND** OpenSpec `tasks.md` 的"测试"项仅记录**验收场景**(Scenario 级别),不记录**测试用例级别**

#### Scenario: 验证 / 归档以 OpenSpec 为主

- **WHEN** OpenSpec task 全部勾选且本地测试全绿
- **THEN** 跑 `openspec validate --strict` + `openspec archive`
- **AND** **不**用 Superpowers `verification-before-completion` 重做一遍规格校验
- **AND** Superpowers `finishing-a-development-branch` 仅负责出 PR/合并,不替代 OpenSpec archive

#### Scenario: 实施中发现需求盲区,回写 OpenSpec delta

- **WHEN** Superpowers 子代理实施 OpenSpec task 时发现新需求(如 spec 未定义的边界条件)
- **THEN** 子代理**不**起第二份设计文档
- **AND** agent 通过 `/opsx:sync-specs <change-id>` 回写 OpenSpec delta spec
- **AND** 该 task 暂停,等待新 requirement 通过 validate 后继续

### Requirement: 双流协作的标准命令时序

agent 在新 session 接到中等以上 change 请求时 SHALL 按以下时序工作,不允许跳步或并行。

#### Scenario: 探索阶段(可选)

- **WHEN** 需求不清(用户给一句话,无明确边界)
- **THEN** `/opsx:explore` 跑一轮澄清目标/边界
- **AND** 探索结果作为 `/opsx:propose` 输入
- **AND** **不**跳到 `/opsx:propose`

#### Scenario: 提案阶段

- **WHEN** 需求清楚(用户已确认边界)
- **THEN** `/opsx:propose <change-id>` 一次性铺 proposal/design/specs/tasks
- **AND** `openspec validate <change-id> --strict` 通过
- **AND** 跳过探索直接进入 plan 阶段

#### Scenario: 计划阶段(Superpowers 桥接点)

- **WHEN** OpenSpec `tasks.md` 已铺好
- **THEN** 用 Superpowers `writing-plans` 把每个 OpenSpec task 拆成 2-5 分钟微任务
- **AND** 输出 `docs/superpowers/plans/<YYYY-MM-DD>-<change-id>.md`
- **AND** plan 中每个 task 含:`<task type="auto|inline">` + 子代理上下文片段 + TDD 红绿测试步骤 + OpenSpec task id 引用

#### Scenario: 隔离 + 实施阶段

- **WHEN** plan 已写好
- **THEN** `using-git-worktrees` 开 `feat/<change-id>` 分支(已存在则沿用)
- **AND** 按 plan 派子代理(`subagent-driven-development` 每任务独立 context)
- **AND** 任务 commit 即 push(commit 即 push 纪律见 AGENTS.md §2.2)
- **AND** plan 完成后回写 OpenSpec `tasks.md` 勾选 + push 分支

#### Scenario: 验证 + 归档阶段

- **WHEN** OpenSpec `tasks.md` 全勾 + 分支测试全绿 + push 完成
- **THEN** 主工作区 `git merge feat/<change-id>`(§2.7.5 门禁全过)
- **AND** `openspec validate <change-id> --strict` 复检
- **AND** `openspec archive <change-id> --yes` 完成归档
- **AND** `git push origin main`

### Requirement: 桥接文档与模板维护

`docs/process/open-spec-superpowers-bridge.md` SHALL 作为唯一权威流程入口,被新 session 通过 OpenSpec archive 累积流程自动发现。

#### Scenario: 新 session 接到中等以上 change

- **WHEN** 新 session 启动,接到 OpenSpec change 实施请求
- **THEN** 先读 `openspec/specs/process/spec.md`(经 archive 累积后必含本 requirement)
- **AND** 按本 spec 的"标准命令时序"执行
- **AND** **不**重新摸索流程

#### Scenario: Superpowers 工具不可用时降级

- **WHEN** agent 执行环境无 Superpowers 插件(命令 /opsx:write-plan 等不存在)
- **THEN** 跳过桥接,直接 `/opsx:apply <change-id>` 实施
- **AND** `docs/process/open-spec-superpowers-bridge.md` §"降级路径"提供指引
- **AND** `process/spec.md` 后续 archive 累积明示该降级场景

#### Scenario: 桥接文档与命令漂移时

- **WHEN** OpenSpec CLI 或 Superpowers 工具升级导致命令路径变化
- **THEN** agent 在 commit message 中标注"drift: <具体变化>"
- **AND** 本 spec 通过 `MODIFIED Requirements` 回写漂移点
- **AND** 下个 change review 时修正 `docs/process/open-spec-superpowers-bridge.md`

### Requirement: 平台与依赖前提

桥接方案 SHALL 显式声明平台前提,避免在不支持的环境强行使用。

#### Scenario: Claude Code 环境(完整桥接)

- **WHEN** 执行环境是 Claude Code + 已装 `npx openspec-superpowers`
- **THEN** 可用 `/opsx:write-plan` + `/opsx:executing-plans` 全套桥接命令
- **AND** 每个 OpenSpec task 自动转 Superpowers 微任务

#### Scenario: DSH / 其他 harness(纯文档桥接)

- **WHEN** 执行环境是 DSH / Cursor / 其他(无 `openspec-superpowers` 桥接包)
- **THEN** 走本文档§"手动组合"流程
- **AND** agent 手动跑 `openspec` CLI + 手动维护 `docs/superpowers/plans/<plan>.md`
- **AND** 享受不到子代理自动 review,但保持 spec 契约一致

#### Scenario: 纯 inline 执行(无 Superpowers)

- **WHEN** 执行环境无任何 Superpowers 工具
- **THEN** 降级到 `/opsx:apply` 实施
- **AND** **不**做微任务拆分
- **AND** OpenSpec archive 仍按 spec 累积推进
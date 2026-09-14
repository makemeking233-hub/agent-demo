## Why

agent-demo 项目已稳定使用 **OpenSpec** 作为"变更契约"(proposal / design / specs / tasks),并产出 `openspec/specs/` 主 spec 累积。但**没有把 OpenSpec 的 `tasks.md` 与 Superpowers 的"2-5 分钟微任务 + 子代理 + TDD + review"执行纪律打通** —— agent 直接 `openspec-apply-change` 实施,容易出现:
- task 颗粒度过粗(单个 task 4 小时+)
- 跨子代理边界不清晰
- 没有 TDD 红绿循环纪律
- 没有两阶段 review(合规 → 质量)

桥接后,OpenSpec 出**合同**(要改什么/为什么/验收),Superpowers 出**施工队**(worktree / 子代理 / TDD / review),token 更省、质量更稳。本 change 是**手工桥接**(避开 Claude Code-only 桥接包 `openspec-superpowers` 与 DSH 不兼容的限制),以文档 + 模板形式沉淀工作流。

## What Changes

- 新建 `docs/process/open-spec-superpowers-bridge.md`(**主交付件**):桥接工作流说明,含职责切分、命令时序、目录模板、防冲突规则。
- 新建 `docs/superpowers/plans/2026-09-13-add-superpowers-bridge-doc.md`:演示 Superpowers `writing-plans` 怎么消费 OpenSpec `tasks.md`,作为后续 change 的**模板**。
- 新建 `openspec/specs/process/spec.md`:**新增 capability**,归档 OpenSpec+Superpowers 桥接流程的标准、DO/DONT、命名约定。

**BREAKING**:无(纯文档增量)。

## Capabilities

### New Capabilities

- `process`:文档化 agent-demo 项目内 OpenSpec + Superpowers 双流协作的工作流、能力边界、防冲突规则。这是项目级**流程能力**,供所有后续 change 复用。

### Modified Capabilities

- `web-ui`:无变化。
- `cli`:无变化。

## Impact

- 新建文件:
  - `docs/process/open-spec-superpowers-bridge.md`(主交付,~150 行)
  - `docs/superpowers/plans/2026-09-13-add-superpowers-bridge-doc.md`(模板 plan,演示格式)
  - `openspec/specs/process/spec.md`(新增 capability spec)
  - `openspec/changes/add-superpowers-bridge-doc/{proposal,design,tasks,specs/process/spec}.md`(本 change artifacts)
- 既有 OpenSpec change 流程**完全不变**(此 change 不动 `openspec/specs/web-ui/` 或 `openspec/specs/cli/`)
- 不引入新依赖(纯文档)

## Out of Scope

- **不实现** Superpowers 技能文件本身(项目历史已用过 Superpowers,假设工具仍在;新 session 接入需自带 Superpowers 插件)
- **不安装** `npx openspec-superpowers` 桥接包(仅 Claude Code 兼容,DSH 用不了)
- **不实现** `openspec-verify` 命令(OpenSpec CLI 已有 `openspec validate`,足够)
- **不修改** AGENTS.md 或 `.dsh/` `.claude/` 任何命令文件(避免桥接文档影响项目级规则)
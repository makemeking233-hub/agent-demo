# 2026-09-13 add-superpowers-bridge-doc

> **类型**: 文档 + 流程规范（add-superpowers-bridge-doc）
> **来源**: 消费 `openspec/changes/add-superpowers-bridge-doc/tasks.md`
> **演示**: 本 plan 自身是用 Superpowers `writing-plans` 消费 OpenSpec tasks.md 的样板

## Goal

把 OpenSpec change `add-superpowers-bridge-doc` 的 4 个 task 组拆成 ≤10 个微任务（Superpowers writing-plans 默认上限），每个微任务含子代理上下文片段 + TDD 红绿步骤 + OpenSpec task id 引用。

## Inputs

- `openspec/changes/add-superpowers-bridge-doc/proposal.md`
- `openspec/changes/add-superpowers-bridge-doc/design.md`
- `openspec/changes/add-superpowers-bridge-doc/specs/process/spec.md`
- `openspec/changes/add-superpowers-bridge-doc/tasks.md`（OpenSpec task 列表）

## Plan

### T1. 写主交付件 `docs/process/open-spec-superpowers-bridge.md` [inline, ~5 min]

<task type="inline" openspec-task="1.1">
**目标**: 完成 `docs/process/open-spec-superpowers-bridge.md`（~150 行）

**输入**: 
- `openspec/changes/add-superpowers-bridge-doc/proposal.md` §3 Capabilities
- `openspec/changes/add-superpowers-bridge-doc/design.md` §Decisions
- `openspec/changes/add-superpowers-bridge-doc/specs/process/spec.md` 5 个 ADDED Requirement

**步骤**:
1. 写 §1 一句话边界（OpenSpec 出合同,Superpowers 出施工队）
2. 写 §2 标准命令时序 + mermaid sequenceDiagram
3. 写 §3 手动组合（DSH 环境无桥接包）
4. 写 §4 防冲突规则表
5. 写 §5 降级路径表
6. 写 §6 平台与依赖前提表
7. 写 §7 适用边界表
8. 写 §8 与 AGENTS.md §2.5/§2.7 衔接表

**验收**:
- 文档存在且可读
- §2 mermaid 序列图渲染正确（用 `participant` 不用 `actor`，无 ASCII `"`、无 `→`）
- §4-§8 表格完整

**Commit**: `docs: open-spec-superpowers-bridge 流程文档（add-superpowers-bridge-doc task 1）`
</task>

### T2. 写 plan 模板演示 `docs/superpowers/plans/2026-09-13-add-superpowers-bridge-doc.md` [inline, ~3 min]

<task type="inline" openspec-task="2.1">
**目标**: 本 plan 文件（即你正在读的）作为模板，演示 Superpowers writing-plans 怎么消费 OpenSpec tasks.md

**输入**:
- `openspec/changes/add-superpowers-bridge-doc/tasks.md`（4 个 task 组）
- `docs/superpowers/plans/2026-08-26-agent-cli-v0.1.md`（参考格式，160KB v0.1 主计划）

**步骤**:
1. 头部声明：Goal / Inputs / Plan
2. 每个微任务用 `<task>` 含 type + openspec-task id
3. 每个微任务列步骤 + 验收 + Commit 模板

**验收**:
- 文件存在
- 每个 task 含 `<task type="..." openspec-task="...">` 元数据
- 模板可被后续 change 复用

**Commit**: `docs: plan 模板演示 Superpowers 消费 OpenSpec tasks.md（add-superpowers-bridge-doc task 2）`
</task>

### T3. 验证 OpenSpec change + 跑 lint [auto, ~2 min]

<task type="auto" openspec-task="3.1 / 4.1">
**目标**: `openspec validate add-superpowers-bridge-doc --type change --strict` 通过 + git diff 自检

**步骤**:
1. 跑 `openspec validate add-superpowers-bridge-doc --type change --strict`
2. 跑 `git diff --stat openspec/changes/add-superpowers-bridge-doc/` 确认 4 个 artifacts 齐
3. 跑 `git diff openspec/changes/add-superpowers-bridge-doc/specs/process/spec.md` 确认 5 个 ADDED Requirement 完整

**验收**:
- validate 返回 "Change 'add-superworks-bridge-doc' is valid"
- git diff 显示 4 个文件：proposal.md / design.md / specs/process/spec.md / tasks.md

**Commit**: `chore: validate + diff self-check（add-superpowers-bridge-doc task 3）`
</task>

### T4. commit + push 分支 + 合并门禁 [auto, ~5 min]

<task type="auto" openspec-task="4.3 / 4.4">
**目标**: 把 OpenSpec change 4 个 artifacts + 主交付件 commit 到 `feat/add-superpowers-bridge-doc` 分支并 push + 通过 §2.7.5 合并门禁

**步骤**:
1. `git add` 显式路径（不禁止 `git add -A`）
   - `openspec/changes/add-superpowers-bridge-doc/`
   - `docs/process/open-spec-superpowers-bridge.md`
   - `docs/superpowers/plans/2026-09-13-add-superpowers-bridge-doc.md`
2. `git commit -m "docs: bridge OpenSpec + Superpowers 双流协作..."`
3. `git push -u origin feat/add-superpowers-bridge-doc`
4. `git rebase main`（如有冲突）→ `git push --force-with-lease`
5. 主工作区 `git merge feat/add-superpowers-bridge-doc --ff-only`
6. **本 change 无 Java 改动**，跳过 `mvn verify`；跳过前端 vitest（沙箱 npm ci 失败，已在 test-report 记录）
7. `git push origin main`

**验收**:
- 分支 + main 上 OpenSpec validate 通过
- §2.7.5 门禁：Java 改动=0，无需 mvn；tsc 基线 ≤ 7（无前端改动）；OpenSpec validate 通过；分支工作区干净；与 main 同步

**Commit**: `chore: merge feat/add-superpowers-bridge-doc to main（add-superbrains-bridge-doc task 4 commit）`
</task>

### T5. archive + 文档清理 [auto, ~2 min]

<task type="auto" openspec-task="4.5 / 4.6">
**目标**: `openspec archive add-superpowers-bridge-doc --yes` 把 delta 合并到 `openspec/specs/process/spec.md` + 清理

**步骤**:
1. `openspec archive add-superseeds-bridge-doc --yes`
2. 验证 `openspec/specs/process/spec.md` 含 5 个 ADDED Requirement（来自本 change 的 specs/process/spec.md）
3. `git add` 显式路径（archive 自动重命名 + delta 合并）：
   - `openspec/specs/process/spec.md`
   - `openspec/changes/archive/2026-09-13-add-superbrains-bridge-doc/`
4. `git commit -m "chore: archive add-superbrains-bridge-doc"`
5. `git push origin main`
6. `git worktree remove .worktrees/add-superbrains-bridge-doc`
7. `git branch -d feat/add-superbrains-bridge-doc`
8. `git push origin --delete feat/add-superbrains-bridge-doc`

**验收**:
- `openspec/specs/process/spec.md` 存在且含 5 个 Requirement
- `openspec/changes/add-superbrains-bridge-doc/` 不存在（已移到 archive）
- `git worktree list` 不含 add-superbrains-bridge-doc

**Commit**: `chore: archive + cleanup（add-superbrains-bridge-doc task 5）`
</task>

## Out of Plan

- 不实现 OpenSpec `verify` 子命令（OpenSpec CLI 已有 `validate`）
- 不安装 `npx openspec-superpowers` 桥接包（仅 Claude Code 兼容）
- 不修改 AGENTS.md（避免规则变更）
- 不实现 OpenSpec `writing-plans` 工具集成（手动组合）

## Summary

5 个微任务，平均 3 分钟，全部 inline + auto 混合。本 change 主要交付物是 `docs/process/open-spec-superpowers-bridge.md`，其他都是 OpenSpec artifacts 必备件。

---

**修订记录**:

- v0.1（2026-09-13）：初版
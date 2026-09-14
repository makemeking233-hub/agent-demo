## 1. 主交付件:桥接流程文档

- [ ] 1.1 新建 `docs/process/open-spec-superpowers-bridge.md`(~150 行)
  - §1 职责切分(OpenSpec 出合同,Superpowers 出施工队)
  - §2 标准命令时序(探索→提案→计划→隔离→实施→验证→归档,7 步)
  - §3 手动组合(无桥接包时的手工流程)
  - §4 防冲突规则(5 条主/副边界)
  - §5 降级路径(Superpowers 不可用时怎么处理)
  - §6 平台与依赖前提(Claude Code/DSH/Cursor/inline 四种)
  - §7 适用边界(简单 bug 不叠,中等 change 必叠,大型 change 强制叠)
  - §8 与 AGENTS.md §2.5/§2.7 的衔接(本流程是补充不覆盖)

## 2. Plan 模板演示

- [ ] 2.1 新建 `docs/superpowers/plans/2026-09-13-add-superpowers-bridge-doc.md`
  - 用本次 change 的 tasks.md(本文件)作为输入,演示 Superpowers `writing-plans` 怎么消费 OpenSpec
  - plan 中每个 task 含 `<task>` 类型标记(`auto`/`inline`)+ 子代理上下文片段 + TDD 红绿步骤
  - plan 头部引用 OpenSpec proposal/design/specs 路径

## 3. OpenSpec 主 spec 新增 capability

- [ ] 3.1 新建 `openspec/specs/process/spec.md`(add-models-dropdown-v0 后未存在)
  - 等 §2 §4 的 tasks.md 全部勾选后跑 `openspec archive add-superpowers-bridge-doc --yes`
  - archive 时 delta spec 自动落到 `openspec/specs/process/spec.md`
  - 验证:`openspec/specs/process/spec.md` 含本 change 的 5 个 ADDED Requirement

## 4. 验证与归档

- [ ] 4.1 `openspec validate add-superpowers-bridge-doc --type change --strict` 通过
- [ ] 4.2 git diff 检查本次 change 4 个 artifacts(proposal/design/specs/process/spec/tasks)无 drift
- [ ] 4.3 commit(中文 Conventional Commits)+ push 到 `feat/add-superpowers-bridge-doc` 分支
- [ ] 4.4 §2.7.5 合并门禁全过:Java 单测(无 Java 改动,无需 mvn verify);tsc 基线 ≤ 7;OpenSpec validate 通过;分支工作区干净;与 main 同步
- [ ] 4.5 `openspec archive add-superpowers-bridge-doc --yes`
- [ ] 4.6 commit + push main

---

**修订记录**:

- v0.1（2026-09-13）：初版
## Context

agent-demo 项目自 v0.1 起使用 **OpenSpec** 作为变更契约工具(`openspec/changes/<id>/{proposal,design,specs,tasks}.md` 四件套 + `openspec validate` + `openspec archive`),并已积累 30+ 个 archived change 形成 `openspec/specs/{cli,web-ui,...}/spec.md` 主 spec。**但执行环节始终没引入 Superpowers 的纪律** —— 直接 `openspec-apply-change` 实施 task,常见问题:

| 问题 | 现状 | Superpowers 解法 |
|---|---|---|
| task 颗粒度过粗 | change 动辄 49 task、~3-5 天工作量 | `writing-plans` 拆 2-5 分钟微任务,默认最多 10 个 |
| 跨子代理边界不清 | 同一个 session 内串行实施,跨 task 上下文飘移 | `subagent-driven-development` 每任务新 context |
| 缺乏 TDD 红绿循环 | 直接写实现 → 测试通过 | `test-driven-development` 红绿重构 |
| 缺两阶段 review | commit 即完 | `requesting-code-review` 合规 + 质量 |
| 缺乏执行可视化 | tasks.md 勾选靠人工 | `executing-plans` 自动回写 OpenSpec tasks.md |

**桥接必要性**(用户 2026-09-13 反馈):OpenSpec 适合"合同",Superpowers 适合"施工"。两个互补,但**职责不能重叠**。

**桥接方案**:采用**手动桥接**(避开 `npx openspec-superpowers` 与 DSH 不兼容问题):
- **不引入** 任何新依赖/插件
- **新建** `docs/process/open-spec-superpowers-bridge.md` 作为唯一权威流程文档
- **新建** `docs/superpowers/plans/2026-09-13-add-superpowers-bridge-doc.md` 作为 plan 模板示例
- **新建** `openspec/specs/process/spec.md` 作为 OpenSpec 主 spec 中的"流程" capability

## Goals / Non-Goals

**Goals:**
- 文档化"OpenSpec 出合同 + Superpowers 出施工队"的双流协作工作流
- 明确职责切分,**禁止两边各做一遍需求调研和设计**
- 提供 plan 模板,演示 Superpowers `writing-plans` 如何消费 OpenSpec `tasks.md`
- 提供命令时序,DSH/Claude Code 都能读
- 防冲突规则写进 `process` capability spec

**Non-Goals:**
- **不实现** Superpowers 技能文件(项目历史已用,新 session 接入需自带)
- **不安装** `npx openspec-superpowers` 桥接包(仅 Claude Code 兼容,DSH 用不了)
- **不修改** AGENTS.md、`.dsh/`、`.claude/` 任何已有配置(避免文档变更影响规则)
- **不实现** OpenSpec `verify` 子命令(OpenSpec CLI 已有 `validate`,足够)
- **不重新设计** OpenSpec 或 Superpowers 任何一侧(只桥接,不改源)

## Decisions

### D1: 手动桥接,跳过 npx openspec-superpowers

**理由**:
- 桥接包会把命令装到 `.claude/commands/`,**只对 Claude Code 生效**,DSH 看不到 `/opsx:write-plan` `/opsx:executing-plans` 命令
- 手动桥接以**纯文档**形式沉淀,DSH/Claude Code/Cursor 等所有 harness 都能读
- 验证成本: dry-run 显示 4 个文件新增,DSH 不兼容 → 不装

**trade-off**: 不享受"每任务新上下文 + 自动 review" 的桥接插件特性(需要执行环境自带 Superpowers)。

### D2: 文档分三层(主流程 + 模板 + spec)

```
docs/process/open-spec-superpowers-bridge.md       # 主流程 (~150 行)
docs/superpowers/plans/2026-09-13-add-superpowers-bridge-doc.md  # plan 模板
openspec/specs/process/spec.md                     # OpenSpec 主 spec（可被 archive 累积）
```

**理由**: 主流程是入口(给人类/AI 第一次看),plan 模板是样板(给 Superpowers 写 plan 时参考),spec 是机器可读契约(给 OpenSpec `openspec-archive-change` 累积 delta)。

### D3: 职责切分一句话 —— "OpenSpec 出合同,Superpowers 出施工队"

**理由**: 用户提示明确给出该边界。文档里展开为:
- OpenSpec 负责探索 / 提案 / 验证 / 归档(每一步对应一个 OpenSpec CLI 命令)
- Superpowers 负责 worktree / plans / 子代理 / review(执行纪律)
- 需求探索 → OpenSpec(避免 Superpowers brainstorming 重复)
- TDD/review → Superpowers(避免 OpenSpec 实施阶段漏掉纪律)
- 边界冲突 → 回写 OpenSpec delta,不起第二份设计

### D4: 防冲突规则写进 `process` capability spec

**理由**: 防冲突是规则,不是建议;机器可读(OpenSpec scenario)约束更稳。

| 场景 | 主 | 副 |
|---|---|---|
| 需求探索 / 边界澄清 | OpenSpec explore | Superpowers brainstorming 可省 |
| 设计 / 验收契约 | OpenSpec propose | — |
| 实施 / TDD / 子代理 | Superpowers executing-plans | OpenSpec tasks.md 勾选 |
| 验证 / 归档 | OpenSpec archive | Superpowers finishing-a-development-branch 可省 |
| 需求盲区(Superpowers 实施中发现) | OpenSpec delta spec 回写 | — |
| plan 模板 | Superpowers writing-plans 消费 OpenSpec tasks | — |

### D5: 桥接模板演示用 change 自指

**理由**: `docs/superpowers/plans/2026-09-13-add-superpowers-bridge-doc.md` 用**本次 change 的 `tasks.md`** 作为输入,演示 Superpowers `writing-plans` 怎么把 OpenSpec 任务拆成 2-5 分钟微任务。这样模板**自指、自验证、有真实数据**。

### D6: 不实现 OpenSpec `verify` 子命令(非目标)

**理由**: OpenSpec CLI 已有 `openspec validate --strict`,足够做规格校验。`verify` 是 OpenSpec 的扩展命令,不在本 change scope。

### D7: 不修改 AGENTS.md(避免规则变更)

**理由**: §2.5 OpenSpec 流程 / §2.7 分支隔离 / §2.2 TDD 已在 AGENTS.md 完整定义。本 change 只**补充**双流协作,不**覆盖**已有规则。后续 session 想用桥接流时,读 `docs/process/open-spec-superpowers-bridge.md`;不想用就按原 AGENTS.md 走。

## Risks / Trade-offs

- **[Risk] 桥接文档不被遵守** → Mitigation: `process` capability spec 用 `## Scenarios` 写明"何时必须走双流",被 OpenSpec archive 累积,后续 session 必读
- **[Risk] Superpowers 工具不可用** → Mitigation: 文档明确"plan/executing-plans 需要 Superpowers 工具链;缺失时降级到 OpenSpec-only(apply-change 直接实施)"
- **[Risk] 双份设计不同步** → Mitigation: 防冲突规则明确"OpenSpec 是主,Superpowers 是副;实施中发现盲区只回写 OpenSpec delta,不起第二份设计"
- **[Trade-off] 桥接不享受子代理 + 自动 review** → Mitigation: 文档明示"需要执行环境自带 Superpowers 插件;否则降级到 OpenSpec-only"
- **[Trade-off] 文档可能漂移** → Mitigation: `process` spec 提供"维护责任" scenario,要求每个 change 引用时 review 一次

## Migration Plan

无破坏性变更,本 change 是**纯增量**:
- 旧 session: 完全感知不到,继续按 OpenSpec-Only 流程走
- 新 session: 读 `docs/process/open-spec-superpowers-bridge.md` 决定是否启用双流

## Open Questions

- 后续是否需要更新 AGENTS.md 加 §2.8 "OpenSpec + Superpowers 双流协作"章节?(用户决策:暂不,留作下个 change)

---

**修订记录**:

- v0.1（2026-09-13）：初版
# 测试过程复盘 — fix-agent-home-isolation

- 批次目录：`docs/test-agent-demo/2026-09-18-fix-agent-home-isolation/`
- 复盘日期：2026-09-18
- 交付物：`test-design.md` / `test-cases.md` / `test-report.md` / 本文件

## 1. 流程回顾

| 阶段 | 动作 | 结果 |
|------|------|------|
| 接单 | 用户从三个候选中选定「修测试日志根目录隔离缺口」 | 见 §2.1：**根因被我说小了** |
| 定位 | grep `user.home` / `AGENT_DEMO_HOME` 全仓 | 发现 **14 处独立解析**，而非 1 处 logback |
| 冲突/范围确认 | 用弹框给出「最小可验收集」与「全部收敛」两个方案及其代价 | 用户选**全部收敛**（B） |
| 隔离 | `git worktree add .worktrees/fix-agent-home-isolation -b fix/fix-agent-home-isolation main` | 与 2 个并行 worktree 互不干扰 |
| 提案 | OpenSpec 四阶段第 2 步 | `openspec validate` 通过 |
| 实施 | `AgentPaths` 入口 → 逐点收敛 → logback → surefire | 编译通过，模块测试全绿 |
| 验证 | 探针 + 差分测量 + 内容归属 | 见 `test-report.md` §2/§3 |
| 收尾 | 四件套 → 归档 → 合并 → 复验 → 清理 | 见 §5 |

## 2. 问题与根因

### 2.1 需求层面

| 问题 | 根因 | 处置 |
|------|------|------|
| 我把根因说成「logback 写死 `user.home`」 | 只看了表象（日志写真实目录），没做全仓解析点普查 | grep 后改为「14 处解析点 + 覆盖链不一致」，并据此重新命名 change（`improve-test-hygiene` → `fix-agent-home-isolation`）与 worktree |
| 范围比预期大得多 | 「隔离」是**跨 14 个调用点的性质**，不是单点缺陷 | 用弹框把「最小可验收集（logback + surefire 三步）」与「全部收敛」摆出来让用户选，附各自代价 |

### 2.2 实施层面

| 问题 | 根因 | 处置 |
|------|------|------|
| **自造分叉**（T-1）：logback 把 `agent.demo.home` 当完整 home，Java 侧当基目录 | 写 xml 时按 `user.home` 的直觉套用，没对照 `AgentPaths` 的语义 | 探针实测抓到 → 改为 `${agent.demo.home:-${user.home}}/.agent-demo/logs`，再测位置反转确认 |
| 断言假失败（T-2） | `Path.toString()` 在 Windows 用反斜杠，我写死 `/` 前缀 | 改 `contains` |
| 断言假绿（T-3） | 不设属性时新旧实现结果相同，那条断言恒真 | 改为显式设属性再断言，并在用例文档标注「必须设属性」 |
| 权限/路径细节 | `WebAgentRuntime` 我读的是**旧 worktree** 的副本，编辑时报「需先读」 | 重新读目标 worktree 的副本再改 |

### 2.3 环境层面

| 问题 | 根因 | 处置 |
|------|------|------|
| `app.log` 差分测量被干扰 | **用户正在 IntelliJ 里运行应用**，实时写真实 `app.log` 与会话文件 | 改用 `logs/sessions/<uuid>/session.jsonl` 的 `cwd` 字段做内容归属，并把该干扰如实写进报告 |
| surefire 内 FILE appender 未落盘（未解） | 未定位 | 如实记入 `test-report.md` §6「未完成验证」，不掩盖 |

## 3. 做得好的

1. **没有停在表象**。用户描述的是「日志根没隔离」，我先做了全仓解析点普查，才拿到「14 处、覆盖链各不相同」这个真正可修的结论——否则只会给 logback 打个补丁，隔离照样漏。
2. **主动把范围决策交回用户**。发现工作量远超预期时，用弹框列出「最小可验收集」与「全部收敛」的代价对比，而不是自行缩水或闷头做完。
3. **用探针而不是推理**。logback 的属性解析语义（能否递归求值、设属性后落点在哪）不是能靠读代码确证的事；一个最小 JVM 几秒内给出决定性证据，并**当场抓到我自己造的分叉**。
4. **区分「隔离对了」与「日志坏了」**。两者表象完全相同（真实目录不增长），只看差分测量必然误判；故增加了「隔离目录内必须有产物」的正向断言。正是这条区分出了 §6 的未解项。
5. **判据可复核**。真实目录的归属一律用 `cwd` 字段/会话 id 判定，不用时间戳凭感觉；清理留审计 CSV。

## 4. 可改进的

1. **改动面估算是失准的**。接到「修日志隔离」时我按单点缺陷估，实际是跨 14 处的性质问题。后续接同类「隔离/一致性」类需求，应**先做解析点普查再估算**。
2. **第一版 xml 的错误本可避免**。`AgentPaths` 的语义是我自己刚写的，写 logback 时却没回头对照——同一改动内的一致性也需显式核对（文档里写了「同语义」，第一版实现却没做到）。
3. **对用户实时运行的应用干扰预判不足**。测量「真实目录是否增长」时没先确认有无其他写入方，导致第一轮数据不可用。后续做差分测量前应先列出**所有**可能写入方。
4. **探针应更早引入**。若在写 logback 之前就用探针确认语义，能省掉一轮完整门禁（约 4 分钟）。
5. **surefire 下 appender 未落盘未查清**。「隔离成立」的结论不依赖它，但它是本 change 边界上的一个空白，已如实记录。

## 5. 对标本项目的工程约束

| 约束 | 落实 |
|------|------|
| `§2.2` TDD | 先写 `AgentPathsTest`（含两条显式设属性的用例）→ 跑红 → 实现 → 转绿 |
| `§2.5` OpenSpec 四阶段 | explore → propose（validate 通过）→ apply → archive |
| `§2.7` 分支隔离 | 全程在 `.worktrees/fix-agent-home-isolation`；期间因 change 改名重建过一次 worktree，旧分支已删 |
| `§2.7.4` 只用显式路径 `git add` | 全部提交列显式路径 |
| `§2.7.5.1` 门禁 5 | `security` 违规对照合并前 main 逐位一致后才放行 |
| `§2.6` 四件套 | 本目录四件齐备 + `test-guide.md` 登记 |
| 全局 `§10` | 差分测量 + 内容归属 + 审计 CSV |

## 6. 遗留与移交

| # | 事项 | 建议 |
|:--:|------|------|
| 1 | surefire JVM 内 logback FILE appender 未落盘（既有现象） | 单独立项排查；不影响本 change 的 Requirement 成立 |
| 2 | jacoco `security` 包 branches 0.63（既有） | 单独立项补 `HomePathGuard` / `TrustedHostFilter` 分支用例（用户已列为待办 C） |
| 3 | 真实 `~/.agent-demo/logs/sessions/` 下的历史遗留目录（7 个，含本次改造前的测试产物） | 属运维动作；判据为 `cwd`/会话 id，用户可自行清理 |
| 4 | `HomePathGuard` 的 `user.home` 有意未改 | 它的界是「用户真实家目录」，不是 agent 数据目录；已在 design.md Open Question 1 记录 |
| 5 | `SettingsFile.resolveDefaultHome` 的 env 语义（完整路径）未改，仅在 `SettingsConfig` 调用侧统一 | 该方法的既有契约与测试保持不变；调用侧已按基目录语义传入 |

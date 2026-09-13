## 1. 测试先行（红）

- [x] 1.1 `ToolCallPairingTest`（8 例：完全无结果 / 部分结果保序 / 已配对零改动 / 幂等 / 无 tool_calls 不受影响 / 插入位置在下一条非 tool 消息之前 / 不改入参 / 多个悬挂 assistant）→ 先红（编译找不到 `ToolCallPairing`）
- [x] 1.2 `SessionResumeLoaderTest#repairsDanglingToolCallsFromInterruptedTurn`：按会话 `1789277081599` 的真实形状（assistant(EditFile) 后直接是 user）验证恢复后配对成立

## 2. 实现

- [x] 2.1 新增 `com.example.agent.core.ToolCallPairing.repair(List<Message>)`（纯函数，不改入参）
- [x] 2.2 `SessionResumeLoader.toMessages` 接入（在 `injectOrphanSkeletons` 之前）
- [x] 2.3 `AgentLoop.toRequest` 接入
- [x] 2.4 全部转绿

## 3. 文档与收尾

- [x] 3.1 `docs/design/design.md` 新增 §11.6「消息配对不变式」，含双向不变式图、两条路径接入表、破坏原因、补合成结果的取舍、以及该 400 的排查手法
- [x] 3.2 `mvn -o -pl agent-core verify`：`Tests run: 385, Failures: 0, Errors: 0` + `All coverage checks have been met` + BUILD SUCCESS
- [x] 3.3 `check-md.sh docs/design/design.md` 全部检查通过
- [x] 3.4 `openspec validate repair-dangling-tool-calls --strict` 通过 + archive + commit + push

## 4. 验收证据

| 项 | 证据 |
|----|------|
| 缺口真实存在（红） | 新增测试前 `mvn test-compile` 报 `ToolCallPairing` 找不到符号 |
| 单元行为正确（绿） | `ToolCallPairingTest` 8/8 通过 |
| 恢复路径自愈 | `SessionResumeLoaderTest` 新增用例通过（4 条消息，补的结果插在 assistant 与下一条 user 之间且 `isError=true`） |
| **真实存档自愈（端到端）** | 临时测试直接加载本机被污染的 `~/.agent-demo/sessions/1789277081599.jsonl`：`messages=32`、**配对违规 0**，且原悬挂 id `call_00_THugpr9KrymMwovF4FAV0144`（编辑 MEMORY.md 的 EditFile）被补上 `isError=true` 的合成结果。验证后该临时测试已删除，不入库 |
| 无回归 | agent-core 全量 385 用例全绿，jacoco 门禁达标 |
| 存档不被改写 | 修复只作用于内存中的消息列表；`SessionStore` 仍是 append-only |

## 5. 遗留

本机另有 4 个会话存档存在同类悬挂（`2026-08-29T13-19-51-25a91053`、`2026-08-29T13-43-45-a200cd1b`、`2026-08-29T14-23-50-b6b3574b`、`5ed84a01-b7b1-4126-8a00-8af6ffeb5b39`）。无需人工处理——恢复时自动自愈。

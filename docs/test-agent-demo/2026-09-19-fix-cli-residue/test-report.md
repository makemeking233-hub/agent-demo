# 测试报告 — fix-cli-residue

- 批次目录：`docs/test-agent-demo/2026-09-19-fix-cli-residue/`
- 执行日期：2026-09-19
- 分支：`fix/fix-cli-residue`（worktree `.worktrees/fix-cli-residue`）
- 合并点：`7969300 chore(openspec): 归档 fix-cli-residue`

## 1. 执行结果

| 项 | 合并前 main (`ab29313`) | 本分支 | 合并后 main | 判定 |
|----|---|---|---|:--:|
| agent-core | 530 / 0 | **535 / 0** | **532 / 0** | ✅ |
| agent-web | 379 / 0 | — | **379 / 0** | ✅ |
| `mvn verify` | BUILD SUCCESS | — | **BUILD SUCCESS** | ✅ |
| jacoco 违规 | 无 | — | 无 | ✅ |
| 新增测试 | — | DM-01 + AM-01 = 2 | — | ✅ |

合并后 main agent-core 532（不是分支的 535）是因为 main 期间又合入了其他 work 的提交；与本 change 无关。

## 2. 代码变更概览

| 文件 | 变更 |
|------|------|
| `AgentConfig.java:122` | `Provider.model: "deepseek-chat" → "deepseek-v4-flash"` |
| `AgentLoop.java:52` | `DEFAULT_MODEL: "deepseek-chat" → "deepseek-v4-flash"` |
| `ChatCommand.java:432-433` | 错误消息示例的 fallback 模型名 → `deepseek-v4-flash` |
| `ChatRequest.java:11` | `@param model` javadoc 示例 → `deepseek-v4-flash` |
| `InitCommandTest.java:21` | fixture 断言同步 |
| `ConfigLoaderTest.java:18` | fixture 断言同步 |
| 新增 | `AgentConfigDefaultsModelTest` / `AgentLoopDefaultModelTest` |

## 3. 缺陷清单

### 3.1 被测缺陷（已修）

| # | 缺陷 | 严重度 |
|:--:|------|:--:|
| D-1 | CLI 启动未配 model/env 时，回退到已停用的 `deepseek-chat`；web profile 已用 `deepseek-v4-flash`，两边不同源 | 🟡 中（只影响 CLI 用户；web 路径已被 `fix-stale-model-fallback` 清理） |

### 3.2 不在范围（已记录）

| # | 项 | 原因 |
|:--:|------|------|
| R-1 | `SlashCommand` 的 `/model chat` 别名 | spec 锁死向后兼容（cli/spec.md / web-ui/spec.md 都有 Scenario） |
| R-2 | `application-local.yml` | gitignored，仅本地示例 |

## 4. 数据清理

- 测试运行未写真实数据（D change 已兜底）
- 无新增 `~/.agent-demo` 写入

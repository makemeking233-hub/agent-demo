# 测试过程复盘 — fix-cli-residue

- 批次目录：`docs/test-agent-demo/2026-09-19-fix-cli-residue/`

## 1. 流程回顾

| 阶段 | 动作 |
|------|------|
| 接单 | 用户从三个候选选 C = 同时做 A + B；本 change 是 B：清 CLI 路径的 deepseek-chat 残留 |
| 定位 | grep 全仓：3 处 main 路径（AgentLoop.DEFAULT_MODEL、AgentConfig.defaults()、ChatCommand 错误消息）+ 2 处 fixture（InitCommandTest、ConfigLoaderTest）+ 1 处 javadoc（ChatRequest） |
| 设计 | OpenSpec 四阶段：proposal/design/tasks/delta（cli capability ADDED） |
| 实施 | TDD：先写 DM-01 / AM-01 跑红（断言 `deepseek-v4-flash` 拿到 `deepseek-chat`）→ 改 3 个 prod 文件 → 同步 2 个 fixture → 跑绿 |
| 归档 | 第一次失败（误把 ADDED 写成 MODIFIED），修正为 ADDED → 成功 |
| 合并 | gate 4 sync 已 up to date；合并 + 游离 worktree 复验 532+379/0 + BUILD SUCCESS |

## 2. 问题与根因

| 问题 | 根因 | 处置 |
|------|------|------|
| 第一次 openspec archive 失败 | delta spec 写了 `## MODIFIED Requirements` 但 cli/spec.md 没有同名 Requirement | 改为 `## ADDED Requirements` |
| 第一次 git add 提交失败 | pathspec 把 `openspec/changes/fix-cli-residue/...` 错写成了 OpenSpec change 子目录路径 | 用 worktree 根目录的相对路径 |
| InitCommandTest 跑红 | `run` 写入的是 `AgentConfig.defaults()`，改了 defaults 后生成的 yaml 不再含 `deepseek-chat` | 同步 fixture 断言 |

## 3. 做得好的

1. **TDD 一跑红确认 bug**：DM-01 一次断言就抓到默认 model 的偏差，避免「看着对就合并」的假绿。
2. **不动 SlashCommand 别名**：搜索时发现 `SlashCommand` 的 `deepseek-chat` 是被 spec 锁死的 `/model chat` 别名，不是残留——这点在第一次扫描时差点误改。
3. **反射读 `private static final`**：没有为测试改 production 可见性（不改 `AgentLoop.DEFAULT_MODEL` 为 public），用反射读常量 + 验修饰符。

## 4. 可改进的

1. **`AgentLoopDefaultModelTest` 用反射**：虽然避免了污染 production API，但反射读 private 字段在「字段被删/改名」时只报 NPE，不易定位。更好的方式是把 `DEFAULT_MODEL` 改成 package-private（与 `DeepSeekProvider.BASE_URL` 的改法一致）。这次没改是为最小化 prod 改动。
2. **没补 AgentConfig 全字段对默认值的快照测试**：只有 provider.model 一条断言。整套 defaults 固化下来（权限/成本/上下文/日志/memory/mcp/worktree/plugins/search/voice 等）能防「某次重构默默改了别的字段」。下次可加。

## 5. 对标本项目的工程约束

| 约束 | 落实 |
|------|------|
| `§2.2` TDD | DM-01 / AM-01 先红后绿 |
| `§2.5` OpenSpec | proposal/design/tasks/delta 齐备，archive 成功（ADDED +1） |
| `§2.7` 分支隔离 | worktree `.worktrees/fix-cli-residue`；合并后游离 worktree 复验 |
| `§2.7.5` 门禁 | gate 4 已 sync；游离 worktree 复验 532+379/0、BUILD SUCCESS |
| `§2.6` 四件套 | 本目录四件齐备（docs 直接加到 main，§2.7.6「纯文档」豁免） |
| 全局 `§10` | 未写真实数据 |

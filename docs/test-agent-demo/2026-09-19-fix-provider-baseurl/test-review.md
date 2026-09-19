# 测试过程复盘 — fix-provider-baseurl

- 批次目录：`docs/test-agent-demo/2026-09-19-fix-provider-baseurl/`

## 1. 流程回顾

| 阶段 | 动作 |
|------|------|
| 接单 | 用户从三个候选选 C = 同时做 A + B；本 change 修 A：DEEPSEEK_BASE_URL / provider.baseUrl 死路径 |
| 定位 | grep 全仓：`AgentLoopFactory:65` 用单参构造器 + `DeepSeekProvider.baseUrl():83` 返回常量 |
| 设计 | OpenSpec 四阶段第 2 步：proposal / design / tasks / delta spec（web-ui） |
| 实施 | TDD：先写 `DeepSeekProviderBaseUrlTest` 跑红（断言 customUrl 失败）→ 改 `OpenAiCompatibleProvider` 字段、`DeepSeekProvider/MiniMaxProvider.baseUrl()` 委派、`AgentLoopFactory.buildProvider` 选 1/2 参 ctor → 跑绿 |
| 验证 | agent-core 全量 533/0、agent-web 379/0 |
| 归档 | 第一次 archive 因并行 agent 改名 Requirement 头失败 → 修正后成功（archive 名用了 09-19 是 openspec 行为） |
| 合并 | gate 4 sync main（含 shadcn-infra）；merge 后游离 worktree 复验 530+379/0 + BUILD SUCCESS |

## 2. 问题与根因

| 问题 | 根因 | 处置 |
|------|------|------|
| OpenSpec 归档报 Requirement 头找不到 | 并行 agent 把同名 Requirement 从 `/api/chat/send 发送聊天消息` 改为 `发送聊天消息` | 修正 delta spec 的头对齐；场景内容不变 |
| `OpenAiCompatibleProvider.baseUrl` final 字段在 ctor 未赋值 → 编译错 | 第一次 edit 时字段声明紧跟 abstract 方法之后，字段在 ctor 之下，编译器要求 final 字段在每个 ctor 显式赋值 | 在 4 参 ctor 加 `this.baseUrl = baseUrl;` |
| 合并前 `git merge fix/...` 报「Already up to date」但实际未合 | main 与 branch tip 的祖先关系判断在某种状态混淆 | 显式 `--no-ff -m "..."` 强制创建合并提交 |

## 3. 做得好的

1. **第一版就用 TDD 跑了红**：DB-01 一次就抓到预期 `customUrl` vs 实际 `BASE_URL` 的差距——避免「看着对就合并」的假绿。
2. **同包访问 protected 方法**：避免了反射或「加 public getter」这种为测试而污染 production API 的做法。
3. **修复 `baseUrl()` 方法本身**：即便修了 buildProvider，`baseUrl()` 仍报告错误值；一并修了。
4. **既有 WireMock 测试间接保证端到端**：没有为本次 bug 写一个全量的 `AgentLoopFactory` 集成测试，而是利用既有 `DeepSeekProviderTest.streamsTextAndUsage` 已经构造 2 参 ctor + WireMock 验证 SSE 抵达——这两个测试机制在修复后行为必须保持。

## 4. 可改进的

1. **OpenSpec delta 头与并行 agent 的 rename 冲突**：写 proposal 时就 grep `openspec/specs/` 当前所有 Requirement 头，避免归档时才发现改名。本次的修复（改 delta 头）是事后补偿，且场景内容与原 Requirement 一致，所以是可逆的；但若并行 agent 删了相关 Scenario 会不可逆。
2. **没补 `AgentLoopFactory` 的端到端测试**：依赖既有 `DeepSeekProviderTest` 间接覆盖是省事，但不是显式的「`buildProvider` 选 2 参 ctor」断言。后续若有人改回单参 ctor（看似无伤大雅），这套间接覆盖仍然绿。

## 5. 对标本项目的工程约束

| 约束 | 落实 |
|------|------|
| `§2.2` TDD | DB-01 跑红后实现 → 转绿 |
| `§2.5` OpenSpec | proposal/design/tasks/delta 齐备，archive 成功（delta spec 头已与并行 agent 改动对齐） |
| `§2.7` 分支隔离 | worktree `.worktrees/fix-provider-baseurl`；合并后游离 worktree 复验 |
| `§2.7.5` 门禁 | gate 4 已 sync；游离 worktree 复验 530+379/0、BUILD SUCCESS |
| `§2.6` 四件套 | 本目录四件齐备（docs 直接加到 main，§2.7.6「纯文档」豁免） |
| 全局 `§10` | 未写真实数据 |

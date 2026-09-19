# 测试报告 — fix-provider-baseurl

- 批次目录：`docs/test-agent-demo/2026-09-19-fix-provider-baseurl/`
- 执行日期：2026-09-18（合并日 2026-09-19）
- 分支：`fix/fix-provider-baseurl`（worktree `.worktrees/fix-provider-baseurl`）
- 合并点：`d433141 Merge branch 'fix/fix-provider-baseurl'`（main HEAD）

## 1. 执行结果

| 项 | 合并前 main (`1d8a0ad`) | 本分支 | 合并后 main | 判定 |
|----|---|---|---|:--:|
| agent-core | 528 / 0 | 533 / 0 | **530 / 0** | ✅ |
| agent-web | 379 / 0 | — | **379 / 0** | ✅ |
| `mvn verify` | BUILD SUCCESS | — | **BUILD SUCCESS** | ✅ |
| jacoco 违规 | 无 | — | 无 | ✅ |
| `DeepSeekProviderBaseUrlTest`（新增） | n/a | 2 / 0 | — | ✅ |

合并后 main 的 agent-core 530（不是分支的 533）是因为 main 在此期间又合了其他 work 的提交；与本 change 无关。

## 2. 代码变更概览

| 文件 | 变更 |
|------|------|
| `OpenAiCompatibleProvider.java` | 构造器把 `baseUrl` 存为 `protected final` 字段；`baseUrl()` 由 abstract 改非抽象 `return baseUrl` |
| `DeepSeekProvider.java` | `baseUrl()` 改 `return super.baseUrl()`；`BASE_URL` 从 `private` 改 package-private（子类默认 fallback） |
| `MiniMaxProvider.java` | `baseUrl()` 改 `return super.baseUrl()` |
| `AgentLoopFactory.java` | `buildProvider` 引入 `baseUrlOf(cfg)`：cfg.baseUrl 非空时选 2 参 ctor |
| 测试 | 新增 `DeepSeekProviderBaseUrlTest` 2 例（与 `DeepSeekProvider` 同包，访问 protected 方法） |

## 3. 缺陷清单

### 3.1 被测缺陷

| # | 缺陷 | 严重度 | 证据 |
|:--:|------|:--:|------|
| P-1 | `DEEPSEEK_BASE_URL` 与 `provider.baseUrl` 完全无效 | 🔴 高（自部署/代理/本地桩上游全失效） | `AgentLoopFactory.buildProvider:65` 用单参构造器，cfg.baseUrl 被丢弃 |
| P-2 | `DeepSeekProvider.baseUrl()` 永远返回硬编码常量 | 🟡 中（用于报告/校验时报告错误值） | `DeepSeekProvider:83` |
| P-3 | `MiniMaxProvider.baseUrl()` 同模式 | 🟡 中 | `MiniMaxProvider:86` |

### 3.2 测试过程中发现的问题

| # | 问题 | 处置 |
|:--:|------|------|
| T-1 | OpenSpec 归档时同名 Requirement 已被并行 agent 改名 | 修正 delta spec 的 Requirement 头对齐，场景内容不变 |
| T-2 | 同一 baseUrl 字段在 OpenAiCompatibleProvider 中未赋值（final 字段须在 ctor 显式赋值） | ctor 加 `this.baseUrl = baseUrl;` 显式赋值 |
| T-3 | 合并时 main 已含并行 agent 的 shadcn-infra 提交 | 走 gate 4 sync；merge 后在游离 worktree 复验 |

## 4. 数据清理（全局规则 §10）

- 测试运行未写真实数据（D change 已兜底）。
- 无新增 `~/.agent-demo` 写入。
- 游离 worktree 已清理。

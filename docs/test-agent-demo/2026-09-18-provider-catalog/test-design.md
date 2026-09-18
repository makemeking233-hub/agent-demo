# 测试设计：add-provider-catalog-abstract（provider 分层目录）

> 批次目录：`docs/test-agent-demo/2026-09-18-provider-catalog/`
> 关联 change：`openspec/changes/add-provider-catalog-abstract/`（已 archive）
> 测试执行起始日：2026-09-18

## 1. 测试范围

| 层 | 覆盖对象 | 不在范围 |
|----|---------|---------|
| 后端 agent-core | `ProviderInference` / `DeepSeekProvider.validateProviderHook` / `MiniMaxProvider.validateProviderHook` / `SlashCommand` `/model` 路径 | 真实 LLM 调用 |
| 后端 agent-web | `ChatStreamService` 6 参重载透传 / `ChatController` provider 推断与兜底 | 真实企业网关 / 多 provider HTTP 路由（v0.2 未实现） |
| 前端 | `chat.ts` 类型与 localStorage 工具 / `ModelSelect` 两层菜单 / `ReasoningEffortSelect` options prop | Playwright E2E（沙箱不可跑） |
| 文档 | `provider-catalog.md` 与实现一致性 | — |

## 2. 测试目标

1. **provider 推断正确性**：模型名前缀 → provider id 的映射覆盖三大 provider 家族 + 未知模型返回 null
2. **provider 校验一致性**：`validateProviderHook` 在 `extra.provider` 不匹配时抛错，缺失时向后兼容放过
3. **provider 透传链路**：`ChatController` → `ChatStreamService` 6 参重载 → `AgentLoop.setProviderId`
4. **BREAKING 迁移安全性**：localStorage 旧格式（无 provider）不炸、能自动推断
5. **两层菜单交互**：provider 切换刷新右栏 / model 选择触发 onChange / effort 联动 / 关闭路径
6. **CLI 兼容性**：`/model chat` 等 v0.1 别名继续可用；完整 provider 路径生效

## 3. 测试环境

| 项 | 值 |
|----|----|
| JDK | 17.0.8 |
| Spring Boot | 3.2.5 |
| Maven | 3.6.1（离线 `-o`） |
| Node | v20.11.0 |
| vitest | 随 frontend/package.json |
| 执行命令 | `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**`；`cd agent-web/frontend && npx vitest run`；`npx tsc --noEmit` |

**隔离**：`WebIntegrationTest` 用 `target/test-data` 系统属性隔离 agent home；本次新增测试均为纯单元测试（mock / 内存），不写真实数据目录。

## 4. 测试策略

| 层 | 手段 | 理由 |
|----|------|------|
| 单元（Java） | JUnit 5 + Mockito | 纯函数 / 钩子逻辑，无 IO |
| 单元（TS） | vitest + @testing-library/react | 组件行为，jsdom 环境 |
| 集成（Java） | `ChatStreamServiceProviderTest` mock `WebAgentRuntime` | 验证参数透传链路而不启真实 AgentLoop 网络 |
| 静态 | `npx tsc --noEmit` | 类型升级的 BREAKING 面 |

**不做**：真实 LLM 调用、真实 provider HTTP 路由验证（v0.2 未实现，见 `provider-catalog.md` §10）。

## 5. 用例矩阵（概览）

详细步骤与预期见 `test-cases.md`。

| 编号段 | 对象 | 用例数 |
|--------|------|-------|
| T-INF-* | `ProviderInference` | 9 |
| T-DS-* | `DeepSeekProvider.validateProviderHook` | 4 |
| T-MM-* | `MiniMaxProvider.validateProviderHook` | 4 |
| T-CS-* | `ChatStreamService` 6 参重载 | 5 |
| T-CC-* | `ChatController` 推断 + 兜底 | 7 |
| T-CLI-* | `SlashCommand` `/model` 路径 | 15 |
| T-FE-CHAT-* | `chat.ts` 类型 + localStorage | 14 |
| T-FE-MS-* | `ModelSelect` 两层菜单 | 11 |
| T-FE-RE-* | `ReasoningEffortSelect` options | 6 |
| **合计** | | **75** |

## 6. 退出标准（DoD）

1. `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**`：单元测试全绿（jacoco 既有 3 个 web 包违规不恶化）
2. `cd agent-web/frontend && npx vitest run`：全绿
3. `npx tsc --noEmit`：错误数 ≤ 基线 7
4. `openspec validate add-provider-catalog-abstract --type change --strict` 通过
5. 新增 75 个用例全部落地且通过

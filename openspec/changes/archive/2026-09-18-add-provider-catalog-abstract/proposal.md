## Why

change `add-models-dropdown-v0` 已落地最简版本:`ModelsResponse.models[]` 平铺列表 + 三档固定 effort。本 change 在其基础上升级为**完整 provider 分层目录**:对齐 DeepSeek Harness `dsh web` 的 `ModelSelection` DTO(`{provider, model, reasoningEffort?}`)+ `ModelReasoningEffort`(`{id, name, description?}`)+ `getSupportedThinkingLevels(model)` 动态等级;前端 ModelSelect 升级为**两层菜单**(provider → model → reasoningEffort)。

后续 v0.2+ 可基于此抽象扩展更多 provider(Anthropic、OpenRouter、本地 Ollama)而无需改前端 UI 组件。

## What Changes

- 后端:引入 `ModelSelection` / `ModelEntry` / `ReasoningEffort` / `ProviderGroup` 四个 DTO(对齐 dsh);`ModelCatalog` 抽象按 provider 分层(每个 provider 列出其 models + 每个 model 的 supported reasoning efforts);`ModelsController` 改返回 `providers[]` 结构(替代 change A 的平铺 `models[]`);`ProviderRequest` 新增 `provider: String` 字段(与 `model` 并列);`AgentLoop.setProvider()` + `setModel()` 拆开,各自 volatile;三 Provider 都接受 `provider + model + reasoningEffort` 三参数;`application-web.yml` 支持 `agent.chat.providers: [{id, name, models: [{id, name, supportsReasoning, reasoningEfforts: [...]}]}]` 结构(替代 change A 的 `supported-models` 简表)。
- 前端:`ModelsResponse` 类型重写为嵌套 `providers[]`;`ModelSelect` 升级为**两层菜单**(trigger 点击 → 第一层面板选 provider → 第二层面板选 model + reasoningEffort 联动);`localStorage` schema 升级为 `ModelSelection`(DSH 兼容 `{provider, model, reasoningEffort}`);`ReasoningEffortSelect` 改为从当前 model 的 `reasoningEfforts` 数组动态化(替代 change A 的硬编码三档)。
- CLI:保留 `/effort` / `/model` 命令;`/model <provider>/<model>` 支持完整路径(替代 change A 的简单别名)。
- **BREAKING**:`/api/chat/models` 响应 schema 变化(从 `{models:[...]}` 变 `{providers:[...]}`,前端必须同步升级);`application-web.yml` 配置 key 变化(`supported-models` → `providers`)。

## Capabilities

### New Capabilities

- `provider-catalog`: 后端 ModelCatalog 抽象 + ProviderGroup/ModelEntry/ReasoningEffort DTO + 三 Provider 透传 provider/model/effort 三参数;前端两层菜单 + 动态化 effort;CLI `/model <provider>/<model>` 完整路径。

### Modified Capabilities

- `web-ui`: 升级 change A 的所有 ADDED Requirement → 嵌套目录版;新增两层菜单 + 动态 effort requirement。
- `cli`: 扩展 `/model` 命令为 `/model <provider>/<model>` 完整路径。

## Impact

- 后端改动:`agent-core/.../provider/ProviderRequest.java`(加 `provider` 字段)、`agent-core/.../core/AgentLoop.java`(拆 `setProvider()` + `setModel()`)、`agent-core/.../provider/openai/OpenAiCompatibleMapper.java`(读 provider)、`agent-core/.../provider/deepseek/DeepSeekProvider.java` + `anthropic/AnthropicProvider.java` + `openai/OpenAiCompatibleProvider.java`(三 Provider 接受 provider 参数)。
- 新建 DTO:`agent-web/.../api/dto/ModelsResponse.java`(重写为 `providers[]`)、`agent-web/.../api/dto/ProviderGroup.java`、`agent-web/.../api/dto/ModelEntry.java`、`agent-web/.../api/dto/ReasoningEffort.java`、`agent-web/.../api/catalog/ModelCatalog.java`(新抽象)。
- 新建 service:`agent-web/.../api/catalog/ProviderCatalogService.java`(从 yaml 读 providers + 构建 ModelCatalog)。
- 前端改动:`agent-web/frontend/src/api/chat.ts`(`ModelsResponse` 重写 + `ProviderGroup` / `ModelEntry` / `ReasoningEffort` 类型 + `send()` 透传 provider)、`agent-web/frontend/src/components/ModelSelect.tsx`(重写为两层菜单)、`agent-web/frontend/src/components/ReasoningEffortSelect.tsx`(从动态数组渲染)、`agent-web/frontend/src/components/ChatPanel.tsx`(state 升级为 ModelSelection)、`localStorage` schema 升级。
- CLI:`agent-core/.../cli/SlashCommand.java`(`/model` 支持 `<provider>/<model>` 路径)。
- 配置:`application-web.yml` `agent.chat.supported-models` → `agent.chat.providers`(结构化为 yaml 嵌套);保留兼容别名(读老 key 时 fallback)。
- 文档:`docs/provider-catalog.md`(新,完整目录架构 + dsh 兼容性 + 三 Provider 适配);`docs/model-and-effort-dropdown.md` 升级引用新版架构。
- 测试:四件套 `docs/test-agent-demo/2026-09-13-add-provider-catalog-abstract/{test-design,test-cases,test-report,test-review}.md`;`docs/test-agent-demo/test-guide.md` §2.11 详情。
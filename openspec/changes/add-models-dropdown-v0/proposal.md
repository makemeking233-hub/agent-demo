## Why

agent-demo Web UI 当前只允许通过 CLI `/model reasoning` 切换 reasoning 模型,但**前端没有下拉框让用户切模型**,而 OpenAI o1/o3 等模型还接受 `reasoning_effort` 参数控制思考强度,后端虽然自动注入默认值 `medium`,但用户无法配置。本 change 让用户在前端 UI 下拉选择模型,可选 `low / medium / high` 思考强度,跨会话持久化,对齐 DeepSeek Harness `dsh web` 的 ModelSelect 体验(简化版,不引入完整 provider 分层 catalog)。

## What Changes

- 后端:`SendRequest` 新增 `reasoning_effort` 字段;`AgentLoop.setReasoningEffort()` + volatile 字段;`ProviderRequest` 透传 `reasoningEffort`;`OpenAiCompatibleMapper` 原硬编码 `medium` 改为读取 `req.reasoningEffort()`(向后兼容 `null` = `medium`);`DeepSeekProvider` / `AnthropicProvider` 接受 `ProviderRequest.reasoningEffort`(Anthropic 折算 `thinking.budget_tokens`);`ModelsResponse.Model` 新增 `reasoningEfforts: ["low","medium","high"]` 字段(`supportsReasoning=false` 时为空数组)。
- 前端:新增通用 `Dropdown.tsx` 组件(`lucide-react` `ChevronDown` + 弹出列表 + 键盘 ↑↓ Enter Esc);新增 `ModelSelect.tsx`(挂在 TopBar 右上角,展示当前 model 名);新增 `ReasoningEffortSelect.tsx`(挂在 Composer 状态栏,仅 `supportsReasoning=true` 时启用);`localStorage` 持久化 `{model, reasoningEffort}`(新会话沿用上次值);`ChatApi.send()` 透传 `reasoning_effort`。
- CLI:保留旧 `/model reasoning` / `/model chat` 别名;新增 `/effort <low|medium|high>` 命令。

后续 `add-provider-catalog-abstract` change 会把 ModelsResponse 升级为完整 provider 分层目录 + 按 provider 动态 effort 等级,本 change 仅做最小可行版本。

## Capabilities

### New Capabilities

<!-- 无独立新 capability;本 change 的新增能力全部落在 web-ui / cli 已有 capability 的 ADDED Requirement 下 -->

### Modified Capabilities

- `web-ui`: 在 `openspec/specs/web-ui/spec.md` 追加新 Requirement:`前端 ModelSelect 组件` + `前端 ReasoningEffortSelect 组件` + `前端 localStorage 模型持久化` + `后端 SendRequest.reasoning_effort 透传` + `后端 ModelsResponse.Model.reasoningEfforts` + `后端 ProviderRequest.reasoningEffort 透传` + `CLI /effort slash 命令`。
- `cli`: 在 `openspec/specs/cli/spec.md` 追加新 Requirement:`/effort <level> slash 命令`。

## Impact

- 后端改动:`agent-core/.../core/AgentLoop.java`(加 `setReasoningEffort()`)、`agent-core/.../provider/openai/OpenAiCompatibleMapper.java`(原硬编码 medium 改读 req)、`agent-core/.../provider/deepseek/DeepSeekProvider.java` + `anthropic/AnthropicProvider.java`(接受 `reasoningEffort`)、`agent-web/.../api/dto/SendRequest.java`(加 `reasoningEffort` 字段)、`agent-web/.../api/dto/ModelsResponse.java`(加 `reasoningEfforts` 字段)、`agent-web/.../api/ChatController.java`(透传 `reasoningEffort`)、`agent-web/.../stream/WebAgentRuntime.java`(透传)、`agent-web/.../api/ModelsController.java`(返回 `reasoningEfforts`)、`agent-core/.../cli/SlashCommand.java`(加 `/effort` 命令)。
- 前端改动:`agent-web/frontend/src/api/chat.ts`(加 `listModels()` + `send()` 透传 `reasoning_effort`)、`agent-web/frontend/src/components/Dropdown.tsx`(新通用组件)、`agent-web/frontend/src/components/ModelSelect.tsx`(新)、`agent-web/frontend/src/components/ReasoningEffortSelect.tsx`(新)、`agent-web/frontend/src/components/TopBar.tsx`(集成 `ModelSelect`)、`agent-web/frontend/src/components/Composer.tsx`(集成 `ReasoningEffortSelect`)、`agent-web/frontend/src/components/ChatPanel.tsx`(持久化 + 新会话默认)。
- 文档:`docs/model-and-effort-dropdown.md`(新);`docs/test-agent-demo/2026-09-13-add-models-dropdown-v0/{test-design,test-cases,test-report,test-review}.md`(四件套);`docs/test-agent-demo/test-guide.md`(登记 + §2.10 详情)。
- 配置:`application-web.yml` 默认 supported-models 不变(`deepseek-chat` + `deepseek-reasoner`),`reasoningEfforts` 数组对两个 provider 默认填 `["low","medium","high"]`(`deepseek-chat` 时为 `[]`)。
- 不引入新依赖;`lucide-react` 已存在。
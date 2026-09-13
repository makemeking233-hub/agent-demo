## Why

agent-demo 当前 LLM 流式输出仅暴露 `text_delta` 事件，模型推理过程对用户完全不可见。当用户使用 `deepseek-reasoner` / `OpenAI o1` / `Anthropic Claude with extended thinking` 这类"先思考再回答"模型时，无法看到模型为什么这样回答、走了哪些推理路径，UX 与"元宝 / ChatGPT"等成熟产品差距明显。本次变更把"思考过程"作为独立的 SSE 事件流推送到前端，前端以可折叠区块实时展示，让用户看到 AI 推理的"内心独白"。

## What Changes

- **后端 `StreamChunk` 新增 `ThinkingDelta` record + `StreamChunkVisitor.visitThinkingDelta()` 默认空实现**：把"思考增量"作为 sealed 类型的第 8 种，与 `TextDelta` 并列。
- **后端 `Message.Assistant` 新增 `reasoningTokens` 字段 + `thinking` List 已存在骨架** 填充实际内容。
- **后端 `DeepSeekProvider` 解析 `choices[].delta.reasoning_content` 字段**（OpenAI 兼容协议扩展）：`deepseek-reasoner` 模型返回时该字段非空。
- **后端 `OpenAiCompatibleProvider` 适配 o1 / o3 reasoning**：通过 `reasoning_effort` 参数 + `usage.completion_tokens_details.reasoning_tokens` 字段。
- **后端 `AnthropicProvider` 解析 `thinking` content blocks**：Anthropic 协议中 thinking 是独立 content block type，与 text 块并行。
- **后端 `AgentLoop` 累积 thinking 增量**：每收到一个 `ThinkingDelta` 追加到本地 list，并在 turn 收尾时合并到 `Message.Assistant.thinking`。
- **后端 `ChatStreamService` 把 `ThinkingDelta` 转 SSE `message_delta` 事件（`delta_type: "thinking"`）**：与 `TextDelta` 同频逐 token 推送。
- **后端 `SseEvent` 新增 `delta_type: "thinking"` 适配**：前端识别字段。
- **后端 `Usage` 新增 `reasoningTokens` 字段**：thinking token 单独计费。
- **后端 `ContextCompressor` 增 thinking-aware 压缩**：保留最近 2 轮 thinking 完整 + 早期 thinking 压缩为一句摘要。
- **后端 `SessionLogSink` 落 thinking.log**：v0.1 已有 thinking.log 骨架，v0.2 填充实际内容；走 Redactor 脱敏（与 text 一致）。
- **前端 `useChatStream` 解析 `delta_type: "thinking"`**：累积到当前 assistant 消息的 `thinking` 字段，独立于 `text` 字段。
- **前端 `MessageBubble` 渲染 thinking 为可折叠区块**：默认折叠 + 标题"思考过程 (N token)" + 超 2000 token 显示"查看更多"按钮 + 跨 turn 合并连续区块。
- **前端 `Composer` 支持 `/model reasoning` slash 命令**：切换到 deepseek-reasoner（后续 v0.3 支持更多模型）。
- **后端 `/api/chat/models` 端点**：返回支持的模型列表（deepseek-chat / deepseek-reasoner）。
- **前端 `ModelsDropdown` 组件**：让用户选择当前会话的模型。
- **abort 处理**：用户点"停止"时，思考 + 文本都停止，已推送的 thinking 标记"已中断"。

无破坏性变更（BREAKING）：现有 `text_delta` 事件契约不变；老 `Message.Assistant.thinking` 列表契约不变（v0.1 始终为空，现在可能非空）。

## Capabilities

### New Capabilities

无（与 web-ui 强相关，归入既有 capability）。

### Modified Capabilities

- `web-ui`：在现有 spec 追加 8 个新 Requirement，覆盖「StreamChunk.ThinkingDelta 类型」「三 provider reasoning 字段解析」「SSE message_delta delta_type=thinking」「thinking 单独计费」「thinking history 压缩」「前端可折叠渲染」「/model slash 命令」「abort 同时停思考 + 文本」。

## Impact

- **后端（修改 ~10 个文件）**：
  - `llm/StreamChunk.java` 新增 `ThinkingDelta` record
  - `core/Message.java` Assistant 加 `reasoningTokens` 字段
  - `llm/DeepSeekProvider.java` 解析 `reasoning_content`
  - `llm/OpenAiCompatibleProvider.java` 解析 `completion_tokens_details.reasoning_tokens`
  - `llm/AnthropicProvider.java` 解析 `thinking` blocks
  - `core/AgentLoop.java` 累积 thinking + 写 `Message.Assistant.thinking`
  - `core/ContextCompressor.java` thinking-aware 压缩
  - `stream/ChatStreamService.java` 透传 ThinkingDelta → SSE
  - `api/dto/SseEvent.java` 加 `delta_type: "thinking"` 支持
  - `log/SessionLogSink.java` + `log/SessionLogger.java` 落 thinking.log
  - `api/ModelsController.java` 新增 `/api/chat/models`
  - `cli/SlashCommand.java` 增 `/model reasoning` 命令
  - 新增 `core/Redactor.java` 已存在，确认 thinking 也走

- **前端（修改 ~5 个文件）**：
  - `api/chat.ts` types 加 `Message.thinking` / `delta_type: "thinking"` 适配
  - `hooks/useChatStream.ts` 解析 thinking delta
  - `components/MessageBubble.tsx` 渲染可折叠 thinking
  - `components/ThinkingCollapse.tsx` 新增折叠组件
  - `components/Composer.tsx` `/model` slash 补全
  - `components/ModelsDropdown.tsx` 新增模型选择
  - `lib/slashCommands.ts` 增 `/model reasoning`

- **测试**：
  - 后端：`StreamChunkThinkingDeltaTest` / `DeepSeekProviderReasoningTest` / `OpenAiReasoningTest` / `AnthropicThinkingTest` / `AgentLoopThinkingTest` / `ContextCompressorThinkingTest` / `ChatStreamServiceThinkingTest`
  - 前端：`useChatStream.thinking.test.ts` / `MessageBubble.thinking.test.tsx` / `ThinkingCollapse.test.tsx`
  - 端到端：`ChatPanel.reasoning.e2e.spec.ts`（Playwright）

- **依赖**：无新增（沿用现有 JUnit 5 / vitest / Playwright）。
- **配置**：`application-web.yml` 增 `agent.chat.supported-models: [deepseek-chat, deepseek-reasoner]` 列表 + `agent.chat.default-model: deepseek-chat`。
- **文档**：`docs/reasoning-thinking.md` 新增架构 + 缓存策略 + provider 适配表。
- **不引入**：不引入新的 LLM 协议抽象层（各 provider 独立解析）；不引入 thinking 缓存（用户每次新会话都重推）。
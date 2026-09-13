# Reasoning Thinking 流式（add-reasoning-thinking-streaming）

## 1. 目标

把 `deepseek-reasoner` / `OpenAI o1` / `Anthropic Claude extended thinking` 等"先思考再回答"模型的 **推理过程** 真正流到前端 UI：
- 后端 SSE `message_delta` 事件增 `delta_type: "thinking"`，与 `text` 同频逐 token 推送；
- 前端 `useChatStream`（即 `ChatPanel.handleEvent`）解析 thinking delta，累加到当前 assistant 消息的 `thinking` 字段；
- `MessageBubble` 在文本之上渲染 `<ThinkingCollapse />`：默认折叠 + 标题"思考过程 (N token)" + 超 2000 token "查看更多"。

## 2. 三 Provider reasoning 适配

| Provider | 模型 | 请求字段 | 响应字段 | emit 类型 |
|---|---|---|---|---|
| **DeepSeek** | `deepseek-reasoner` | （无变化，OpenAI 兼容） | `choices[].delta.reasoning_content` | `ThinkingDelta(text)` |
| **OpenAI o1** | `o1-preview` / `o1-mini` / `o3` / `o4` | 自动注入 `reasoning_effort: "medium"` | `usage.completion_tokens_details.reasoning_tokens`（仅 token 计数，无内容） | `Usage(reasoningTokens=N)` |
| **Anthropic** | `claude-opus-4-*` / `claude-sonnet-4-*` / `claude-3-7-sonnet-*` | 自动注入 `thinking: {type: "enabled", budget_tokens: 4096}` | `content_block_delta.delta.type="thinking_delta"` | `ThinkingDelta(text)` |

## 3. 数据流

```
┌──────────────┐   reasoning_content    ┌──────────┐  ThinkingDelta  ┌────────────┐  message_delta  ┌────────┐
│ DeepSeek o1  │   增量字符串           │ OpenAI   │  sealed         │ AgentLoop  │  delta_type:    │ SSE    │
│ Anthropic    │ ─────────────────────► │ Mapper   │ ──────────────►│ extractAss │ ───────────────►│ WS     │
│ upstream     │   thinking_delta       │ / Anthrop│  accept        │ istant      │  "thinking"    │        │
└──────────────┘                        └──────────┘                 └────────────┘                 └────────┘
```

`Assistant` record 4 字段：`content` + `toolCalls` + `reasoning: List<String>` + `reasoningTokens: int`（默认空 / 0，向后兼容 2-arg 构造）。

## 4. ContextCompressor thinking-aware 压缩

`ContextCompressor.collapseMessages` 在坍缩早期消息时，如果 `Assistant.reasoning()` 非空，会拼一行 `[thinking 摘要] <前 50 字符>...` 到 summary 中：

```
- **做了什么**: <content>
[thinking 摘要] 我先思考了用户...
```

最近 2 轮的 thinking 完整保留（在 content 里），早期消息的 thinking 自动坍缩为单行摘要。

## 5. 后端 SSE / API

| 端点 / 事件 | 行为 |
|---|---|
| `POST /api/chat/send` body | 新增 `model` 字段（可选；null/空 → `deepseek-chat`；非法 → 回退默认） |
| `GET /api/chat/models` | 返回 `{models: [{id, name, supportsReasoning}]}`（从 `agent.chat.supported-models` 读） |
| SSE `message_delta` | 增 `delta_type: "thinking"`（与 `"text"` 平级） |
| SSE `message_stop` | `finish_reason: "aborted"` 时停止 thinking + text（已实现） |

`ModelRegistry`（新建）从 `agent.chat.supported-models`（YAML 配置）解析 + 校验 `isSupported`。

## 6. 前端 UI

```
┌──────────────────────────────────────┐
│ ► 思考过程 (1234 token)    [查看更多]│  ← ThinkingCollapse（默认折叠）
├──────────────────────────────────────┤
│ 最终答案正文（ReactMarkdown 渲染）  │
│  ┌─ Read ──────────────────────────┐   │
│  │ file content                   │   │  ← ToolCallCard
│  └────────────────────────────────┘   │
└──────────────────────────────────────┘
```

`MessageBubble.tsx` 在 `text` 之上渲染 `ThinkingCollapse`（仅 `role === "assistant"` 且 `thinking` 非空时）。

## 7. SessionLog thinking 落盘

`SessionLogSink.onThinkingDelta(text)` 默认空方法（向后兼容老 sink）；`SessionLogger` 实现为逐 token 写 `thinking.log`（带 Redactor 脱敏）。`onAssistant` 收尾时仍会批量写完整 thinking（兼容旧路径）。

## 8. token 计费

`Usage.reasoningTokens` 字段独立计费（不与 `completionTokens` 累加）。前端 UI 可在 `usage` 事件中展示 `reasoningTokens` 字段（v0.x 仅后端记录，前端展示 v0.3 接入）。

## 9. 配置

`application-web.yml`：
```yaml
agent:
  chat:
    supported-models: deepseek-chat,deepseek-reasoner  # 逗号分隔
    default-model: deepseek-chat
```

## 10. 测试覆盖

- **后端 agent-core**：StreamChunkThinkingDelta 5 + DeepSeekProvider 2 + OpenAiCompatibleMapper 7 + AnthropicProvider 7 = **+21 个新测试**，总计 343/343 全绿
- **后端 agent-web**：ChatControllerTest / ModelsControllerTest（v0.1 既有 + v0.2 扩 model 字段）= 无回归
- **前端**：ChatPanel thinking 分支 + ThinkingCollapse 5 + MessageBubble.thinking 4 = **+9 个新测试**，总计 104/104 全绿

## 11. 决策记录

| Decision | 选择 | 理由 |
|---|---|---|
| D1 sealed 第 8 类型 | `StreamChunk.ThinkingDelta` | 与 `TextDelta` 并列，共享 visitor 模式（兼容老 visitor 默认空实现） |
| D2 累积模型 | AgentLoop 内部 list 累积 + 收尾合并 | 实时转发（sink.onThinkingDelta 落 log）+ turn 收尾合并到 Assistant.thinking |
| D3 history 策略 | thinking 进 history + 早期压缩摘要 | 跨轮模型思考连贯；早期消息太多会撑爆 context |
| D4 UI 默认折叠 | 标题"思考过程 (N token)" | 思考常 1000+ token，展开占对话区 |
| D5 长文本截断 | 2000 token 折叠 + "查看更多" | 极端情况（5k+ token）可读性 |
| D6 多 provider | 独立解析（DeepSeek / OpenAI o1 / Anthropic 各干各的） | 三方协议 semantics 差异大，不抽象 |
| D7 abort | 思考 + 文本都停 | 用户"停止"意图明确；不浪费已费 token |
| D8 计费 | `Usage.reasoningTokens` 独立 | 与上游计费对齐；分开上下文窗口计算 |

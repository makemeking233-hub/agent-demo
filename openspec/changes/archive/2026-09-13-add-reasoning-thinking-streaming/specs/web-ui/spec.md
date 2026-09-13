## ADDED Requirements

### Requirement: StreamChunk.ThinkingDelta 类型

`StreamChunk` sealed interface SHALL 新增 `ThinkingDelta(String text)` record 作为第 8 种 permit 类型；`StreamChunkVisitor` SHALL 新增 `visitThinkingDelta(ThinkingDelta)` 默认空方法（向后兼容旧 visitor）。

#### Scenario: ThinkingDelta 触发 Visitor

- **WHEN** `AgentLoop` 收到上游 LLM 的 reasoning 增量
- **THEN** 产出 `StreamChunk.ThinkingDelta` 事件
- **AND** 调 `visitor.visitThinkingDelta(this)` 派发

#### Scenario: 老 Visitor 编译通过

- **WHEN** 旧版本 `StreamChunkVisitor` 实现未覆盖 `visitThinkingDelta`
- **THEN** 该方法调默认空实现，不抛 `AbstractMethodError`

### Requirement: DeepSeek reasoning_content 解析

`DeepSeekProvider` SHALL 解析 OpenAI 兼容响应中 `choices[].delta.reasoning_content` 字段，当 `model="deepseek-reasoner"` 且该字段非空时产出 `StreamChunk.ThinkingDelta`。

#### Scenario: deepseek-reasoner 正常 reasoning 流

- **WHEN** Provider 收到 `{"choices":[{"delta":{"reasoning_content":"用户问..."}}]}` 增量
- **THEN** 产出 `ThinkingDelta("用户问...")`
- **AND** 不产出 `TextDelta`（text 为 null）

#### Scenario: deepseek-chat 不触发 reasoning

- **WHEN** Provider 收到 `model="deepseek-chat"` 响应（无 reasoning_content 字段）
- **THEN** 不产出 `ThinkingDelta`（即便上游误返 reasoning_content 也忽略）

#### Scenario: OpenAI o1 reasoning_tokens 统计

- **WHEN** Provider 收到 `usage.completion_tokens_details.reasoning_tokens=1500`
- **THEN** `Usage.reasoningTokens=1500`
- **AND** 不产出 `ThinkingDelta`（OpenAI o1 不暴露内容）

### Requirement: Anthropic thinking blocks 解析

`AnthropicProvider` SHALL 解析响应中 `content[].type="thinking"` 块，提取 `text` 字段产出 `ThinkingDelta`。

#### Scenario: Anthropic single thinking block

- **WHEN** Provider 收到 `{"content":[{"type":"thinking","text":"我需要先..."}]}`
- **THEN** 产出单个 `ThinkingDelta("我需要先...")`

#### Scenario: Anthropic text + thinking 混合

- **WHEN** Provider 收到 `{"content":[{"type":"thinking","text":"思考"},{"type":"text","text":"答案"}]}`
- **THEN** 先 `ThinkingDelta("思考")` 后 `TextDelta("答案")`（按 content 数组顺序）

### Requirement: AgentLoop thinking 累积与 history 合并

`AgentLoop` SHALL 累积当前 turn 内的 `ThinkingDelta` 到本地 list，turn 收尾时合并到 `Message.Assistant.thinking`；同时通过 `SessionLogSink.onThinkingDelta` 转发给 ChatStreamService。

#### Scenario: turn 内多 ThinkingDelta 累积

- **WHEN** 一个 turn 收到 N 个 ThinkingDelta
- **THEN** 本地 list 长度 = N
- **AND** 每个 delta 立即触发 `sink.onThinkingDelta(delta)`（实时转发）
- **AND** turn 收尾时 `Message.Assistant.thinking = List.copyOf(list)`

#### Scenario: turn 间清空

- **WHEN** turn 收尾（final `Finished` 事件）
- **THEN** 本地 thinking list 清空
- **AND** 下个 turn 重新累积

### Requirement: ContextCompressor thinking-aware 压缩

`ContextCompressor` SHALL 在压缩历史时，对 `Message.Assistant.thinking` 列表执行 thinking-aware 压缩：保留最近 2 轮 thinking 完整 + 早期 thinking 压缩为单行摘要。

#### Scenario: 最近 2 轮保留

- **WHEN** 历史有 3 轮对话，每轮 thinking 200 token
- **THEN** 前 1 轮 thinking 压缩为摘要
- **AND** 后 2 轮 thinking 完整保留

#### Scenario: 早期 thinking 摘要模板

- **WHEN** thinking 文本被压缩
- **THEN** 摘要格式为 `"[第 N 轮思考摘要] <前 50 字符>..."`
- **AND** 原始完整文本不丢失（在 session.jsonl 中保留）

### Requirement: SSE message_delta delta_type=thinking 推送

`ChatStreamService` SHALL 将 `StreamChunk.ThinkingDelta` 转为 SSE `message_delta` 事件，`delta_type="thinking"` 字段；与 `delta_type="text"` 同频逐 token 推送。

#### Scenario: ThinkingDelta → SSE 事件

- **WHEN** `ChatStreamService` 收到 `ThinkingDelta("我先")`
- **THEN** 推送 `data: {"type":"message_delta","delta_type":"thinking","content":"我先"}`

#### Scenario: thinking 与 text 交替推送

- **WHEN** Provider 同时产出 ThinkingDelta + TextDelta
- **THEN** SSE 事件按到达顺序推（先 thinking 后 text 或反过来，取决于 Provider 实际顺序）

#### Scenario: 客户端按 delta_type 路由

- **WHEN** 前端收到 `delta_type="thinking"` 事件
- **THEN** 累积到当前消息的 `thinking` 字段（独立于 `text` 字段）

### Requirement: thinking 单独计费

`StreamChunk.Usage` SHALL 新增 `reasoningTokens` 字段；DeepSeek / OpenAI / Anthropic 三 Provider SHALL 各自解析 reasoning 相关 token 计数并填充。

#### Scenario: DeepSeek reasoning token

- **WHEN** DeepSeek 响应 `usage.completion_tokens=2000, reasoning_tokens=800`
- **THEN** `Usage(promptTokens, completionTokens=2000, reasoningTokens=800)`

#### Scenario: OpenAI o1 reasoning_tokens

- **WHEN** OpenAI 响应 `usage.completion_tokens_details.reasoning_tokens=1500`
- **THEN** `Usage.completionTokens` 含总 token，`Usage.reasoningTokens=1500`

#### Scenario: thinking 不计入 completion_tokens

- **WHEN** Provider 解析 reasoning_tokens
- **THEN** 同一响应中 `completionTokens` 不重复累加 reasoning_tokens

### Requirement: SessionLogSink thinking 落盘 + 脱敏

`SessionLogSink.onThinkingDelta(delta)` SHALL 接收逐 token 增量，转发到 `SessionLogger` 写入 `thinking.log`；走 `Redactor` 脱敏（与 text 一致）。

#### Scenario: thinking 写入 thinking.log

- **WHEN** `onThinkingDelta` 被调
- **THEN** 该 delta 追加到 `thinking.log`（每行带时间戳 + "thinking> " 前缀）

#### Scenario: thinking 走 Redactor 脱敏

- **WHEN** thinking delta 包含 `sk-xxx` 形式的 API key
- **THEN** 落盘内容中 key 被替换为 `***REDACTED***`

### Requirement: 前端 MessageBubble thinking 可折叠渲染

前端 `MessageBubble` SHALL 在 assistant 消息体上方渲染 `ThinkingCollapse` 组件，标题"思考过程 (N token)"，默认折叠；超 2000 token 显示"查看更多"按钮。

#### Scenario: 短 thinking 完整展示

- **WHEN** `thinking.length <= 2000`
- **THEN** 折叠标题"思考过程 (N token)" + 折叠内容完整

#### Scenario: 长 thinking 截断

- **WHEN** `thinking.length > 2000`
- **THEN** 默认显示前 2000 字符 + "..." + "查看更多"按钮
- **AND** 点击"查看更多"展开完整内容

#### Scenario: 跨 turn thinking 合并

- **WHEN** 当前消息 thinking 含多 turn 增量（按 turnIndex 分组）
- **THEN** 合并为单个连续区块显示（不按 turn 分割）
- **AND** token 计数为所有 turn 之和

### Requirement: /model reasoning slash 命令

`SlashCommand` SHALL 支持 `/model reasoning`（切到 `deepseek-reasoner`）与 `/model chat`（切回 `deepseek-chat`），命令结果通过 `message_delta` (delta_type: "text") 推送切换成功提示。

#### Scenario: /model reasoning 切到 deepseek-reasoner

- **WHEN** 用户提交 `/model reasoning`
- **THEN** 当前会话的 `AgentLoop.model` 改为 `deepseek-reasoner`
- **AND** 推送 `message_delta: "已切换到 deepseek-reasoner"`

#### Scenario: /model chat 切回 deepseek-chat

- **WHEN** 用户提交 `/model chat`
- **THEN** 当前会话的 `AgentLoop.model` 改为 `deepseek-chat`
- **AND** 推送 `message_delta: "已切换到 deepseek-chat"`

#### Scenario: 未知模型

- **WHEN** 用户提交 `/model gpt-4-turbo`（不在 supported-models 列表）
- **THEN** 推送 `message_delta: "未知模型 gpt-4-turbo"`（不切换）

### Requirement: /api/chat/models 端点

后端 SHALL 新增 `GET /api/chat/models` 端点，返回 `{"models": [{"id":"deepseek-chat","name":"DeepSeek Chat","supportsReasoning":false}, {"id":"deepseek-reasoner","name":"DeepSeek Reasoner","supportsReasoning":true}]}`。

#### Scenario: 列出 supported-models

- **WHEN** 客户端发 `GET /api/chat/models`
- **THEN** 返回当前 `agent.chat.supported-models` 配置的所有模型
- **AND** 每项含 `id` / `name` / `supportsReasoning` 三个字段

#### Scenario: trusted-host 鉴权

- **WHEN** 客户端源 IP 不在 trusted-hosts 白名单
- **THEN** 返回 `403 host_not_trusted`

### Requirement: abort 同时停 thinking + text

当用户在前端点击"停止生成"调 `POST /api/chat/abort/{stream_id}` 时，AgentLoop SHALL 通过 `AbortSignal` 通知所有 Provider 停止生成；thinking 与 text 都停。

#### Scenario: 中断时 thinking 部分保留

- **WHEN** 用户在 thinking 推到 1500 token 时 abort
- **THEN** 文本流停止
- **AND** 已推送的 1500 token thinking 保留为部分状态
- **AND** message_stop event 标记 `finish_reason: "aborted"`

#### Scenario: 中断后 history 完整

- **WHEN** turn 因 abort 终止
- **THEN** 当前 turn 的 `Message.Assistant` 仍写 history（含部分 text + 部分 thinking）
- **AND** 下轮发送时带上 abort 标记的 message（让模型知道上次被中断）
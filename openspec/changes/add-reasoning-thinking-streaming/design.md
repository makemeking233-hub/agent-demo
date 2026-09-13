## Context

agent-demo v0.1 已实现 LLM 流式 SSE 推送（`Sinks.Many<ServerSentEvent>` + `message_delta` 7 类事件），但 `StreamChunk` sealed interface 不含 `ThinkingDelta` 类型，`Message.Assistant.thinking` 列表虽预留但 v0.1 始终为空（`SessionLogSink.onAssistant(assistant, List<String> thinking)` 注释："v0.1 恒为空；v0.2 deepseek-reasoner 接入后非空"）。

本次 change 实际工作 = **让 thinking 真正流通**：上游 provider 解析 → StreamChunk 新增类型 → AgentLoop 累积 → SSE 推流 → 前端可折叠渲染。三 provider 适配（DeepSeek / OpenAI o1 / Anthropic extended thinking）覆盖主流 reasoning model。

约束：v0.1 已有 `text_delta` / `tool_call_*` / `usage` / `finished` / `error` / `permission_request` 7 类事件契约不变，thinking 作为第 8 类增量事件并行推送。

## Goals / Non-Goals

**Goals：**

- `StreamChunk` 新增 `ThinkingDelta` record（sealed 第 8 种），与 `TextDelta` 共享 Visitor 模式。
- 三 provider 解析 reasoning 字段：DeepSeek `reasoning_content` / OpenAI `completion_tokens_details.reasoning_tokens` / Anthropic `thinking` content blocks。
- AgentLoop 累积 thinking 增量到 `Message.Assistant.thinking`，同时通过 `SessionLogSink` 落 `thinking.log`（走 Redactor 脱敏）。
- ChatStreamService 透传 `ThinkingDelta` → SSE `message_delta` (delta_type: "thinking")，逐 token 推（与文本同频）。
- `Usage` 增 `reasoningTokens` 字段，thinking token 单独计费。
- `ContextCompressor` 增 thinking-aware 压缩：保留最近 2 轮 thinking 完整 + 早期 thinking 压缩为一句摘要。
- 前端 `useChatStream` 解析 thinking delta，累积到当前 assistant 消息的 `thinking` 字段。
- 前端 `MessageBubble` 渲染 thinking 为可折叠区块：默认折叠 + 标题"思考过程 (N token)" + 超 2000 token 显示"查看更多"按钮。
- 前端 `/model reasoning` slash 命令 + `ModelsDropdown` 组件。
- abort 同时停思考 + 文本（已推送的 thinking 标记"已中断"）。

**Non-Goals：**

- 不引入新的 LLM 协议抽象层（三个 provider 独立解析，不强行统一）。
- 不缓存 thinking（用户每次新会话重推）。
- 不做 thinking 内容审查（仅 Redactor 脱敏，与 text 一致）。
- 不做 thinking 全文搜索（v0.x 不做）。
- 不做"thinking 摘要"AI 二次调用（直接用"摘要"占位文本压缩）。

## Decisions

### D1：`StreamChunk` 新增 `ThinkingDelta` record，与 `TextDelta` 并列

**理由**：现有 `StreamChunk` 是 sealed interface with permits 7 types，加 `ThinkingDelta` 是最小变更。共享 `accept(StreamChunkVisitor)` 模式，所有现有 visitor 自动收到"无变化"的默认空实现（兼容旧 visitor）。

**实现**：
```java
public sealed interface StreamChunk
    permits ..., StreamChunk.ThinkingDelta {
  // ...
  record ThinkingDelta(String text) implements StreamChunk {
    @Override public void accept(StreamChunkVisitor v) { v.visitThinkingDelta(this); }
  }
  
  interface StreamChunkVisitor {
    // ...
    default void visitThinkingDelta(ThinkingDelta c) {}  // 旧 visitor 编译通过
  }
}
```

**考虑过**：用 `record ThinkingChunk(List<String> chunks, FinishReason reason)` 批量推送。否决：与现有"每 chunk 一个事件"的模式不一致；前端处理"分块到达"更实时。

### D2：thinking 累积按"逐 ThinkingDelta 增量 + turn 收尾时合并"两阶段

**理由**：模型产出的每个 `ThinkingDelta` 立即推送给前端（实时性），同时在 AgentLoop 内部累积到 `Message.Assistant.thinking` list。turn 收尾时该 list 已是完整思考文本，直接合并到 final message。

**实现**：
```java
// AgentLoop.processTurn 内
private final List<String> currentThinking = new ArrayList<>();
// 每收到 ThinkingDelta：
currentThinking.add(delta.text());
// 也调 sink.onThinkingDelta(delta) 转发（让 ChatStreamService 推 SSE）
// turn 收尾时：
Message.Assistant assistant = new Message.Assistant(text, currentThinking, toolCalls);
history.append(assistant);
currentThinking.clear();
```

**考虑过**：直接累积到 history 每次都 append，turn 收尾时合并。否决：history append 在收到第一个 token 就触发，turn 中途 history 不一致。

### D3：thinking 进 history，但 ContextCompressor 压缩

**理由**：用户决策 #8。模型看到自己的思考能保持多轮对话连贯（"上次我考虑过 X 这次再展开"）。但 thinking 文本通常很长，每轮都进 history 会快速烧光 context window。ContextCompressor 介入：保留最近 2 轮 thinking 完整 + 早期 thinking 压缩为"[X 轮前思考：用户问 X，我决定 Y]"一句话摘要。

**实现**：
```java
// ContextCompressor.compress
if (msg.thinking() != null && !msg.thinking().isEmpty()) {
  String compressed = (i >= recentCount) 
    ? summarize(msg.thinking())  // AI 二次摘要 or 模板
    : String.join("\n", msg.thinking());
  msg = msg.withThinking(List.of(compressed));
}
```

**考虑过**：thinking 不进 history（节省 token）。否决：模型跨轮思考会丢失连贯性。

### D4：前端 thinking 渲染跨 turn 合并连续区块

**理由**：用户决策 #9。模型推理过程常跨多个 tool_call：
```
思考 v1（决定要查 weather）→ tool_call(weather) → 思考 v2（查到 rain）→ tool_call(umbrella) → 思考 v3（建议带伞）→ 最终答案
```
前端如果"每个 turn 单独一块"，UX 中断感；合并连续区块像"实时日记"沉浸感强。

**实现**：
```tsx
// MessageBubble 接收 thinking: Array<{ turnIndex: number, text: string, tokens: number }>
// 同一 turnIndex 合并；不同 turnIndex 顺序排列但视觉连续
<ThinkingCollapse 
  blocks={thinking.map(t => t.text).join('\n')} 
  tokenCount={thinking.reduce((s, t) => s + count(t.text), 0)}
  collapsible
/>
```

**考虑过**：按 turn 单独折叠 + 序号标注。否决：实现简单但 UX 弱。

### D5：SSE 事件 schema 扩展（向后兼容）

**理由**：现有 `message_delta` 事件 `delta_type` 字段是 enum-like 字符串（`text` / `thinking`）。前 v0.1 spec 已定义 `thinking` enum value（line 82-83）但从未触发。现在让 spec 落地。

**实现**：
```java
// SseEvent.messageDelta
public record messageDelta(String type, String deltaType, String content) { }
// type="message_delta", deltaType="text"|"thinking"
```

**考虑过**：新增 `thinking_delta` 事件类型（与 `message_delta` 平级）。否决：增加事件类型数量，前端 listener 分支变多；用 `delta_type` 区分更扁平。

### D6：Provider 适配采用"独立解析，不抽象"

**理由**：三个 provider 的 reasoning 字段语义/位置都不同：
- DeepSeek：`choices[].delta.reasoning_content`（增量字符串）
- OpenAI o1：`usage.completion_tokens_details.reasoning_tokens`（仅 token 数，不暴露内容）+ `reasoning_effort` 参数
- Anthropic：`content[].type="thinking"` 块（结构化对象，不是字符串增量）

强行统一抽象（如定义 `ReasoningChunk` 接口）会复杂化各 provider 实现。保持各 provider 独立解析 + 统一输出 `ThinkingDelta(String text)` 到 StreamChunk。

**实现**：
- `DeepSeekProvider`：从 `delta.reasoning_content` 读 → emit `ThinkingDelta(text)`
- `OpenAiCompatibleProvider`：从 `usage.completion_tokens_details.reasoning_tokens` 读 token 数 → 仅在 `Usage.reasoningTokens` 写，不 emit `ThinkingDelta`（OpenAI o1 不暴露内容）
- `AnthropicProvider`：从 `content[]` 读 `type="thinking"` 块 → emit `ThinkingDelta(text)`（但 Anthropic 的 thinking block 是"完整块"而不是 delta，可能整个 turn 一个 chunk）

**考虑过**：抽象 `ReasoningProvider` 接口。否决：v0.x 三个 provider 适配足够，v0.2+ 才考虑抽象。

### D7：thinking 单独计费（`Usage.reasoningTokens`）

**理由**：DeepSeek / OpenAI / Anthropic 官方都把 reasoning token 单独计费（与 completion token 区分）。如果合并到 completion_tokens 会导致：
- 用户看到 usage 与计费对不上
- 上下文窗口计算不准（reasoning token 也算窗口）

**实现**：
```java
// Usage record
public record Usage(int promptTokens, int completionTokens, int reasoningTokens) {}
// 解析各 provider 的 reasoning_tokens 字段填入
```

**考虑过**：合并到 completion_tokens。否决：与上游计费对不齐。

### D8：前端 thinking 默认折叠 + 标题显示 token 数

**理由**：用户决策 #3。thinking 经常 1000+ token，全展开占满对话区，干扰用户看最终答案。默认折叠 + 标题"思考过程 (N token)"让用户知道有思考但不影响主流程。

**实现**：
```tsx
<details className={styles.thinking} open={false}>
  <summary>思考过程 ({tokens} token)</summary>
  <div className={styles.thinkingBody}>{thinkingText}</div>
</details>
```

**考虑过**：完全隐藏 + TopBar 开关。否决：用户看不到推理发生过，会困惑为什么模型这样回答。

### D9：超 2000 token 显示"查看更多"折叠

**理由**：用户决策 #7。极端情况（deepseek-reasoner 复杂问题 thinking 5k+ token）即使折叠标题也很长。前 2000 token 完整显示，后面的"查看更多"展开。

**实现**：
```tsx
const PREVIEW = 2000;
const truncated = thinking.length > PREVIEW 
  ? thinking.slice(0, PREVIEW) + '...'
  : thinking;
const [expanded, setExpanded] = useState(false);
{thinking.length > PREVIEW && (
  <button onClick={() => setExpanded(true)}>查看更多</button>
)}
```

**考虑过**：不截断。否决：5000+ token 单块不可读。

### D10：abort 行为统一（思考 + 文本都停）

**理由**：用户决策 #6。用户点"停止"时：
- 文本 stream 停止：已实现（`AbortSignal` + Provider 检查）
- thinking stream 也停止：复用同一个 `AbortSignal`，Provider 内看到 `cancel` 信号就停生成

**实现**：DeepSeek / OpenAI / Anthropic 三 provider 都在 `provider.chatStream` 内 check `AbortSignal.isCancelled()`，取消时立即返回。

**考虑过**：让 thinking 推完（已费 token）。否决：用户已明确"停止"，还让 thinking 推 30s 是糟糕体验。

## Risks / Trade-offs

### R1：Anthropic thinking block 是完整块不是 delta

[Accepted] Anthropic protocol 的 `type="thinking"` 是完整 content block（不是增量），所以 thinking 显示会有"突然出现一大段"现象。v0.x 接受；v0.2 考虑用 streaming + block_uuid 增量。

### R2：OpenAI o1 不暴露 reasoning 内容

[Accepted] OpenAI o1 API 只在 `usage.reasoning_tokens` 暴露 token 数，不暴露 delta 内容。前端能看到"OpenAI o1 思考了 X token"但看不到内容。v0.x 接受；如要内容需升级到 o1 Pro / o3。

### R3：thinking 压缩 AI 二次调用延迟

[Mitigation] v0.x 用"模板压缩"（截断前 50 字 + "[...]"），不调 AI 二次摘要。后续可加 LLM-based 摘要但需评估延迟。

### R4：thinking 进 history 烧 token 速度

[Mitigation] ContextCompressor 保留最近 2 轮 thinking 完整 + 早期压缩；超过 8 轮对话触发"早期 thinking 丢弃"（只保留最近 1 轮）。

### R5：DeepSeek reasoning 偶发 10k+ token

[Mitigation] 前端超 2000 token 折叠"查看更多"；后端 `ThinkingDelta` 累积在 AgentLoop 内最多保留 1 turn（避免内存爆炸）。

### R6：三个 provider 适配工作量翻倍

[Accepted] 3 provider × 3 个测试类（unit / mock / 集成）= 9 个测试，但每个相对独立可并行。v0.x 接受；v0.2 考虑抽象 `ReasoningProvider` 接口。

## Migration Plan

无破坏性变更：
- `StreamChunk` sealed 新增 `ThinkingDelta` 是新增 permits，老代码不需改动（visitor 默认空实现）
- `Message.Assistant` 增 `reasoningTokens` 字段，老代码不传默认 0
- `SseEvent.message_delta` 增 `delta_type="thinking"`，前端 ChatPanel 不订阅新事件就忽略
- `/api/chat/models` 是新端点，不动既有 `/api/chat/send` / `/api/chat/stream/{id}`

部署步骤：
1. `mvn clean package -DskipNpm=true` 构建含新代码
2. 启动后端（HTTP 模式不变）：`mvn spring-boot:run -Dspring-boot.run.profiles=web`
3. 用户访问 https://localhost:8443/ 试用 `/model reasoning` 切到 deepseek-reasoner，看思考流式展示

回滚：单 commit revert；`StreamChunk` 删除 permits 条目 + `ThinkingDelta` record；`Message.Assistant` 字段回退到无；`ChatStreamService` 删 thinking 透传。

## Open Questions

无（11 个核心决策 + 5 个补充决策全部敲定；下个里程碑如发现 Anthropic streaming thinking 真正暴露，再开新 change 适配增量模式）。
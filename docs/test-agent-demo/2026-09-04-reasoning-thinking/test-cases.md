# `2026-09-04-reasoning-thinking/` — 测试用例清单

## 1. 后端 agent-core / StreamChunkThinkingDelta（5 用例）

| 编号 | 场景 | 期望 |
|---|---|---|
| ST-01 | ThinkingDelta 存 text | `new ThinkingDelta("用户问...").text()` === "用户问..." |
| ST-02 | FullVisitor 收 text + thinking | `visits.texts == ["答案"]` & `visits.thinkings == ["思考"]` |
| ST-03 | 老 Visitor 兼容 ThinkingDelta | 不覆盖 visitThinkingDelta → 不抛错 |
| ST-04 | Usage 收 reasoningTokens | `new Usage(10, 20, 5)` 三字段正确 |
| ST-05 | Finished 持 reasoningTokens Usage | `finished.usage().reasoningTokens()` === 1 |

## 2. 后端 agent-core / DeepSeekProvider（2 用例）

| 编号 | 场景 | 期望 |
|---|---|---|
| DP-01 | deepseek-reasoner 推 reasoning_content | emit `ThinkingDelta` + `TextDelta` |
| DP-02 | deepseek-chat 不触发 thinking | thinking chunk 计数 = 0 |

## 3. 后端 agent-core / OpenAiCompatibleMapper（7 用例）

| 编号 | 场景 | 期望 |
|---|---|---|
| OM-01 | o1-preview 含 reasoning_effort: medium | body 含 "reasoning_effort":"medium" |
| OM-02 | o3-mini 含 reasoning_effort | 同上 |
| OM-03 | deepseek-chat 不含 reasoning_effort | body 不含该字段 |
| OM-04 | extra.reasoning_effort=high 覆盖默认 | body["reasoning_effort"] = "high" |
| OM-05 | SSE 含 reasoning_content → ThinkingDelta | emit type=ThinkingDelta |
| OM-06 | reasoning + content 同时出现 → ThinkingDelta 优先 | parser pipeline 第一个命中者胜出 |
| OM-07 | o1 usage 含 completion_tokens_details.reasoning_tokens | Usage.reasoningTokens = 15 |

## 4. 后端 agent-core / AnthropicProvider（7 用例）

| 编号 | 场景 | 期望 |
|---|---|---|
| AP-01 | claude-opus-4 body 含 thinking field | body 含 "thinking":{"type":"enabled","budget_tokens":4096} |
| AP-02 | claude-3-5-sonnet 不含 thinking | body 不含 "thinking" |
| AP-03 | SSE thinking_delta → ThinkingDelta | text 等于 delta.text |
| AP-04 | SSE text_delta → TextDelta | text 等于 delta.text |
| AP-05 | SSE message_stop → Finished(STOP) | finish_reason === STOP |
| AP-06 | event: 行被忽略 | parse 返回 null |
| AP-07 | isThinkingModel 检测 opus-4 / sonnet-4 / 3-7-sonnet | true；claude-3-5-sonnet / null → false |

## 5. 前端 / ThinkingCollapse（5 用例）

| 编号 | 场景 | 期望 |
|---|---|---|
| TC-01 | text="" 不渲染 | container.firstChild === null |
| TC-02 | 默认折叠 + 标题 token 数 | 标题"思考过程 (1 token)" |
| TC-03 | 短文本（≤ 2000）完整展示 | 无"查看更多" |
| TC-04 | 长文本（> 2000）默认截断 | "查看更多"按钮显示 |
| TC-05 | 显式 tokens 参数 | 覆盖估算 |

## 6. 前端 / MessageBubble thinking 集成（4 用例）

| 编号 | 场景 | 期望 |
|---|---|---|
| MB-01 | assistant 带 thinking → ThinkingCollapse 渲染 | thinking + text 都在 |
| MB-02 | user 消息忽略 thinking | thinking 文本不显示 |
| MB-03 | 无 thinking 时不显示 details 元素 | details === null |
| MB-04 | thinking + tools 共存 | details 元素 + Read 工具都在 DOM |

# `2026-09-04-reasoning-thinking/` — 测试复盘

## 1. 流程回顾

按 OpenSpec `explore → propose → apply` 流程：
1. **explore**：brainstorming 10 决策（按 message 动态检测 / 逐 token 推 / 折叠 UI / 单独计费 / 三 provider 适配 / abort 同步停 / 2000 token 截断 / 早期摘要 / 跨 turn 合并 / Redactor 脱敏）。
2. **propose**：1 个 change（add-reasoning-thinking-streaming）+ 4 artifacts。
3. **apply**：36 task，分 3 个 commit 全部 push。
4. **archive**：本批次后归档。

## 2. 做得好的

- **`StreamChunk.ThinkingDelta` sealed 第 8 类型**：visitor 模式默认空方法，老 visitor 编译通过（`StreamChunkThinkingDeltaTest` 验证）。
- **兼容 2-arg Assistant 构造**：老 `new Assistant(content, toolCalls)` 自动转发 4-arg 兼容构造（reasoning=List.of(), tokens=0），无破坏性。
- **SseSessionLogSink 已有 thinking 透传逻辑**（v0.1 spec 就绪）—— v0.1 永远没触发，v0.2 AgentLoop 改后自动激活。
- **`Message.Assistant` 4-arg 兼容构造 + `List.copyOf` reasoning 不可变**：避免 set 误用。
- **三 provider 独立解析不抽象**：DeepSeek / OpenAI o1 / Anthropic 各自实现，差异大不强行统一。
- **`ThinkingCollapse` 默认折叠 + 2000 token 截断 + "查看更多"按钮**：避免 5k+ token 单块不可读。

## 3. 可改进

- **OpenAI o1 reasoning 字段**：协议只暴露 `reasoning_tokens` 计数，不暴露内容。v0.x 接受；如要内容需等 o1 Pro / o3 公开 streaming reasoning（目前仍无）。
- **Anthropic thinking block 是完整块**：不是增量。v0.2 用 block_uuid 做增量。
- **跨 turn thinking 合并**：v0.x ChatPanel 单 turn 累加；v0.2 加 turnIndex 标记 + 跨 turn 合并 UI。
- **/model slash 命令**：web 端 task 5.5 跳过；v0.2 补。
- **Playwright e2e**：本机无 Chrome GUI 跑不动；v0.2 加。
- **`ContextCompressor.thinking-aware` 实现简化**：早期消息的 thinking 拼成单行摘要（v0.x 简化版），不调 AI 二次摘要。

## 4. 风险与遗留

- **后端 `ChatStreamService.create()` 当前还是 `streams.create(sessionId, "deepseek-chat", ...)`** v0.1 写死的 model。v0.2 改造为 model 参数从 SendRequest.model 读取 + ModelRegistry 校验（已实现）。
- **`Application` 在 h2 / h3 / heading 1 标签渲染兼容**：ThinkingCollapse 用 `<details>/<summary>` 浏览器原生支持，零依赖。
- **token 计数估算**：`Math.ceil(text.length / 4)` 粗略估算（中文 1 字 ≈ 2 token）；v0.2 接 jtokkit 客户端精确计算。
- **Provider 抽象**：v0.3 引入 `ReasoningProvider` 接口（待 v0.x 验证稳定性再设计）。

## 5. 交付物

- 12 个新文件 + 8 个修改
- 3 个 commit 全部 push 到 origin/main（47f4392 / bc7ccb2 / d90f8d4 / 待 archive）
- 343/343 agent-core + 153/153 agent-web + 104/104 前端
- 测试文档四件套
- `docs/reasoning-thinking.md`（架构 + 决策记录）

## 6. 归档状态

✅ change `add-reasoning-thinking-streaming` 已 archive 到 `openspec/changes/archive/2026-09-13-add-reasoning-thinking-streaming/`。

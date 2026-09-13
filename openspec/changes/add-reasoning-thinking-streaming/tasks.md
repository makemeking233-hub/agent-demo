## 1. 核心数据层（agent-core/llm）

- [x] 1.1 `agent-core/.../llm/StreamChunk.java` 新增 `ThinkingDelta(String text)` record + `StreamChunkVisitor.visitThinkingDelta` 默认空方法（permit 列表 + 测试）
- [x] 1.2 `agent-core/.../core/Message.java` `Assistant` record 新增 `reasoningTokens` 字段（默认 0）
- [x] 1.3 `agent-core/.../llm/Usage.java` / `StreamChunk.Usage` 新增 `reasoningTokens` 字段
- [x] 1.4 `agent-core/.../llm/StreamChunkTest.java` 扩 `ThinkingDelta` record 测试 + Visitor 兼容性测试

## 2. Provider 层

- [ ] 2.1 `DeepSeekProvider` 解析 `choices[].delta.reasoning_content` → emit `ThinkingDelta`；`usage` 解析 `completion_tokens` + `reasoning_tokens`（独立计费）
- [ ] 2.2 `DeepSeekProviderTest` 扩 reasoning 流测试（正常 / 不触发 chat / token 单独计费）
- [ ] 2.3 `OpenAiCompatibleProvider` 适配 o1 / o3：`reasoning_effort` 参数 + `usage.completion_tokens_details.reasoning_tokens` 解析
- [ ] 2.4 `OpenAiCompatibleProviderTest` 扩 o1 reasoning token 测试（mock OpenAI o1 响应）
- [ ] 2.5 `AnthropicProvider` 解析 `content[].type="thinking"` 块 → emit `ThinkingDelta`（text + thinking 混合按数组顺序推）
- [ ] 2.6 `AnthropicProviderTest` 扩 thinking block 测试

## 3. AgentLoop 核心逻辑

- [ ] 3.1 `AgentLoop` 加 `currentThinking: List<String>` 字段；`processTurn` 内累积 ThinkingDelta + 通过 `SessionLogSink.onThinkingDelta` 转发
- [ ] 3.2 `AgentLoop.processTurn` turn 收尾时把 `currentThinking` 合并到 `Message.Assistant.thinking` + 清空 list
- [ ] 3.3 `SessionLogSink` 接口加 `onThinkingDelta(delta)` 默认空方法 + `CompositeSessionLogSink` 派发
- [ ] 3.4 `SessionLogger.thinking.log` 写入逻辑：从 v0.1 "v0.1 恒为空" 改为实际写入（带 Redactor 脱敏）
- [ ] 3.5 `AgentLoopTest` 扩 thinking 累积 + turn 收尾合并 + 跨 turn 清空测试

## 4. ContextCompressor thinking-aware 压缩

- [ ] 4.1 `ContextCompressor` 加 thinking-aware 压缩：保留最近 2 轮完整 + 早期压缩为摘要模板 `[第 N 轮思考摘要] <前 50 字>...`
- [ ] 4.2 `ContextCompressorTest` 扩 3 轮 / 5 轮 / 10 轮场景测试

## 5. 后端 SSE / API 层（agent-web）

- [ ] 5.1 `SseEvent` / `messageDelta` 增 `deltaType` 字段值 "thinking" 适配（已有 text，新增 thinking）
- [ ] 5.2 `ChatStreamService` 透传 `StreamChunk.ThinkingDelta` → SSE `message_delta` (delta_type: "thinking")，与 text_delta 同频逐 token
- [ ] 5.3 `ChatStreamServiceTest` 扩 thinking 流测试（按事件序 / abort 时同时停）
- [ ] 5.4 `ModelsController` 新增 `GET /api/chat/models` 端点（从 `agent.chat.supported-models` 读）+ `ModelsControllerTest` 单测
- [ ] 5.5 `SlashCommand` 加 `/model <name>` 命令（含 `/model reasoning` / `/model chat`），响应 `message_delta` 提示
- [ ] 5.6 `application-web.yml` 增 `agent.chat.supported-models: [deepseek-chat, deepseek-reasoner]` + `default-model: deepseek-chat`

## 6. 前端核心流（TypeScript）

- [ ] 6.1 `frontend/src/api/chat.ts` types 加 `deltaType: "thinking"` 适配 + `Message.thinking: string[]` 字段 + `Message.reasoningTokens` 字段
- [ ] 6.2 `useChatStream` 解析 `delta_type="thinking"` 事件 → 累积到当前 assistant 消息的 `thinking` 字段（独立于 text）
- [ ] 6.3 `useChatStream` 解析 `usage.reasoning_tokens` → 写 reasoningTokens（前端展示）

## 7. 前端 UI 组件

- [ ] 7.1 `frontend/src/components/ThinkingCollapse.tsx` 新增可折叠组件（默认折叠 + 标题"思考过程 (N token)" + 超 2000 token "查看更多"）
- [ ] 7.2 `MessageBubble.tsx` 集成 `ThinkingCollapse`：assistant 消息上方显示（如果 thinking 非空）
- [ ] 7.3 `ModelsDropdown.tsx` 新增模型选择下拉（从 `GET /api/chat/models` 拉列表 + 调用 `SlashCommand` 切模型）
- [ ] 7.4 `TopBar.tsx` 集成 `ModelsDropdown`（放在 Settings 图标旁）
- [ ] 7.5 `ChatPanel` / `MessageBubble` 跨 turn thinking 合并（按事件序累加，不按 turn 分割）
- [ ] 7.6 `ChatPanel` abort 时 thinking 标记"已中断"（message_stop event finish_reason="aborted"）

## 8. 前端测试

- [ ] 8.1 `tests/chat-stream-thinking.test.tsx` 扩 useChatStream 解析 thinking delta + 跨 turn 合并 + abort
- [ ] 8.2 `tests/MessageBubble.thinking.test.tsx` 扩 ThinkingCollapse 渲染 / 折叠 / 跨 turn 合并
- [ ] 8.3 `tests/ThinkingCollapse.test.tsx` 扩 超 2000 token 截断 + 展开按钮

## 9. 后端测试

- [ ] 9.1 `StreamChunkThinkingDeltaTest` 单独测试 ThinkingDelta record + Visitor 派发
- [ ] 9.2 `AgentLoopThinkingTest` 扩 thinking 累积 + history 合并 + abort 测试
- [ ] 9.3 `ChatStreamServiceThinkingTest` 扩 thinking SSE 透传
- [ ] 9.4 `mvn -pl agent-core test` 全绿 + `mvn -pl agent-web test` 全绿（跳过 E2E）
- [ ] 9.5 `mvn -pl agent-core,agent-web verify` jacoco 门禁通过（LINE≥80% / BRANCH≥70%）

## 10. 文档 + 收尾

- [ ] 10.1 新增 `docs/reasoning-thinking.md`（架构 + 缓存策略 + 三 provider 适配表 + 决策记录）
- [ ] 10.2 写四件套：`docs/test-agent-demo/2026-09-04-reasoning-thinking/{test-design,test-cases,test-report,test-review}.md` + 更新 `test-guide.md` §2.9
- [ ] 10.3 `openspec validate add-reasoning-thinking-streaming --type change --strict` 通过 + `openspec archive add-reasoning-thinking-streaming --yes` + commit + push
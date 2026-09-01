# Tasks: 后端工具调用时序

## 1. SseSessionLogSink 顺序

- [ ] 1.1 `SseSessionLogSink.onAssistant` 重排：先 emit `tool_call_start`（每个 toolCall），再 emit `message_delta`(text)，最后 thinking

## 2. 测试与验证

- [ ] 2.1 agent-web `SseSessionLogSinkTest` 保持通过
- [ ] 2.2 `mvn verify -DskipNpm=true` 全绿（含前端 vitest）
- [ ] 2.3 commit + push + archive（中文 Conventional Commits）

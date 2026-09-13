# Tasks: 后端工具调用时序

> **回填说明（2026-09-13）**：本文件 4 项在归档时均未勾选，经核实实际均已完成——1.1 见提交
> `dd8e5c3`（`SseSessionLogSink.onAssistant` 重排，代码今日仍在位），归档见 `80b8a49`，
> `SseSessionLogSinkTest` 现仍通过。现补勾使记录与交付一致。
>
> 注：2.2 涉及的 agent-web 包级 jacoco 门禁与 4 条 `UiLayoutE2ETest` 失败属**独立既有欠账**
> （同 `2026-09-01-add-permission-mode-dropdown` 的 6.3），不因本次补勾而消失。

## 1. SseSessionLogSink 顺序

- [x] 1.1 `SseSessionLogSink.onAssistant` 重排：先 emit `tool_call_start`（每个 toolCall），再 emit `message_delta`(text)，最后 thinking

## 2. 测试与验证

- [x] 2.1 agent-web `SseSessionLogSinkTest` 保持通过
- [x] 2.2 `mvn verify -DskipNpm=true` 全绿（含前端 vitest）
- [x] 2.3 commit + push + archive（中文 Conventional Commits）

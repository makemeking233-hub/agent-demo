# 任务：add-web-session-restore

> TDD 优先：每个任务"测试先红 → 实现 → 测试转绿"。`mvn verify`（jacoco LINE≥80% / BRANCH≥70%）收尾。
> 前端用 vitest；后端用 JUnit5 + Mockito。

## T1: SessionStore 按 id 读取

- [ ] 先写测试 `SessionStoreTest#loadById_readsNamedSession`：在临时目录写 `s-1.jsonl`（若干 SessionEntry），
  `SessionStore.loadById(dir, "s-1")` 返回有序 entries；`loadById(dir,"missing")` 返回空 list；目录不存在返回空。
- [ ] 实现 `SessionStore.loadById(Path, String)`，抽取私有 `readEntries(Path)`；`loadLatest` 改为复用
  `readEntries`。
- [ ] `mvn -pl agent-core test -Dtest=SessionStoreTest` 全绿。

## T2: SessionResumeLoader 按 id 加载

- [ ] 先写 `SessionResumeLoaderTest#loadById_restoresMessagesAndTokens`：含 user/assistant(toolCalls)/
  tool_result(isError)/meta 的 entry，确认还原出对应 `Message`（含 ToolCall/toolCallId/isError）与 token。
- [ ] 实现 `SessionResumeLoader.loadById(Path, String)`，抽取 `toMessages(List<SessionEntry>)`；
  `load(sessionsDir)`（最近）改为复用 `toMessages`，`injectOrphanSkeletons` 保持。
- [ ] `mvn -pl agent-core test -Dtest=SessionResumeLoaderTest` 全绿。

## T3: CompositeSessionLogSink

- [ ] 先写 `CompositeSessionLogSinkTest`：两个子 sink 都收到 `onUser/onAssistant/onToolResult/onTurnEnd`；
  空列表不抛异常。
- [ ] 实现 `com.example.agent.log.CompositeSessionLogSink`（`List<SessionLogSink>` 逐回调转发）。
- [ ] `mvn -pl agent-core test -Dtest=CompositeSessionLogSinkTest` 全绿。

## T4: WebAgentRuntime 落盘 + history 回填

- [ ] 先写 `WebAgentRuntimeTest#historyFor_backfillsFromDisk`：临时 sessions 目录放 `s-9.jsonl`（user+assistant），
  `historyFor("s-9")` 返回 size≥2 的 history；`historyFor("new")` 返回空；`sinkFor` 返回非空复合 sink。
- [ ] 实现 `WebAgentRuntime.recorderFor / sinkFor / messagesFor`，并让 `historyFor(sessionId)` 回填。
- [ ] 让 `ChatStreamService.create` 用 `runtime.sinkFor(sessionId, sseSink)`。
- [ ] `mvn -pl agent-web test` 全绿。

## T5: history 端点

- [ ] 先写 `SessionControllerTest#messages_returnsTranscript`（MockMvc，MockBean provider）：写临时会话文件后
  `GET /api/sessions/{id}/messages` 返回 200 + 消息数组；未知 id 返回 404。
- [ ] 实现 DTO + `SessionController.messages(sessionId)`（用 `WebAgentRuntime.messagesFor`）。
- [ ] `mvn -pl agent-web test -Dtest=SessionControllerTest` 全绿。

## T6: 前端持久化 + 回填

- [ ] 先写 `ChatPanel.test`（或扩展）：设置 localStorage session_id + items，挂载后恢复 session_id 并
  调用 history 回填；`/clear` 清空 localStorage。
- [ ] 实现 `chat.ts#history(sessionId)` + ChatPanel 挂载回填 + localStorage 读写。
- [ ] `cd agent-web/frontend && npm run test` 全绿。

## 收尾

- [ ] `mvn verify` 全绿（jacoco 门禁通过）。
- [ ] commit + push（中文 Conventional Commits）。
- [ ] `openspec-archive-change add-web-session-restore`。

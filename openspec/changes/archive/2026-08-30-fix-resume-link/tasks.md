# Tasks: Resume 链路修复

## 1. 工具类：SessionResumeLoader

- [x] 1.1 新增 `SessionResumeLoader` + `ResumeResult` record：`load(sessionsDir)` 逐 entry 转换 user/assistant/tool_result/system/meta，解析 `extras.toolCalls`/`extras.toolCallId`/`extras.isError`，恢复 token 累计
- [x] 1.2 新增 `snip(List<Message>, TokenEstimator, maxTokens)`：裁剪（超限时旧轮坍缩为 summary system 消息，保留最新轮）

## 2. 并行孤儿处理

- [x] 2.1 转换后后处理：为无前置 assistant.tool_calls 的孤儿 tool_result 注入合成 assistant 骨架，保证序列 well-formed

## 3. 接入 SlashCommand / ChatCommand

- [x] 3.1 `SlashCommand.doResume` 改用 `SessionResumeLoader`，`onResume` 回调传 `ResumeResult`（保留 8 参旧版兼容）
- [x] 3.2 `ChatCommand` `/resume` 回调同步 loader 返回的 token 到累计器 + snip

## 4. 测试与验证

- [x] 4.1 新增 `SessionResumeLoaderTest`：toolCalls 恢复、toolCallId/isError、meta token、snip、孤儿处理
- [x] 4.2 适配 `SlashCommandTest`（保持通过）
- [x] 4.3 `mvn -pl agent-core verify` 全绿（jacoco 门禁达标）
- [x] 4.4 commit + push（中文 Conventional Commits）

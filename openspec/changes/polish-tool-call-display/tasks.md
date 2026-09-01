# Tasks: 工具调用卡片折叠与内联显示

## 1. ToolCallCard 折叠

- [x] 1.1 `ToolCallCard` 加折叠状态（useState 默认折叠）；header 点击切换，Chevron 指示，有输出才显示箭头
- [x] 1.2 css header 改为可点击按钮样式 + chevron 样式

## 2. ChatPanel 内联工具到 assistant 消息

- [x] 2.1 `Item` assistant 文本项扩展 `tools?: InlineTool[]`
- [x] 2.2 `tool_call_start` → `addToolToLastAssistant`（内联到最近 assistant，无则新建）
- [x] 2.3 `tool_call_end` → `updateToolInLastAssistant`（按 toolCallId 更新，找不到回退独立 tool item）

## 3. MessageBubble 渲染内联工具

- [x] 3.1 `MessageBubble` 加 `tools` prop，在文本后按顺序渲染 ToolCallCard；空文本无工具不显示占位

## 4. 测试与验证

- [x] 4.1 更新 `ToolCallCard.test`/`MessageBubble.test` 覆盖折叠/内联；前端 vitest 全绿（15 测试）
- [x] 4.2 `vite build` 更新 static 产物（新 js/css）
- [x] 4.3 commit + push + archive（中文 Conventional Commits）

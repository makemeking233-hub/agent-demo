## Context

agent-web 前端工具调用卡片展示优化：① 卡片可折叠（默认收起，点击展开）；② 工具调用内联到所属 assistant 消息（而非聚集到消息下方）。纯前端改动，无后端 API 变更。

## Goals / Non-Goals

**Goals:**
- 工具调用卡片默认折叠，点击标题展开/收起详情。
- 工具调用内联到最近一条 assistant 消息，按事件到达顺序展示。

**Non-Goals:**
- 不改后端 SSE 事件模型（仍按 `tool_call_start/end` 推送）。
- 不改工具调用三态（running/ok/fail）逻辑，仅展示增强。

## Decisions

**D1: `ToolCallCard` 加折叠状态（useState，默认折叠）。**
- header 变为可点击按钮，`ChevronRight/Down` 指示；有输出才显示箭头；点击切换。有输出默认收起、点击展开。

**D2: `ChatPanel` 把工具调用内联到最近一条 assistant 项。**
- `Item` 的 assistant 文本项扩展 `tools?: InlineTool[]`。
- `tool_call_start` → `addToolToLastAssistant`：从后往前找最后一条 assistant 项，把工具加进其 `tools`；无 assistant 则新建空 assistant 承载。
- `tool_call_end` → `updateToolInLastAssistant`：在 assistant 项的 tools 里按 toolCallId 更新；找不到回退到独立 tool item。

**D3: `MessageBubble` 渲染内联工具卡片。**
- 加可选 `tools` prop；在文本后按顺序渲染 `ToolCallCard`。
- 文本为空且无工具时不显示 `…` 占位（避免空助手气泡）。

**D4: 默认折叠 + 可点击。**
- `ToolCallCard` 默认 `collapsed=true`，点击 header 切换。css 的 header 改为可点击按钮样式。

## Risks / Trade-offs

- [内联依赖"最后一条 assistant"] → 用函数式 setItems 从后往前实时找，避免陈旧闭包；无 assistant 时新建承载，保证工具不丢。
- [旧数据/回退] → `updateToolInLastAssistant` 找不到内联 tool 时回退到独立 tool item，兼容历史渲染。

## Migration Plan

1. `ToolCallCard` 折叠 + css。
2. `MessageBubble` 内联 tools 渲染。
3. `ChatPanel` 内联/更新工具到 assistant。
4. 更新 `ToolCallCard.test`/`MessageBubble.test` 覆盖折叠/内联。
5. 前端 vitest 全绿 + vite build 更新产物。

## Open Questions

- 内联是否需要在"模型先调工具再写文本"时把工具放文本前：当前取决于后端事件顺序（后端 onAssistant 先文本后工具）。本 change 聚焦「内联归属正确消息」，严格时序留后端调整（另 change）。

## Why

agent-web 前端工具调用卡片展示有两个问题：① 工具调用过程无法折叠，长输出占满对话区；② 工具调用卡片脱离所属的 assistant 消息，被集中显示在消息下方（而非按发生顺序内联），导致"结果生成后工具调用跑到底下"的乱序，阅读混乱。

## What Changes

- **工具调用卡片可折叠**：`ToolCallCard` 加折叠状态，默认收起；点击 header 展开/收起；无输出时不渲染内容区。
- **工具调用内联到 assistant 消息**：`ChatPanel` 把 `tool_call_start/end` 事件内联到最近一条 assistant 消息项，`MessageBubble` 在文本下按到达顺序渲染工具卡片；工具不再脱离消息跑到下方。

## Capabilities

### New Capabilities
- （无）

### Modified Capabilities
- `web-ui`：修改工具调用展示的行为（可折叠 + 内联到消息）。

## Impact

- 受影响前端组件：`agent-web/frontend/src/components/ToolCallCard.tsx`、`MessageBubble.tsx`、`ChatPanel.tsx`、`ToolCallCard.module.css`。
- 测试：`ToolCallCard.test.tsx`、`MessageBubble.test.tsx` 更新（覆盖折叠/内联）。
- build 产物：`agent-web/src/main/resources/static/` 下 js/css 更新。
- 无后端 API 变更（纯前端渲染/交互）。

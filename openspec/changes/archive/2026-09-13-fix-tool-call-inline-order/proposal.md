## Why

多轮工具调用全部堆在回复最底部，与调用发生的时机脱节（见图一：8 张工具卡整排挤在末尾）。DSH 的做法是按调用发生的位置内联排布（见图二），用户能顺着读「思考 → 做了什么 → 又说了什么」。

**根因（数据模型而非样式）**：`ChatPanel` 的 assistant item 是个**桶**——`{ text, thinking?, tools? }`，而 `MessageBubble` 固定按 `thinking → text → tools` 顺序渲染。多轮迭代的顺序是「工具 → 工具 → … → 最终文本」，但 `appendTextToLastAssistant` 会把最终文本**合并进那个已挂着工具的 item**，于是文本被渲染到所有工具卡**上面**，工具卡留在底部。

复现时序：

```text
iter1  tool_call_start Ls        → 无 assistant 文本项 → 新建 item A1{text:"", tools:[Ls]}
iter1  tool_call_end   Ls        → 更新 A1
iter1  tool_call_start Shell     → 追加到 A1.tools
...
iter4  message_delta(text) "你好…" → appendTextToLastAssistant 合并进 A1（A1 已有 tools）
                                   → MessageBubble 渲染 A1: text 在上、8 张卡在下  ← 图一
```

**附带发现（同属工具卡排布错误）**：`mapHistoryToItems` 对同一次调用会生成**两张卡**——assistant 消息的 `toolCalls` 生成一张内联卡（`status: ok`、无输出），紧跟的 `role: "tool"` 消息又生成一张独立卡（`name` 硬编码为字面量 `"tool"`、带输出）。刷新会话后每次工具调用都会重复出现，且第二张卡的工具名显示为 `tool`。

## What Changes

- `appendTextToLastAssistant` / `appendThinkingToLastAssistant`：**若最后一条 assistant 文本项已挂载工具，则新建一条 item 而不是合并**——使每次迭代的文本落在该迭代工具卡之后，恢复真实时间线。
- `mapHistoryToItems`：按 `toolCallId` 把 `role: "tool"` 的结果**并入**对应 assistant item 的内联工具（带上 `text` 与 `isError` 状态），只有匹配不到调用时才退化为独立卡（孤儿）。消除重复卡与字面量 `tool` 名称。
- **视觉不动**：保留现有带边框卡片与「完成 · Nms」文案（用户明确选择）。

## Capabilities

### Modified Capabilities

- `web-ui-layout`：新增「助手消息内工具调用按调用顺序内联排布」与「历史重建不重复渲染工具调用」两个 Requirement。

## Impact

- **前端（2 个文件）**：`components/ChatPanel.tsx`（3 个函数）、`components/ChatPanel.test.tsx`（补用例）
- **后端 / 协议**：零改动（SSE 事件顺序本就正确：`message_delta(text)` 先于该迭代的 `tool_call_start`）
- **文档**：`README.md` §10 补一句工具卡内联说明

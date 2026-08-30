## ADDED Requirements

### Requirement: 对话区消息渲染

系统 SHALL 在中间对话区渲染用户/助手消息与工具调用/权限卡片，并以 DeepSeek Harness 风格样式呈现。

#### Scenario: 助手 markdown 渲染

- **WHEN** 助手消息包含 markdown 文本
- **THEN** 对话区用 Markdown 渲染显示（含行内代码、粗体、代码块）

#### Scenario: 工具调用卡片三态

- **WHEN** 一轮中出现工具调用
- **THEN** 对话区在 tool_call_start 时渲染"执行中"卡片，tool_call_end 时更新为"完成/失败"卡片，并显示耗时与结果

### Requirement: 会话流式状态

系统 SHALL 在流式回复期间显示进行中状态，并允许用户中断。

#### Scenario: abort 按钮

- **WHEN** 流式回复进行中（stream_id 存在且未 message_stop）
- **THEN** 底部输入区显示 abort 按钮，点击后调用 `/api/chat/abort/{id}`

#### Scenario: 流结束恢复输入

- **WHEN** 收到 message_stop 或流错误
- **THEN** abort 按钮消失，输入框恢复可用

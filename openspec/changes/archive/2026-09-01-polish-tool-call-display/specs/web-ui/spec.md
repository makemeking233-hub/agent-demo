# web-ui Specification (delta)

> 本文件是 `polish-tool-call-display` 的 delta spec。在 archive 时合并到 `openspec/specs/web-ui/spec.md`。

## ADDED Requirements

### Requirement: 工具调用卡片折叠与内联

系统 SHALL 让工具调用卡片在对话区内联到所属的 assistant 消息（按到达顺序，而非聚集到消息下方），且默认折叠——用户可点击卡片标题展开/收起详情（输出内容）。

#### Scenario: 工具调用内联到 assistant 消息

- **WHEN** 一轮中出现工具调用（`tool_call_start` / `tool_call_end`）
- **THEN** 对应的工具调用卡片内联渲染在最近一条 assistant 消息内（按事件到达顺序），而不是聚集在消息列表下方

#### Scenario: 工具调用卡片默认折叠

- **WHEN** 一个工具调用卡片渲染且包含输出内容
- **THEN** 卡片默认折叠（只显示标题带：图标 + 工具名 + 状态/耗时），详情（输出）默认隐藏

#### Scenario: 点击展开/收起

- **WHEN** 用户点击工具调用卡片的标题行
- **THEN** 卡片在折叠/展开之间切换（展开时显示输出内容，收起时隐藏）

#### Scenario: 无输出不渲染详情区

- **WHEN** 工具调用卡片无输出内容
- **THEN** 不渲染详情区（`<pre>`），卡片仅显示标题带

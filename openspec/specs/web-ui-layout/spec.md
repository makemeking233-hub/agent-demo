# web-ui-layout Specification

## Purpose
TBD - created by archiving change polish-web-ui-frontend. Update Purpose after archive.
## Requirements
### Requirement: 三栏布局外壳

系统 SHALL 提供一个 DeepSeek Harness 风格的三栏布局：顶栏（品牌 logo + 应用名 + 操作按钮）、左侧会话列表、中间对话区、底部输入区。

#### Scenario: 默认三栏展示

- **WHEN** 用户在浏览器打开应用
- **THEN** 页面渲染为顶栏 + 左侧会话列表 + 中间对话区 + 底部多行输入的四段布局，左侧列表为分组树状（工作区 → 会话）

#### Scenario: 主题切换

- **WHEN** 用户点击顶栏的主题切换按钮
- **THEN** 页面在亮/暗两套 `--dsw-*` token 之间切换，且选择持久化到 `localStorage`

### Requirement: 会话列表可选中

系统 SHALL 允许用户从左侧会话列表中选择一个会话，并高亮当前选中项。

#### Scenario: 选中会话

- **WHEN** 用户点击左侧列表中的某个会话项
- **THEN** 该会话项获得选中高亮样式，其余项取消高亮

#### Scenario: 静态占位数据

- **WHEN** 左侧列表没有真实 session 接口数据时
- **THEN** 展示一组静态占位会话（工作区分组 + 会话条目），供 UI 预览与交互

### Requirement: 底部多行输入

系统 SHALL 提供一个支持多行、快捷键发送的底部输入区。

#### Scenario: Ctrl+Enter 发送

- **WHEN** 用户按 Ctrl+Enter（或 Cmd+Enter）
- **THEN** 提交当前输入内容

#### Scenario: Shift+Enter 换行

- **WHEN** 用户按 Shift+Enter
- **THEN** 在输入框内插入换行而不提交

#### Scenario: 空输入不可发送

- **WHEN** 输入框内容为空或全空白
- **THEN** 发送按钮禁用

### Requirement: 工具调用按调用顺序内联排布

助手回合内，工具调用卡片 SHALL 出现在其调用发生的时间线位置（紧随该次调用之前累积的文本之后），SHALL NOT 统一堆叠在整条回复的末尾。

#### Scenario: 先工具后文本

- **WHEN** 某回合先产生若干工具调用（其间无文本），随后才产生最终文本
- **THEN** 工具卡片排列在最终文本**之前**
- **AND** 卡片顺序与调用顺序一致

#### Scenario: 每次迭代「文本后工具」

- **WHEN** 某次迭代既有文本又有工具调用
- **THEN** 该迭代的工具卡片紧随该迭代的文本之后

#### Scenario: 多次迭代交错

- **WHEN** 回合包含「工具 → 文本+工具 → 文本」三次迭代
- **THEN** 渲染顺序为「工具卡 → 文本1 → 工具卡 → 文本2」
- **AND** 每次迭代的文本不会跳到其所属工具卡之前

#### Scenario: 思考与文本同属一次迭代

- **WHEN** 某次迭代先流式输出思考、再输出文本
- **THEN** 两者落在同一条助手消息内
- **AND** 思考渲染在文本上方

### Requirement: 历史重建不重复渲染工具调用

从服务端历史重建时，同一次工具调用 SHALL 只渲染一张卡片，SHALL 显示其真实工具名与执行结果。

#### Scenario: 助手消息加工具结果消息

- **WHEN** 历史包含一条带 `toolCalls` 的助手消息，以及紧随其后的 `role: "tool"` 结果消息
- **THEN** 该调用只渲染一张卡片
- **AND** 卡片显示真实工具名（不是字面量 `tool`）
- **AND** 卡片可展开查看该结果

#### Scenario: 孤儿工具结果

- **WHEN** 历史中存在找不到对应调用的工具结果
- **THEN** 该结果仍以独立卡片渲染，不丢失


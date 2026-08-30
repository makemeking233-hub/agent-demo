## ADDED Requirements

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

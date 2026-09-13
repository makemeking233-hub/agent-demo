## ADDED Requirements

### Requirement: GFM 表格渲染

对话区 SHALL 把助手/用户消息中的 GFM 表格渲染为真正的 `<table>` 元素（含表头、单元格边框、横向溢出滚动），不得以竖线原文形式显示。

#### Scenario: 标准表格渲染为表格元素

- **WHEN** 消息包含 `| 维度 | 2PC | TCC |` 加 `|---|---|---|` 分隔行与数据行
- **THEN** 对话区渲染出 `<table>`，首行为表头单元格，后续为数据行
- **AND** 页面中不出现 `|---|` 这样的分隔行原文

#### Scenario: 宽表格横向滚动不撑破气泡

- **WHEN** 表格列数多到超出消息气泡宽度
- **THEN** 表格容器出现横向滚动条
- **AND** 气泡本身宽度不超出其 `max-width`，页面不出现横向滚动

#### Scenario: 未闭合的表格按纯文本显示

- **WHEN** 消息以 `| a | b |` 开头但缺少分隔行（表格语法不成立）
- **THEN** 该段按普通段落文本渲染，不抛错、不影响其余内容的渲染

### Requirement: GFM 其他扩展渲染

对话区 SHALL 支持 GFM 的删除线、任务列表与自动链接。

#### Scenario: 删除线与任务列表渲染

- **WHEN** 消息包含 `~~已废弃~~` 与 `- [x] 已完成` / `- [ ] 待办`
- **THEN** 删除线渲染为 `<del>` 样式，任务列表渲染为带勾选框的列表项

#### Scenario: 裸 URL 自动成为链接

- **WHEN** 消息包含裸露的 `https://example.com/path` 文本
- **THEN** 该文本渲染为可点击的 `<a>` 元素

### Requirement: 数学公式渲染

对话区 SHALL 把 `$$...$$` 块级公式与 `$...$` 行内公式渲染为 KaTeX 排版，不得显示 `\frac` 等 LaTeX 源码。

#### Scenario: 块级公式渲染

- **WHEN** 消息包含独立成段的 `$$E = mc^2$$`
- **THEN** 该公式以 KaTeX 排版居中渲染，页面不出现 `$$` 定界符原文

#### Scenario: 非法公式降级不中断渲染

- **WHEN** 消息包含语法错误的公式（如 `$$\frac{1}{$$`）
- **THEN** KaTeX 解析失败时该处降级显示原始文本
- **AND** 消息中其余段落、表格、代码块仍正常渲染，整条消息不白屏

### Requirement: 代码块语法高亮

对话区 SHALL 对带语言标记的围栏代码块做语法高亮，且 SHALL 仅按需注册语言集，不引入全量高亮语言包。

#### Scenario: 已知语言高亮

- **WHEN** 消息包含 ```` ```java ```` 围栏代码块
- **THEN** 代码块内的关键字、字符串、注释以不同颜色呈现

#### Scenario: 未知语言降级为纯代码块

- **WHEN** 消息包含未注册语言的围栏（如 ```` ```brainfuck ````）
- **THEN** 代码块仍以等宽字体与深色背景渲染，不做高亮
- **AND** 控制台无未捕获异常，消息其余部分正常渲染

### Requirement: 消息内图片渲染

对话区 SHALL 渲染消息中的 Markdown 图片语法，支持远程 URL 与本地文件路径两种来源；本地路径 SHALL 经后端 `/api/fs/raw` 接口读取。

#### Scenario: 远程图片渲染

- **WHEN** 消息包含 `![架构图](https://example.com/a.png)`
- **THEN** 对话区渲染 `<img>` 并加载该远程图片

#### Scenario: 本地图片经接口渲染

- **WHEN** 消息包含 `![架构图](C:\Users\me\docs\a.png)`（位于 `$HOME` 子树内）
- **THEN** 图片 `src` 指向 `/api/fs/raw?path=<该路径>` 并成功加载显示

#### Scenario: 图片加载失败显示 alt 占位

- **WHEN** 图片地址不可达或后端返回 4xx
- **THEN** 该位置显示 `alt` 文本占位与失败提示，不显示浏览器默认的碎图图标
- **AND** 消息其余内容正常渲染

### Requirement: 消息内链接安全基线

对话区 SHALL 对所有渲染出的链接附加 `target="_blank"` 与 `rel="noopener noreferrer"`，且 SHALL NOT 把非 `http`/`https`/`mailto` 协议的链接渲染为可点击元素。

#### Scenario: 外链带安全属性

- **WHEN** 消息包含 `[文档](https://example.com)`
- **THEN** 渲染出的 `<a>` 同时带有 `target="_blank"` 与 `rel="noopener noreferrer"`

#### Scenario: 危险协议不成为可点击链接

- **WHEN** 消息包含 `[点我](javascript:alert(1))`
- **THEN** 该处不渲染为可点击的 `<a href="javascript:...">`
- **AND** 页面不执行任何脚本

### Requirement: 原始 HTML 不渲染

对话区 SHALL NOT 渲染消息中的原始 HTML 标签；所有 HTML 一律作为纯文本转义显示。这是本能力的安全基线，不得通过引入 `rehype-raw` 等方式放开。

#### Scenario: script 标签被转义

- **WHEN** 消息包含 `<script>alert(1)</script>`
- **THEN** 该段以纯文本形式显示标签本身
- **AND** 不弹出任何 alert，DOM 中不存在被注入的 `<script>` 元素

#### Scenario: 事件属性不执行

- **WHEN** 消息包含 `<img src=x onerror="alert(1)">`
- **THEN** 不渲染为真实 `<img>` 元素，`onerror` 不被执行

### Requirement: 流式期间的渲染时序

对话区 SHALL 在流式追加文本期间保持文本逐帧可见，且 SHALL 降低 Markdown 重解析的优先级，使相邻增量之间不产生可感知的输入延迟。

#### Scenario: 增量文本即时可见

- **WHEN** 助手正在流式输出，`message_delta` 连续到达
- **THEN** 新增文本随每次增量即时出现在气泡中，不被 Markdown 解析阻塞

#### Scenario: 渲染耗时受控

- **WHEN** 单条消息已积累较长文本并持续追加
- **THEN** Markdown 解析按低优先级调度，单位时间内的重解析次数少于增量到达次数

### Requirement: 渲染样式完整性

对话区 SHALL 为 Markdown 产出的全部常见元素提供样式，不得依赖浏览器默认样式导致排版不成形。

#### Scenario: 标题层级有明确视觉区分

- **WHEN** 消息包含 `##` 到 `####` 的标题
- **THEN** 各级标题的字号与字重随层级递减，且与正文有明确区分

#### Scenario: 引用块、分隔线与行内代码有样式

- **WHEN** 消息包含 `> 引用`、`---` 与 `` `code` ``
- **THEN** 引用块左侧有竖线缩进、分隔线为水平细线、行内代码有背景色与等宽字体

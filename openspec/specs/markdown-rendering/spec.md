# markdown-rendering Specification

## Purpose
TBD - created by archiving change add-rich-markdown-rendering. Update Purpose after archive.
## Requirements
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
- **THEN** 该处不渲染 `<a>` 元素（连 `href=""` 的空链接也不留，而只显示可点击外观被去掉的纯文本）
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

### Requirement: mermaid 围栏渲染

对话区 SHALL 把语言标记为 mermaid 的围栏代码块渲染为图，SHALL NOT 以代码块形式显示其源码。

#### Scenario: 合法图渲染为 SVG

- **WHEN** 消息包含语言标记为 mermaid 的围栏，且内容是一段合法的流程图定义
- **THEN** 该位置渲染出 SVG 图
- **AND** 页面上不再出现该图的源码文本

#### Scenario: 其他语言的围栏不受影响

- **WHEN** 同一条消息还包含语言标记为 java 的围栏
- **THEN** 该围栏仍按带高亮的代码块渲染

### Requirement: mermaid 运行时按需加载

系统 SHALL 仅在消息中出现 mermaid 围栏时才加载 mermaid 运行时，SHALL NOT 在首屏或其他无关路径下加载它。

#### Scenario: 没有围栏时不加载

- **WHEN** 会话中不存在任何 mermaid 围栏
- **THEN** 不产生任何 mermaid 相关资源的网络请求

#### Scenario: 出现围栏才加载

- **WHEN** 消息中首次出现 mermaid 围栏且围栏已闭合
- **THEN** 此时才动态载入 mermaid 运行时并渲染

### Requirement: mermaid 图使用恒定深色配色

mermaid 图 SHALL 使用深色配色，SHALL NOT 随应用主题切换而改变，以与对话区现有代码块（`github-dark`）的观感保持一致。

#### Scenario: 浅色主题下仍为深色

- **WHEN** 应用处于浅色主题且消息包含 mermaid 围栏
- **THEN** 该图仍以深色配色渲染，与其上方代码块观感一致

#### Scenario: 切换主题后已有图不重绘

- **WHEN** 用户在会话已渲染出图之后切换应用主题
- **THEN** 已渲染的图保持不变，不触发重新渲染

### Requirement: mermaid 渲染的安全配置

系统 SHALL 以 `securityLevel: 'strict'` 初始化 mermaid，使标签内容经消毒后才渲染；SHALL NOT 采用会禁用标签内 HTML 的配置——那会让标签里的换行标记退化成字面文本，而项目自己的图示规范恰恰在标签里使用该标记换行。

#### Scenario: 标签内的脚本被消毒

- **WHEN** 图中某个节点标签包含 script 标签
- **THEN** 渲染出的 SVG 中不存在可执行的 script 元素

#### Scenario: 标签内的换行标记仍然生效

- **WHEN** 图中某个节点标签包含换行标记
- **THEN** 该标签按多行渲染，而不是把标记当字面文本显示

### Requirement: mermaid 仅在整个围栏闭合后渲染

流式输出期间，对话区 SHALL NOT 对尚未闭合的 mermaid 围栏发起渲染；未闭合期间该位置按代码块显示源码。

#### Scenario: 流式期间显示源码而非半张图

- **WHEN** mermaid 围栏已经开始但结束标记尚未到达
- **THEN** 该位置显示源码文本，不显示图，也不出现错误提示

#### Scenario: 闭合后只渲染一次

- **WHEN** 结束标记到达且围栏内容随后不再变化
- **THEN** 该位置渲染为图，且同一张图不因后续无关增量而被反复重渲染

### Requirement: mermaid 渲染失败的兜底

渲染失败、语法非法或运行时加载失败时，对话区 SHALL 在该位置显示**原始 mermaid 源码块与一行错误提示**；SHALL NOT 白屏，SHALL NOT 影响同一条消息中其余内容的渲染。

#### Scenario: 语法非法时显示源码与提示

- **WHEN** mermaid 围栏内容语法非法
- **THEN** 该位置显示原始源码块与一行错误提示
- **AND** 同一条消息里的表格、代码块等内容仍正常渲染

#### Scenario: 运行时加载失败时同样兜底

- **WHEN** mermaid 运行时加载失败（离线或资源取不到）
- **THEN** 该位置降级显示源码块与提示
- **AND** 不抛出未捕获异常

### Requirement: mermaid 图的容器与可访问性

图 SHALL 渲染在可横向滚动的容器内并带无障碍标签，SHALL NOT 撑破消息气泡。

#### Scenario: 宽图横向滚动

- **WHEN** 图的内容宽度超过消息气泡可用宽度
- **THEN** 图容器出现横向滚动条
- **AND** 气泡宽度不超出其上限，页面不出现横向滚动

#### Scenario: 图带无障碍标签

- **WHEN** 图渲染完成
- **THEN** 其容器带有 `role="img"` 与非空的 `aria-label`


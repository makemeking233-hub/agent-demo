## ADDED Requirements

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

## MODIFIED Requirements

### Requirement: 对话区消息渲染

系统 SHALL 在中间对话区渲染用户/助手消息与工具调用/权限卡片，并以 DeepSeek Harness 风格样式呈现。消息正文 SHALL 按 GFM（GitHub Flavored Markdown）方言渲染，覆盖标题、段落、列表、表格、引用块、分隔线、行内代码、围栏代码块（含语法高亮）、数学公式与图片；原始 HTML SHALL NOT 被渲染。

#### Scenario: 助手 markdown 渲染

- **WHEN** 助手消息包含 markdown 文本
- **THEN** 对话区用 Markdown 渲染显示（含行内代码、粗体、代码块）
- **AND** 同一条消息中的表格渲染为 `<table>`、公式渲染为 KaTeX、带语言标记的代码块带语法高亮、图片语法渲染为 `<img>`

#### Scenario: 工具调用卡片三态

- **WHEN** 一轮中出现工具调用
- **THEN** 对话区在 tool_call_start 时渲染"执行中"卡片，tool_call_end 时更新为"完成/失败"卡片，并显示耗时与结果

#### Scenario: 原始 HTML 不渲染

- **WHEN** 消息包含 `<script>alert(1)</script>` 等原始 HTML
- **THEN** 该段以纯文本形式显示，DOM 中不出现被注入的元素，脚本不执行

## ADDED Requirements

### Requirement: 本地文件内容读取接口

系统 SHALL 提供 `GET /api/fs/raw?path=<路径>` 以字节流形式返回指定本地文件内容，供对话区渲染本地图片。该端点 SHALL 复用 `/api/fs/**` 的既有安全边界：先做 trusted-host 鉴权，再把路径解析为 `toRealPath()` 并强制落在 `$HOME` 子树内，否则返回 `403` 且不执行任何 IO。单文件大小 SHALL 上限 16 MiB。

#### Scenario: 读取家目录内的图片

- **WHEN** 客户端请求 `GET /api/fs/raw?path=C:\Users\me\docs\arch.png` 且该文件存在、位于家目录内
- **THEN** 服务端返回 `200`，响应体为该文件字节，`Content-Type` 为 `image/png`

#### Scenario: 路径逃逸被挡

- **WHEN** 客户端请求 `GET /api/fs/raw?path=C:\Users\me\..\..\Windows\win.ini`
- **THEN** 服务端经 `toRealPath()` 解析后判断不在家目录子树，返回 `403`，响应体 `{"error": "path_outside_home"}`
- **AND** 不返回该文件任何字节

#### Scenario: 目录路径被拒

- **WHEN** 客户端请求的目标路径是一个目录而非文件
- **THEN** 服务端返回 `400`，响应体 `{"error": "not_a_file"}`

#### Scenario: 文件不存在

- **WHEN** 客户端请求家目录内一个不存在的路径
- **THEN** 服务端返回 `404`，响应体 `{"error": "not_found"}`

#### Scenario: 超过大小上限

- **WHEN** 目标文件大于 16 MiB
- **THEN** 服务端返回 `413`，响应体 `{"error": "file_too_large"}`
- **AND** 不把该文件读入内存

### Requirement: 本地文件内容接口的类型白名单与响应头加固

`GET /api/fs/raw` SHALL 仅返回**图片类型白名单**内的内容（`image/png`、`image/jpeg`、`image/gif`、`image/webp`、`image/avif`、`image/bmp`、`image/x-icon`、`image/svg+xml`），白名单外的扩展名 SHALL 返回 `415`。每个 `200` 响应 SHALL 带 `X-Content-Type-Options: nosniff` 与 `Content-Disposition: inline`；`image/svg+xml` 响应 SHALL 额外带 `Content-Security-Policy: sandbox`，使该 URL 被直接访问时其中的脚本无法执行。

#### Scenario: 非白名单类型被拒

- **WHEN** 客户端请求家目录内一个 `.html` 文件
- **THEN** 服务端返回 `415`，响应体 `{"error": "unsupported_media_type"}`
- **AND** 不返回该文件字节，因此该文件不会以 `text/html` 在同源下被打开

#### Scenario: SVG 带 sandbox 且仍能作为图片显示

- **WHEN** 客户端请求一个 `.svg` 文件
- **THEN** 响应 `Content-Type` 为 `image/svg+xml` 且带 `Content-Security-Policy: sandbox`
- **AND** 该 URL 用在 `<img src>` 中时图片正常显示

#### Scenario: 每个成功响应都带 nosniff

- **WHEN** 客户端成功读取白名单内的任意图片
- **THEN** 响应头包含 `X-Content-Type-Options: nosniff`

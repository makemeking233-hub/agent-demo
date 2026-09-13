# `2026-09-13-rich-markdown/` — 测试用例清单

> 用例编号沿用 `MessageBubble.markdown.test.tsx` 与 `FsControllerRawTest` / `FsControllerRawHttpTest` 的实际描述（it 名 / @Test 方法名），便于回溯源码。
> 优先级：P0 = 核心安全边界 / 核心价值；P1 = 防御性回归；P2 = 体验细节。

## 1. 后端业务语义（`FsControllerRawTest`，15 例）

### 1.1 正常路径（3 例）

| 编号 | 测试方法 | 前置 | 步骤 | 预期 | 优先级 |
|---|---|---|---|---|---|
| BR-01 | `rawReturnsImageBytesWithinHome` | `@TempDir` home 下写 `arch.png`（7 字节 "PNGDATA"） | `controller.raw(home/arch.png)` | 200 OK / `Content-Type: image/png` / body 字节等于原始内容 | P0 |
| BR-02 | `rawMapsWhitelistedExtensionsToMimeTypes` | 9 种扩展名分别落盘（png/jpg/jpeg/gif/webp/avif/bmp/ico/svg） | 对每种扩展名各调一次 | 200 OK + 对应 MIME（image/png、image/jpeg、image/jpeg、image/gif、image/webp、image/avif、image/bmp、image/x-icon、image/svg+xml） | P0 |
| BR-03 | `rawAcceptsUppercaseExtension` | home 下写 `ARCH.PNG`（大写扩展名） | `controller.raw(home/ARCH.PNG)` | 200 OK / `Content-Type: image/png`（大小写不敏感） | P1 |

### 1.2 安全边界（3 例）

| 编号 | 测试方法 | 前置 | 步骤 | 预期 | 优先级 |
|---|---|---|---|---|---|
| BR-04 | `rawRejectsPathOutsideHome` | `@TempDir outsideHome` 下写 `secret.png` | 请求家目录外的文件 | 403 Forbidden / `{"error":"path_outside_home"}` | P0 |
| BR-05 | `rawRejectsDotDotEscape` | home 下写 `arch.png` | 构造 `home/../evil.png`（归一化后必落 home 外） | 403 Forbidden / `{"error":"path_outside_home"}` | P0 |
| BR-06 | `rawRejectsRelativePath` | — | `controller.raw("relative/pic.png")` | 400 Bad Request / `{"error":"path_not_absolute"}`（不是 403，区分「绝对路径但越界」与「相对路径无法定位」） | P1 |

### 1.3 错误分支（6 例）

| 编号 | 测试方法 | 前置 | 步骤 | 预期 | 优先级 |
|---|---|---|---|---|---|
| BR-07 | `rawReturns404ForMissingFile` | home 下不存在 `nope.png` | `controller.raw(home/nope.png)` | 404 Not Found / `{"error":"path_not_found"}` | P0 |
| BR-08 | `rawReturns400ForDirectory` | home 下创建子目录 `a-folder` | `controller.raw(home/a-folder)` | 400 Bad Request / `{"error":"not_a_file"}` | P1 |
| BR-09 | `rawReturns413ForOversizeFile` | 用 `RandomAccessFile.setLength(MAX_RAW_BYTES + 1)` 创建 16MiB+1 字节的稀疏文件（不实际写盘） | `controller.raw(home/big.png)` | 413 Payload Too Large / `{"error":"file_too_large"}` | P0 |
| BR-10 | `rawReturns415ForNonWhitelistedExtension` | home 下写 `evil.html`，内容为 `<script>alert(1)</script>` | `controller.raw(home/evil.html)` | 415 Unsupported Media Type / `{"error":"unsupported_media_type"}`（不返回字节，避免同源存储型 XSS） | P0 |
| BR-11 | `rawReturns415ForFileWithoutExtension` | home 下写 `README`（无扩展名） | `controller.raw(home/README)` | 415 Unsupported Media Type / `{"error":"unsupported_media_type"}` | P1 |

### 1.4 响应头加固（4 例）

| 编号 | 测试方法 | 前置 | 步骤 | 预期 | 优先级 |
|---|---|---|---|---|---|
| BR-12 | `rawSetsNosniffOnSuccess` | home 下写 `arch.png` | `controller.raw(home/arch.png)` | 响应头 `X-Content-Type-Options: nosniff`（防 MIME 嗅探） | P0 |
| BR-13 | `rawSetsInlineContentDisposition` | home 下写 `arch.png` | `controller.raw(home/arch.png)` | `Content-Disposition: inline; ...; filename="arch.png"`（以 inline 形式给出，便于浏览器内嵌显示） | P1 |
| BR-14 | `rawSetsSandboxCspForSvg` | home 下写 `arch.svg`（合法 SVG 内容） | `controller.raw(home/arch.svg)` | 200 OK / `Content-Type: image/svg+xml` / `Content-Security-Policy: sandbox`（直接访问该 URL 时脚本无法执行；用 `<img src>` 嵌入不受影响） | P0 |
| BR-15 | `rawDoesNotSetSandboxCspForPng` | home 下写 `arch.png` | `controller.raw(home/arch.png)` | PNG 响应**不带** `Content-Security-Policy`（sandbox 只对 SVG 生效） | P1 |

## 2. 后端 HTTP 层（`FsControllerRawHttpTest`，5 例）

> 用 `WebTestClient.bindToController` 起独立 WebFlux 装配；验证经容器序列化**之后**的响应报文。trusted-host 过滤器不在此装配内，由既有 spec 与 `TrustedHostFilter` 测试覆盖。

| 编号 | 测试方法 | 前置 | 步骤 | 预期 | 优先级 |
|---|---|---|---|---|---|
| HT-01 | `servesPngWithHardenedHeadersOverHttp` | home 下写 `arch.png`（"PNGDATA"） | GET `/api/fs/raw?path=<home/arch.png>` | 200 OK + `Content-Type: image/png` + `X-Content-Type-Options: nosniff` + 无 `Content-Security-Policy` + `Content-Disposition` 以 `inline` 开头 + body 字节等于 "PNGDATA" | P0 |
| HT-02 | `servesSvgWithSandboxCspOverHttp` | home 下写 `arch.svg` | GET `/api/fs/raw?path=<home/arch.svg>` | 200 OK + `Content-Type: image/svg+xml` + `Content-Security-Policy: sandbox`（CSP 头未被容器过滤） | P0 |
| HT-03 | `escapesNonAsciiFilenameInContentDisposition` | home 下写中文文件名 `架构图.png` | GET `/api/fs/raw?path=<home/架构图.png>` | 200 OK + `Content-Disposition` 含 `filename*=UTF-8''`（RFC 5987 编码，否则中文名被按 ISO-8859-1 写坏） | P0 |
| HT-04 | `rejectsPathOutsideHomeOverHttp` | `@TempDir outsideHome` 下写 `outside.png` | GET `/api/fs/raw?path=<outside/outside.png>` | 403 Forbidden + body `$.error === "path_outside_home"`（JSON 序列化后路径仍落在 body 里） | P0 |
| HT-05 | `rejectsNonWhitelistedTypeOverHttp` | home 下写 `evil.html` | GET `/api/fs/raw?path=<home/evil.html>` | 415 Unsupported Media Type + body `$.error === "unsupported_media_type"` | P0 |

## 3. 前端富 Markdown（`MessageBubble.markdown.test.tsx`，24 例 / 7 组）

> 用例编号沿用源码 `it()` 描述。

### 3.1 GFM 表格（4 例）

| 编号 | 测试描述 | 前置 | 步骤 | 预期 | 优先级 |
|---|---|---|---|---|---|
| GFM-T01 | 把表格渲染为 table 元素 | 标准 GFM 表格 markdown | `render(<MessageBubble role="assistant" text={TABLE} />)` | 存在 `<table>` / 表头 `<th>` 3 个 / 表头文本 "维度2PCTCC" / 数据行 `<tr>` 2 个 | P0 |
| GFM-T02 | 不把分隔行原样显示出来 | 同上 | 同上 | DOM 文本不含 `\|---\|` 原文、不含 `\| 维度 \|` 原文（GFM 表格语法不残留为竖线文本） | P0 |
| GFM-T03 | 表格外包一层横向滚动容器 | 同上 | 同上 | `<table>` 父元素是 `<div>` 且 className 含 `tableWrap`（列数过多时容器横向滚动而非撑破气泡） | P1 |
| GFM-T04 | 未闭合的表格按普通段落渲染且不抛错 | `\| a \| b \|\n\n后续段落正常`（缺分隔行） | `render(...)` | 不存在 `<table>` / `screen.getByText("后续段落正常")` 在 DOM 里（语法不成立的「类表格」按段落降级） | P1 |

### 3.2 GFM 其他扩展（3 例）

| 编号 | 测试描述 | 前置 | 步骤 | 预期 | 优先级 |
|---|---|---|---|---|---|
| GFM-O01 | 渲染删除线为 del | `~~已废弃~~` | `render(...)` | 存在 `<del>` / 文本 "已废弃" | P0 |
| GFM-O02 | 渲染任务列表为勾选框 | `- [x] 已完成\n- [ ] 待办` | `render(...)` | 存在 `<input type="checkbox">` 2 个 / 第一个 `checked === true` / 第二个 `checked === false` | P1 |
| GFM-O03 | 把裸 URL 渲染为链接 | `见 https://example.com/path 说明` | `render(...)` | 存在 `<a>` / `href === "https://example.com/path"` | P1 |

### 3.3 代码高亮（2 例）

| 编号 | 测试描述 | 前置 | 步骤 | 预期 | 优先级 |
|---|---|---|---|---|---|
| HL-01 | 对带语言标记的代码块做高亮 | `` ```java\npublic class A {}\n``` `` | `render(...)` | 存在 `<pre><code>` / code 类名匹配 `hljs` / 存在 token span / 存在 `<span class="hljs-keyword">`（关键字被包成 span 才算高亮成功） | P0 |
| HL-02 | 未注册语言降级为纯代码块且不抛错 | `` ```brainfuck\n+++\n``` `` | `render(...)` | 存在 `<pre><code>` / code 文本含 `+++` / **`<span>` 数量为 0**（降级判据是「没有 token span」，不是「没有 hljs 类」—— `rehype-highlight` 对每个 `pre>code` 都会无条件加 `hljs` 类） | P1 |

### 3.4 数学公式（3 例）

| 编号 | 测试描述 | 前置 | 步骤 | 预期 | 优先级 |
|---|---|---|---|---|---|
| MX-01 | 把块级公式渲染为 KaTeX | `$$E = mc^2$$` | `render(...)` + `await waitFor(...)` | 存在 `.katex` 元素 / DOM 文本不含 `$$`（动态 import 落地后渲染为 KaTeX） | P0 |
| MX-02 | 把行内公式渲染为 KaTeX | `质能方程 $E = mc^2$ 成立` | `render(...)` + `await waitFor(...)` | 存在 `.katex` / DOM 文本含 "质能方程" 与 "成立"（公式周围普通文本不受影响） | P0 |
| MX-03 | 非法公式降级为原文且不中断其余渲染 | `$$\frac{1}{$$\n\n\| a \| b \|\n\|---\|---\|\n\| 1 \| 2 \|` | `render(...)` + `await waitFor(...)` | 同消息里的 `<table>` 仍渲染 / DOM 文本含 "frac"（KaTeX 解析失败时降级显示原文，不白屏） | P0 |

### 3.5 图片（5 例）

| 编号 | 测试描述 | 前置 | 步骤 | 预期 | 优先级 |
|---|---|---|---|---|---|
| IMG-01 | 远程图片直连 | `![架构图](https://example.com/a.png)` | `render(...)` | 存在 `<img>` / `src === "https://example.com/a.png"` / `alt === "架构图"` | P0 |
| IMG-02 | Windows 绝对路径转 /api/fs/raw | `![图](C:\Users\me\arch.png)` | `render(...)` | 存在 `<img>` / `src === "/api/fs/raw?path=" + encodeURIComponent("C:\\Users\\me\\arch.png")` | P0 |
| IMG-03 | 含中文与空格的路径经 URI 编码后转 /api/fs/raw | `![图](<C:\Users\me\我的 文档\a.png>)`（含空格须尖括号形式） | `render(...)` | 存在 `<img>` / `src === "/api/fs/raw?path=" + encodeURIComponent("C:\\Users\\me\\我的 文档\\a.png")` | P0 |
| IMG-04 | 加载失败时显示 alt 占位而非碎图 | `![架构图](https://example.com/a.png)` | `fireEvent.error(img)` | 不存在 `<img>` / DOM 文本含 "架构图"（不显示浏览器默认碎图图标） | P0 |
| IMG-05 | 无法定位的相对路径直接降级为占位 | `![相对图](./arch.png)` | `render(...)` | 不存在 `<img>` / DOM 文本含 "相对图"（前端无可靠工作区上下文，相对路径不解析） | P1 |

### 3.6 安全基线（4 例）

| 编号 | 测试描述 | 前置 | 步骤 | 预期 | 优先级 |
|---|---|---|---|---|---|
| SEC-01 | 原始 script 标签不注入 DOM、不执行 | `<script>alert(1)</script>` | `render(...)` | 不存在 `<script>` 元素 / DOM 文本含字面量 `<script>alert(1)</script>`（标签当纯文本展示，不执行） | P0 |
| SEC-02 | 原始 HTML 的事件属性不产生可执行元素 | `<img src=x onerror="alert(1)">` | `render(...)` | 不存在 `<img>` 元素 / DOM 文本含 "onerror"（`onerror` 不会被解析为属性） | P0 |
| SEC-03 | `javascript:` 协议不产生可点击链接 | `[点我](javascript:alert(1))` | `render(...)` | **不存在** `<a>` 元素（连 `href=""` 的空链接也不留——空 href 仍是可点击元素，点下去整页重载）/ DOM 文本含 "点我" | P0 |
| SEC-04 | 外链带 target 与 rel 安全属性 | `[文档](https://example.com)` | `render(...)` | 存在 `<a>` / `href === "https://example.com"` / `target === "_blank"` / `rel === "noopener noreferrer"` | P0 |

### 3.7 流式渲染时序（3 例，回归护栏）

> 这 3 例**不是**红→绿驱动：jsdom 无可观测调度差异，真实收益需浏览器大消息实测。本组把「增量即时可见 / 快速追加不丢内容 / 长文混排不崩」钉死，防后续优化引入卡顿或内容丢失。详见 `test-design.md §5.2`。

| 编号 | 测试描述 | 前置 | 步骤 | 预期 | 优先级 |
|---|---|---|---|---|---|
| STR-01 | 单次追加的文本立即出现在 DOM 中 | 初值 `"第一段"` | `rerender(<MessageBubble text="第一段，第二段" />)` | DOM 文本含 "第一段，第二段"（不被 Markdown 解析阻塞） | P1 |
| STR-02 | 快速连续追加后内容完整，取最后一次的值 | 初值 `"A"` | 依次 `rerender` 5 次：AB / ABC / ABCD / ABCDE / ABCDEF | DOM 文本含 "ABCDEF"（快速连发不丢内容） | P1 |
| STR-03 | 长文本与表格、代码块混排时完整渲染 | 40 段段落 + 表格 + `\`\`\`java\`\`\`` 代码块 | `render(...)` | 存在 `<table>` / 存在 `<pre><code>` / DOM 文本含 "第 39 段落文字。" / 含 "一致性"（混排不崩） | P1 |

## 4. 落地情况

| 关注点 | 用例数 | 全部落地 | 全部通过 | 备注 |
|---|---:|:---:|:---:|---|
| 后端业务语义（`FsControllerRawTest`） | 15 | 是 | 是 | 见 `test-report.md §1` |
| 后端 HTTP 层（`FsControllerRawHttpTest`） | 5 | 是 | 是 | 见 `test-report.md §1` |
| 前端 GFM 表格 | 4 | 是 | 是 | GFM-T01 ~ T04 |
| 前端 GFM 其他扩展 | 3 | 是 | 是 | GFM-O01 ~ O03 |
| 前端代码高亮 | 2 | 是 | 是 | HL-01、HL-02 |
| 前端数学公式 | 3 | 是 | 是 | MX-01 ~ MX-03 |
| 前端图片 | 5 | 是 | 是 | IMG-01 ~ IMG-05 |
| 前端安全基线 | 4 | 是 | 是 | SEC-01 ~ SEC-04 |
| 前端流式时序 | 3 | 是 | 是 | STR-01 ~ STR-03（回归护栏） |
| **合计** | **44** | 是 | 是 | — |

## 5. 回归基线

| 项 | 改动前 | 改动后 | 增量 |
|---|---:|---:|---:|
| 后端 Java 测试（agent-web） | 182 | **203** | +21（16 + 5） |
| 前端测试文件 | 18 | **19** | +1 |
| 前端用例数 | 143 | **167** | +24 |
| `npx tsc --noEmit` 错误数 | 27 | **7** | −20（与本 change 无关：补 `vite-env.d.ts` 后从 27 降到 7；详见 `AGENTS.md §2.7.7`） |

## 6. 用例与 spec 对照

| spec Requirement | spec Scenario | 覆盖用例 |
|---|---|---|
| GFM 表格渲染 | 标准表格渲染为表格元素 | GFM-T01、T02 |
| GFM 表格渲染 | 宽表格横向滚动不撑破气泡 | GFM-T03 |
| GFM 表格渲染 | 未闭合的表格按纯文本显示 | GFM-T04 |
| GFM 其他扩展渲染 | 删除线与任务列表渲染 | GFM-O01、O02 |
| GFM 其他扩展渲染 | 裸 URL 自动成为链接 | GFM-O03 |
| 数学公式渲染 | 块级公式渲染 | MX-01 |
| 数学公式渲染 | 非法公式降级不中断渲染 | MX-03 |
| 数学公式渲染（行内） | （非 spec 强制，T 顺手加） | MX-02 |
| 代码块语法高亮 | 已知语言高亮 | HL-01 |
| 代码块语法高亮 | 未知语言降级为纯代码块 | HL-02 |
| 消息内图片渲染 | 远程图片渲染 | IMG-01 |
| 消息内图片渲染 | 本地图片经接口渲染 | IMG-02、IMG-03 |
| 消息内图片渲染 | 图片加载失败显示 alt 占位 | IMG-04、IMG-05 |
| 消息内链接安全基线 | 外链带安全属性 | SEC-04 |
| 消息内链接安全基线 | 危险协议不成为可点击链接 | SEC-03 |
| 原始 HTML 不渲染 | script 标签被转义 | SEC-01 |
| 原始 HTML 不渲染 | 事件属性不执行 | SEC-02 |
| 流式期间的渲染时序 | 增量文本即时可见 | STR-01、STR-02 |
| 流式期间的渲染时序 | 渲染耗时受控 | STR-03 |
| 本地文件内容读取接口 | 读取家目录内的图片 | BR-01、HT-01 |
| 本地文件内容读取接口 | 路径逃逸被挡 | BR-04、BR-05、HT-04 |
| 本地文件内容读取接口 | 目录路径被拒 | BR-08 |
| 本地文件内容读取接口 | 文件不存在 | BR-07 |
| 本地文件内容读取接口 | 超过大小上限 | BR-09 |
| 本地文件内容读取接口（相对路径） | （spec 未强制，T 顺手加） | BR-06 |
| 类型白名单与响应头加固 | 非白名单类型被拒 | BR-10、HT-05 |
| 类型白名单与响应头加固 | SVG 带 sandbox 且仍能作为图片显示 | BR-14、HT-02 |
| 类型白名单与响应头加固 | 每个成功响应都带 nosniff | BR-12、HT-01 |
| 大小写不敏感 | （spec 未强制，T 顺手加） | BR-03 |
| 中文文件名 RFC 5987 | （spec 未强制，HTTP 层验证） | HT-03 |
| PNG 不带 sandbox | （spec 未强制，T 顺手加） | BR-15 |
| 无扩展名被拒 | （spec 未强制，T 顺手加） | BR-11 |
| Content-Disposition inline | （spec 已声明，T 加测试钉死） | BR-13 |
| MIME 映射完整性 | （spec 未强制，T 加测试钉死） | BR-02 |

> 表来源：spec 见 `openspec/changes/add-rich-markdown-rendering/specs/markdown-rendering/spec.md` 与 `openspec/changes/add-rich-markdown-rendering/specs/web-ui/spec.md`。

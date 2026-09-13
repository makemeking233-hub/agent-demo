## Why

Web 对话区的 Markdown 渲染器 `react-markdown@10` 是**裸用**的（`MessageBubble.tsx` 只写了 `<ReactMarkdown>{props.text}</ReactMarkdown>`，未挂任何 remark/rehype 插件），因此只支持 CommonMark。而助手实际大量输出 GFM 表格与 LaTeX 公式（`~/.dsh/AGENTS.md §3` 更要求公式一律用双美元块），结果是：

- 表格退化成一行 `| 维度 | 2PC | TCC |` 竖线原文，完全不可读（截图即实例）；
- 公式退化成 `\frac{...}` 原文；
- 代码块无语法高亮；
- 图片无法引用本地文件，架构图只能贴路径字符串。

这不是"少个锦上添花的功能"，而是**渲染层把模型的结构化输出降级成了半成品文本**。

## What Changes

- 挂 `remark-gfm`：GFM 表格、删除线、任务列表、自动链接。
- 挂 `remark-math` + `rehype-katex`：双美元块公式与行内公式渲染为 KaTeX。
- 挂 `rehype-highlight`：代码块语法高亮，**按需注册语言**（不引入全量 5.25 MB）。
- 图片渲染：远程 URL 直接加载；本地文件经**新增** `GET /api/fs/raw?path=` 读取，沿用现有 `$HOME` 子树安全边界；`.svg` 亦走该接口以图片形式引用。
- 排版补齐：`h1`–`h6`、`table`（边框/表头/横向滚动）、`img`（最大宽度/圆角）、`blockquote`、`hr` 的样式。
- 流式渲染时序：用 React 18 `useDeferredValue` 降低 Markdown 解析优先级，避免每个 `message_delta` 都重解析全量文本。
- 安全基线：**保持 raw HTML 关闭**（不引入 `rehype-raw`），外链统一 `target="_blank"` + `rel="noopener noreferrer"`。

## Capabilities

### New Capabilities

- `markdown-rendering`: 对话区富 Markdown 渲染能力——GFM 方言支持、数学公式、代码高亮、图片（远程/本地）、外链安全基线、流式渲染时序。

### Modified Capabilities

- `web-ui`: 「对话区消息渲染」Requirement 扩展（原仅承诺行内代码/粗体/代码块，现须覆盖表格、公式、高亮、图片）；并在 `/api/fs/**` 下新增 `GET /api/fs/raw` 本地文件内容接口。

## Impact

| 面 | 内容 |
|---|---|
| 前端代码 | `MessageBubble.tsx`（挂插件、自定义 `components` 映射）、`MessageBubble.module.css`（排版补齐）、新增 Markdown 渲染子模块与图片 URL 解析工具 |
| 后端代码 | `FsController`（新增 `GET /api/fs/raw`）、`HomePathGuard`（复用，不新增边界逻辑） |
| 依赖 | 新增 `remark-gfm`、`remark-math`、`rehype-katex`、`katex`、`rehype-highlight`（均为前端运行时依赖）；`agent-web` 打包体积与 PWA 预缓存清单会变化 |
| 规范 | `openspec/specs/markdown-rendering/`（新）、`openspec/specs/web-ui/spec.md`（delta） |
| 测试 | `MessageBubble.test.tsx` 扩充；新增 `FsController` 的 `raw` 端点用例（含 403 边界）；`npx tsc --noEmit` 错误数须保持 27 不变 |
| 风险 | 前端新增依赖改变构建产物 → 必须重新构建 `static/` 才对用户可见（本项目已有 `web-build` 约定）；KaTeX 字体需处理懒加载 |

## Out of Scope

下列内容经探索阶段确认**不在本 change**，各自另开 change：

- **mermaid 图渲染**（懒加载 + 主题联动 + 失败兜底）→ `add-mermaid-diagrams`。
- **内联 SVG**（`<svg>` 标签直接嵌入）→ 需放开 raw HTML，与 `ShellTool`/`WebSearchTool` 提示注入链叠加且当前无 CSP，已明确否决；SVG 仅以图片形式引用。
- **echarts / chart.js 等数据图表** → 需自定义围栏语法并修改系统提示词引导模型，属"新增模型能力"，已明确否决。
- 后端 CSP 加固（`Content-Security-Policy` 下发）——独立的安全话题，不夹带在本 change。

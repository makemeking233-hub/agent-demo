# `2026-09-13-rich-markdown/` — 富 Markdown 渲染测试设计

> change: `openspec/changes/add-rich-markdown-rendering/`
> 对应规范：`AGENTS.md §2.6 测试文档组织规范`
> 后端 `GET /api/fs/raw` 设计依据：`openspec/changes/add-rich-markdown-rendering/design.md` D7

## 1. 测试目标

`add-rich-markdown-rendering` 把 Web 对话区从「`react-markdown` 裸用 + 6 条 CSS 规则」升级为「完整富 Markdown 渲染」，覆盖六类能力：

1. **GFM 方言**：表格、删除线、任务列表、裸 URL 自动链接。
2. **数学公式**：`$$...$$` 块级与 `$...$` 行内公式走 KaTeX。
3. **代码块语法高亮**：围栏代码块按语言上色，按需懒加载 KaTeX。
4. **图片**：远程 URL 直连；本地文件经**新增** `GET /api/fs/raw` 读取。
5. **安全基线**：原始 HTML 不渲染、危险协议不成为可点击链接、外链带 `target=_blank` + `rel=noopener noreferrer`。
6. **流式渲染时序**：增量文本逐帧可见，Markdown 重解析走低优先级调度。

测试目的是验证以上六类能力**真实可渲染、可防御、可持续演进**，并把 D1–D8 的设计决策通过测试代码钉死（防回归）。

## 2. 环境与策略

| 维度 | 工具 / 版本 | 备注 |
|---|---|---|
| 后端 | JDK 17 + Spring Boot 3.2 + Maven 3.9 + JUnit 5 + AssertJ | `WebTestClient.bindToController` 起独立装配；不启动完整 Spring 上下文；`@TempDir` 注入 home，**不碰用户真实 `$HOME`** |
| 前端 | Node.js + Vite + Vitest + jsdom + @testing-library/react | `react-markdown@10` 配 `remark-gfm` / `remark-math` / `rehype-highlight` 真渲染；KaTeX 用 `waitFor` 等动态 import 落地 |
| 类型 | `npx tsc --noEmit` | 基线错误数 ≤ 7（2026-09-13 补 `vite-env.d.ts` 后从 27 降下来；详见 `AGENTS.md §2.7.7`） |
| 隔离 | worktree `.worktrees/add-rich-markdown-rendering` | 不污染 main；改动前 worktree 内 `npm install` 拉齐新依赖 |

**测试策略**：

- **TDD 优先**：每个 task 严格按 `tasks.md` 的「测试先红 → 实现 → 测试转绿」节奏。
- **真渲染，不 mock 插件**：本 change 的价值全在「渲染结果对不对」。KaTeX 走 `waitFor`，不 mock `import('katex')`。
- **后端分层测试**：业务语义走 `FsControllerRawTest`（直接调 `controller.raw()` 看 `ResponseEntity`），HTTP 层走 `FsControllerRawHttpTest`（`WebTestClient` 验真实报文）。
- **缺陷驱动测试修正**：实现过程中发现的 6 个问题对应调整测试预期（如「未注册语言降级」改判「无 token span」而非「无 hljs 类」），记录在 test-report 的「缺陷清单」。

## 3. 测试范围

### 3.1 In Scope

| 模块 | 文件 | 用例数 | 优先级 |
|---|---|---:|---|
| 后端业务语义 | `FsControllerRawTest` | 15 | P0（核心安全边界 + 错误分支） |
| 后端 HTTP 层 | `FsControllerRawHttpTest` | 5 | P0（响应头与序列化） |
| 前端富 Markdown | `MessageBubble.markdown.test.tsx` | 24 | P0（核心价值） |

### 3.2 Out of Scope

- **Playwright / Selenium E2E**：本机无 Chrome GUI 跑不动；v0.x 不强求；手测覆盖。
- **真实浏览器性能基准**：jsdom 无可观测调度差异，`useDeferredValue` 的真实收益需在浏览器用大消息实测（见 §6 风险）。
- **mermaid / echarts 渲染**：在 `openspec/changes/add-mermaid-diagrams/` 独立 change。
- **全站 CSP 下发**：独立安全话题，不夹带。

## 4. 测试矩阵

### 4.1 后端

| 维度 | 覆盖点 | 用例数 |
|---|---|---:|
| 正常路径 | 读取家目录内 png / 9 种白名单扩展名映射 / 大写扩展名 | 3 |
| 安全边界 | 路径逃逸（独立 temp + `..` 归一化）/ 相对路径 | 3 |
| 错误分支 | 404 文件不存在 / 400 目录 / 400 相对路径 / 413 超 16MiB（稀疏文件）/ 415 非白名单扩展名 / 415 无扩展名 | 6 |
| 响应头加固 | `nosniff` / `Content-Disposition: inline` / SVG `sandbox` / PNG 不带 `sandbox` | 4 |

### 4.2 前端（24 用例分 7 组）

| 关注点 | 用例数 | 关键断言 |
|---|---:|---|
| GFM 表格 | 4 | `<table>` + 表头列数 + 不含 `\|---\|` 原文 + 横向滚动容器 + 未闭合按段落 |
| GFM 其他扩展 | 3 | `<del>`、任务列表勾选状态、裸 URL → `<a>` |
| 代码高亮 | 2 | java 围栏有 `hljs` 类 + token span + 关键字 span；brainfuck 降级为无 span 的纯代码块 |
| 数学公式 | 3 | 块级 / 行内 KaTeX 渲染 + 定界符不残留；非法公式降级不中断同消息表格 |
| 图片 | 5 | 远程直连；Windows 路径转 `/api/fs/raw`；含中文空格路径 URI 编码；error 事件降级；相对路径降级 |
| 安全基线 | 4 | `<script>` 转义；`onerror` 不注入；`javascript:` 不产生 `<a>`；外链带 `target=_blank` + `rel=noopener noreferrer` |
| 流式时序 | 3 | 单次追加立即可见；快速连续追加取末次值；长文 + 表格 + 代码块混排完整渲染 |

### 4.3 回归基线

| 项 | 改动前 | 改动后 |
|---|---:|---:|
| 后端 Java 测试 | 既有 158（含 `FsControllerTest` 既有 15） | **178**（+15 `FsControllerRawTest` + 5 `FsControllerRawHttpTest`） |
| 前端测试文件 | 18 | 19（新增 `MessageBubble.markdown.test.tsx`） |
| 前端用例数 | 143 | **167**（+24 富 Markdown） |
| `npx tsc --noEmit` 错误数 | 27 | **7**（补 `vite-env.d.ts` 后，与本 change 无关） |

## 5. 策略细则

### 5.1 「真渲染 + DOM 断言」的取舍

为什么不做模块 mock：mock 掉插件等价于「没测」—— `react-markdown` 的价值就是把这些 mdast 节点翻译成 React 元素，mock 后断言的是 mock 自己的返回，不是生产路径。KaTeX 走动态 import，测试里用 `await waitFor(() => container.querySelector('.katex'))` 等真实 import 落地。

### 5.2 T8「流式渲染时序」是回归护栏，不是红→绿

`useDeferredValue` 的作用是降低 Markdown 重解析的**调度优先级**，jsdom 里没有可观测的调度差异，真实收益要靠浏览器里的大消息实测。因此本组**不是红→绿驱动的**——加实现前后都必须全绿，实测两次运行均全绿。T8 的价值在于把以下三条钉死：

1. 增量文本逐帧可见（不被 Markdown 解析阻塞）；
2. 快速连续追加不丢内容（取最后一次的值）；
3. 长文本 + 表格 + 代码块混排不崩。

防止后续为了性能优化引入卡顿、内容丢失或布局抖动。

### 5.3 后端 HTTP 层与业务语义层分工

| 层 | 关心 | 不关心 |
|---|---|---|
| 业务语义（`FsControllerRawTest`） | 状态码、错误码、字节内容、MIME 映射、大小写扩展名 | 响应头在 WebFlux 序列化后是否真发出去 |
| HTTP 层（`FsControllerRawHttpTest`） | 经 `WebTestClient.bindToController` 真实序列化后的 `Content-Type` / `Content-Disposition` / `X-Content-Type-Options` / `Content-Security-Policy` 等头是否落在报文里 | 字节是否等于原始内容 |

**为什么不合并**：实测会踩的坑包括 `Content-Disposition` 带中文文件名被容器拒绝或写成乱码（HTTP 层第 3 用例就是为此存在）；容器可能改写 `Content-Type`；`sandbox` CSP 头可能被某些过滤器过滤掉。两层独立断言才能在回归时定位到底是 controller 逻辑错还是容器序列化错。

### 5.4 不污染用户真实数据

测试严格遵守全局规则 §10：

- 后端 `FsControllerRawTest` / `FsControllerRawHttpTest` 均用 `@TempDir` 注入 home，不指向真实 `$HOME`。
- HTTP 测试用 `WebTestClient.bindToController` 起独立装配，不启动 `@SpringBootTest`，不触发真实应用定时任务、不写会话存档。
- 前端 vitest 跑在 jsdom，无副作用。

## 6. 风险与遗留

| 风险 | 缓解 |
|---|---|
| 主 bundle 体积 +226 kB（gzip 173.61 kB） | D2 把 KaTeX 拆为独立懒加载块（261.76 kB / gzip 77.92 kB），首屏不引入；T5 实测已确认接受 |
| 浏览器实测 `useDeferredValue` 真实收益 | T8 三用例是回归护栏；真实收益要浏览器跑大消息验证 |
| KaTeX 字体 woff2 约 1 MB 进 `dist/assets/` | `vite.config.ts` 默认不预缓存字体；运行时按需下载，不影响 PWA 安装包 |
| SVG 直接访问（不走 `<img>`） | D7 给 SVG 加 `Content-Security-Policy: sandbox`，脚本无法执行 |
| `lowlight common` 34 语言进了主包（`rehype-highlight` 顶层静态 import） | D3 已实测推翻「自选子集省体积」，改回默认 `common` 反而小 51 kB |
| Windows 路径百分号编码（`mdast-util-to-hast` 规范化） | D4 自写 `urlTransform`，判类型前先 `decodeURIComponent`；远程 URL 不解码，避免 `%20` 被还原成空格 |
| `defaultUrlTransform` 看不见反斜杠，会把 `C:\...` 清成空串 | D4 自写 `urlTransform`，不再用默认 |

## 7. DoD（Definition of Done）

- [x] 后端 `mvn -pl agent-web -am test` 全绿（178 用例，含新增 15 + 5）
- [x] 前端 `npx vitest run` 全绿（167 用例 / 19 文件，含新增 24）
- [x] `npx tsc --noEmit` 错误数 ≤ 7（与基线持平，未引入新错误）
- [x] `npm run build` 产出新 hash 的 `index-*.js` / `index-*.css`，KaTeX 字体进 `static/assets/`
- [x] 在构建产物中 grep 特征串（`katex`、`hljs`、`/api/fs/raw`）确认新代码确实进了 bundle
- [x] 把构建产物同步到 `agent-web/target/classes/static/` 后 curl 验三种响应（200/403/415）与响应头
- [x] OpenSpec change `add-rich-markdown-rendering` 已 archive 到 `openspec/changes/archive/`，delta spec 已并入 `openspec/specs/markdown-rendering/` 与 `openspec/specs/web-ui/spec.md`
- [x] `tasks.md` 无未勾选项
- [x] 测试文档四件套完成并归档到 `docs/test-agent-demo/2026-09-13-rich-markdown/`

## 8. 退出标准

当且仅当：

1. 7 节 DoD 全部勾选；
2. 后端与前端构建产物均与设计决策 D1–D8 一致（grep 自检通过）；
3. 主 bundle 增量符合 §6 风险表的预期（主 JS 约 561 kB / gzip 174 kB；KaTeX 独立懒加载块 261 kB / gzip 78 kB）；
4. 测试用例覆盖 §4 矩阵的所有行；

方可进入 OpenSpec archive 阶段（§2.5）。

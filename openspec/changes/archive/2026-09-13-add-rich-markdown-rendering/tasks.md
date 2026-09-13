## 1. 准备与基线

- [x] 1.1 在本 worktree 的 `agent-web/frontend/` 执行 `npm install remark-gfm remark-math katex rehype-highlight`，确认 `package.json` / `package-lock.json` 的变更只落在 `agent-web/frontend/` 内
- [x] 1.2 记录改动前基线：`index-*.js` 与 `index-*.css` 体积、`npx vitest run` 用例数、`npx tsc --noEmit` 错误数（须为 27），写入 change 的备注供 T9 对照
- [x] 1.3 跑一次 `npx vitest run` 确认 worktree 内测试基线全绿（首次需 `npm install` 拉齐既有依赖）

## 2. 后端：本地文件内容接口（TDD）

- [x] 2.1 **测试先红**：新增 `FsControllerRawTest`，覆盖 spec 的全部场景——家目录内图片返回 200 + 正确 `Content-Type`、`..` 逃逸返回 403 `path_outside_home`、目录返回 400 `not_a_file`、不存在返回 404 `not_found`、超 16 MiB 返回 413 `file_too_large`、`.html` 返回 415 `unsupported_media_type`、成功响应带 `X-Content-Type-Options: nosniff`、`.svg` 响应带 `Content-Security-Policy: sandbox`
- [x] 2.2 确认测试如预期失败（`404` 而非 `200`，因为端点尚不存在）
- [x] 2.3 实现 `GET /api/fs/raw`：复用 `HomePathGuard.resolveWithinHome` 做边界校验（trusted-host 校验在前），按扩展名映射 MIME 白名单，校验大小上限后再读字节，设置 `nosniff` / `Content-Disposition: inline` / SVG 的 `sandbox` 头
- [x] 2.4 测试转绿，**commit + push 到 `feat/add-rich-markdown-rendering`**（信息形如 `feat(web): 新增 /api/fs/raw 本地图片读取接口`）

## 3. 前端：GFM 基础渲染与排版样式

- [x] 3.1 **测试先红**：扩充 `MessageBubble.test.tsx`——标准表格渲染为 `<table>` 且页面不含 `|---|` 原文；宽表格容器可横向滚动；未闭合表格按段落渲染不抛错；`~~删除线~~` 渲染为 `<del>`；`- [x]` 渲染为勾选框列表；裸 URL 渲染为 `<a>`
- [x] 3.2 实现：`MessageBubble.tsx` 挂 `remarkPlugins={[remarkGfm]}`
- [x] 3.3 补齐 `MessageBubble.module.css`：`h1`–`h6` 层级化字号字重、`table`（表头底色、单元格边框、`display:block; overflow-x:auto` 的横向滚动容器）、`img`（`max-width:100%`、圆角）、`blockquote`、`hr`、`del`
- [x] 3.4 测试转绿，**commit + push**

## 4. 前端：代码块语法高亮

- [x] 4.1 **测试先红**：`java` 围栏产出带 `hljs` 类名与关键字 `<span>` 的 DOM；未注册语言（如 `brainfuck`）仍渲染为 `<pre><code>` 且不抛错
- [x] 4.2 实现：挂 `rehype-highlight`，**显式注册语言子集**（java / xml / json / yaml / bash / sql / python / javascript / typescript / tsx / css / markdown / diff / properties / docker），并引入配套主题样式（与现有深色 `pre` 配色协调）
- [x] 4.3 测试转绿，**commit + push**

## 5. 前端：公式渲染（懒加载 KaTeX）

- [x] 5.1 **测试先红**：`$$E = mc^2$$` 渲染出 KaTeX 的 DOM 结构（`.katex`）且页面不含 `$$` 原文；行内 `$a^2$` 同样渲染；非法公式 `$$\frac{1}{$$` 降级显示原文且同消息内其余表格/代码块仍正常渲染
- [x] 5.2 实现：挂 `remark-math` 做解析；新增极小的 rehype 插件把数学节点转成 `<span data-tex>` / `<div data-tex>`；新增 `MathBlock` 组件在 `useEffect` 中 `await import('katex')` 与 `await import('katex/dist/katex.min.css')` 后渲染；渲染前显示占位避免布局跳动
- [x] 5.3 测试转绿，**commit + push**
- [x] 5.4 实测主 bundle 增量并与 1.2 基线对照；把数字回填到 `design.md` 的 Open Questions 第 2 条（若增量不可接受，按 D2 同一模式把高亮也改懒加载并记录决策）

## 6. 前端：图片渲染

- [x] 6.1 **测试先红**：`![alt](https://example.com/a.png)` 产出 `src` 为原 URL 的 `<img>`；`![alt](C:\Users\me\我的 文档\a.png)` 产出 `src` 为 `/api/fs/raw?path=...`（校验 URI 编码正确处理中文、空格、反斜杠）；图片 `error` 事件后显示 `alt` 占位与失败提示而非碎图
- [x] 6.2 实现：新增图片 URL 解析工具（远程直连 / 本地与 `file://` 转 `/api/fs/raw` / 相对路径按工作区解析），在 `ReactMarkdown` 的 `components.img` 中接入；实现加载失败兜底组件
- [x] 6.3 测试转绿，**commit + push**

## 7. 前端：安全基线固化

- [x] 7.1 **测试先红**：`<script>alert(1)</script>` 以纯文本显示且 DOM 中无被注入的 `script` 元素；`<img src=x onerror="alert(1)">` 不产出真实 `<img>`；`[点我](javascript:alert(1))` 不产出可点击 `<a href="javascript:...">`；外链同时带 `target="_blank"` 与 `rel="noopener noreferrer"`
- [x] 7.2 实现：在 `components.a` / `components.img` 中做协议白名单与安全属性补齐；确认**未**引入 `rehype-raw`
- [x] 7.3 测试转绿，**commit + push**

## 8. 前端：流式渲染时序

- [x] 8.1 **测试先红**：连续多次推进 `text` prop 模拟流式追加后，断言最终文本完整可见；断言 Markdown 解析走低优先级调度（例如同一批次内的多次更新不触发等量次数的完整重解析）
- [x] 8.2 实现：在 `MessageBubble` 中用 `useDeferredValue` 承载传给 `ReactMarkdown` 的文本
- [x] 8.3 测试转绿，**commit + push**

## 9. 构建、产物与端到端自检

- [x] 9.1 执行 `npm run build`，确认 `static/` 产出新 hash 的 `index-*.js` / `index-*.css`，且 KaTeX 字体等新增资源落在 `static/assets/` 下
- [x] 9.2 在**构建产物**中 grep 特征串（如 `katex`、`hljs`、`/api/fs/raw`）确认新代码确实进了 bundle——防止"改了源码但浏览器仍加载旧 bundle"这类假通过
- [x] 9.3 把构建产物同步到 `agent-web/target/classes/static/`，并 `curl` 校验 `GET /api/fs/raw` 的成功（200 + `Content-Type`）、越界（403）、非白名单（415）三种响应及响应头
- [x] 9.4 记录改动后的 `index-*.js` / `index-*.css` 体积，与 1.2 基线并列成对照表写入测试报告

## 10. 收尾与合并门禁

- [x] 10.1 全量质量门：`mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**`、`npx vitest run`、`npx tsc --noEmit`（错误数须仍为 27）
- [x] 10.2 按 §2.6 补齐本批测试四件套（`docs/test-agent-demo/<日期>-rich-markdown/`）并在 `test-guide.md` §1 登记
- [x] 10.3 执行 `openspec archive add-rich-markdown-rendering`，确认 delta spec 已并入 `openspec/specs/markdown-rendering/` 与 `openspec/specs/web-ui/spec.md`，且 `tasks.md` 无未勾选项
- [x] 10.4 按 AGENTS.md §2.7.5 走合并门禁：同步 `main` 后重跑 10.1 → 合并回 `main` → 在 `main` 上复验 → 通过才 push；随后清理 worktree 与分支

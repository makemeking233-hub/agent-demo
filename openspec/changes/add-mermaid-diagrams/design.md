## Context

对话区正文渲染入口是 `agent-web/frontend/src/components/MarkdownContent.tsx`，上一次 `add-rich-markdown-rendering` 已挂上 `remark-gfm` / `remark-math` / `rehype-highlight`，并用一个自定义 rehype 插件把数学节点换成 `math-inline` / `math-block` 标签、交给懒加载的 `MathNode` 组件渲染。

mermaid 需要走**完全相同的手法**：围栏代码块在 hast 里是 `<pre><code class="language-mermaid">`，要在 rehype 阶段改写成自定义标签，再由 React 组件接管渲染 —— 这样既能在 `components` 映射里精确命中，又不会跟代码高亮的 `span` 抢映射。

mermaid 的实际构建结构（实测 `mermaid@12.0.0`）：

```text
node_modules/mermaid/dist/
├── mermaid.core.mjs      52.1 KB   ← 入口，很小
├── mermaid.esm.mjs       63.8 KB
└── chunks/               19.2 MB   ← 各图型实现，按需动态加载（未压缩）
```

也就是说 **mermaid 自己就做了按图型懒加载**：入口很小，具体图型在解析到对应语法时才拉取。这对我们是利好，但引出一个必须处理的副作用（见 D7）。

## Goals / Non-Goals

**Goals:**

- ```` ```mermaid ```` 围栏渲染成图，不再显示源码。
- 页面没出现围栏时，mermaid 一个字节都不加载。
- 图与现有深色代码块观感一致。
- 流式输出时不拿半张图反复渲染。
- 语法错误/加载失败都有像样的兜底，不白屏。
- 引入 mermaid 后 **PWA 安装包不显著变大**。

**Non-Goals:**

- 数据图表库（echarts / chart.js）——需自定义语法并改系统提示词，属新增模型能力。
- 内联 SVG —— 需放开 raw HTML，已明确否决。
- 图跟随应用主题切换（本次定为恒定深色）。
- 节点点击等交互回调、导出图片、复制源码。
- 后端任何改动（本次纯前端）。

## Decisions

### D1 用 rehype 插件把围栏换成自定义标签，不在 `components.code` 里判别

**选择**：新增 rehype 插件，把 `<pre><code class="language-mermaid">` 整块替换为 `<mermaid-block source="...">`，再在 `components` 里映射到 `MermaidBlock`。

**否决在 `components.code` 里按 className 分支**：`components.code` 会命中**所有** `code` 元素（含行内代码与高亮后的每个 token 容器），要在其中判别并返回一个块级组件，既别扭又容易破坏行内代码的既有行为。而自定义标签方案与上一次 `math-inline` / `math-block` 完全同构，模式一致、互不干扰。

### D2 运行时全懒加载：`await import("mermaid")`

**选择**：`MermaidBlock` 在 `useEffect` 里动态导入 mermaid，导入前显示源码占位。

**理由**：mermaid 入口虽只有 52 KB，但它是 CJS/ESM 混合且带大量依赖，静态 import 会进主 bundle。懒加载把这份代价推迟到"真的出现图"时，与 D2 之于 KaTeX 的理由相同。

### D3 恒定深色配色

**选择**：`theme: "dark"` 写死，不订阅应用主题。

**理由**：对话区的代码块用 `github-dark` 且**不随主题切换变化**（既有事实）。若图跟随主题，浅色模式下会出现"浅色图 + 深色代码块"同屏，比"深色图 + 深色代码块"更突兀。选前者之外的另一种"一致"，即与代码块一致。

### D4 `securityLevel: 'strict'`，但**不**关闭 `htmlLabels`

**选择**：`securityLevel: "strict"`。

**为什么不顺手关掉 `htmlLabels`**：关掉它能让标签退化成纯 SVG `<text>`，表面看更安全，但**标签里的换行标记会变成字面文本**。而项目自己的图示规范（AGENTS.md §2.4「允许」表）恰恰明确在 flowchart 节点标签里用该标记换行——关掉会让项目自己写的图全部走形。

`securityLevel: 'strict'` 下 mermaid 会用 DOMPurify 消毒标签内容，换行标记保留、脚本被剥离，是正确的那一档。

### D5 只在围栏闭合后渲染，且同一段源码只渲染一次

**选择**：`MermaidBlock` 拿到的 `source` 是**已闭合**的围栏内容；用一个 ref 记住"已渲染过的源码字符串"，相同则不重复渲染。

**理由**：流式期间未闭合的围栏若送去渲染，mermaid 会抛语法错误，于是每来一个增量就"报错 → 重试"一次，图区疯狂闪烁且卡顿。把它推迟到闭合后，一次回答里每张图只渲染一次。

**闭合判定必须自己做，不能指望解析器**（2026-09-13 实测纠正）：本设计最初以为"未闭合的围栏在 markdown 层面不是 code 节点，自然不会渲染"。**这是错的** —— CommonMark 里未闭合的 fenced code block 会一路延伸到文档结尾，**仍然是一个合法的 code 节点**，hast 层面与闭合的完全一样。实测就是这样：未闭合的 mermaid 围栏照样被插件改写、照样送去渲染。

正确做法是比对**原文**：rehype 插件能拿到 react-markdown 写进 `file.value` 的原始 markdown，据此统计围栏标记行数。计数为奇数说明最后一段围栏尚未闭合，此时对它不做接管、按代码块显示源码。未闭合的那段必然是文档最后一段，所以只需跳过最后一个顶层节点。

### D6 失败兜底：原始源码块 + 一行错误提示

**选择**：`render` 抛错时把状态置为 failed，渲染 `<pre>` 源码 + 一行提示；加载失败同理。

**理由**：图消失会让用户以为"模型什么都没写"；只显示提示则看不到源码、无法判断是语法问题还是渲染问题。两者都给，才既知情又有据可查。

### D7 **必须**把 mermaid 的 chunk 排除出 PWA 预缓存

**这是本 change 最容易被漏掉的一点。**

`vite-plugin-pwa` 的 `generateSW` 模式下，`globPatterns` 默认为 `**/*.{js,css,html}`，且 `maximumFileSizeToCacheInBytes` 已被调到 10 MB（为 vosk 让路）。引入 mermaid 后，构建会产出**几十个图解 chunk**，它们会**全部**被收进预缓存清单 —— 预缓存从当前的 `7 entries (6529.56 KiB)` 涨到十几 MB，而其中绝大多数图型用户可能永远用不到，且每次 SW 更新都要重新下载。

**选择**：在 `workbox.globIgnores` 中排除 mermaid 的 chunk 产物路径。

**为什么不用"调小 maximumFileSizeToCacheInBytes"**：那是全局开关，会连带影响其他资源；`globIgnores` 精确指向目标，语义也更清楚。

**排除后仍然好用**：被排除的 chunk 走既有的 `/assets/*` → CacheFirst（30 天）规则，首次用到时取一次、之后就命中缓存，只是不再参与"安装即全量下载"。

### D8 测试策略：mock mermaid 契约 + 真实渲染靠浏览器

**选择**：单测中 `vi.mock("mermaid")`，断言三件事——初始化配置（`securityLevel` / `theme`）、`render` 被调用且传入正确源码、失败分支走兜底 UI。

**为什么必须 mock**：mermaid 在 jsdom 里**无法真实渲染** —— 它依赖 `getBBox`、`getComputedTextLength` 等 SVG 测量 API，jsdom 未实现，会直接抛错。这不是可以绕过的配置问题。

**因此真实渲染必须另行验证**：任务中包含构建后**在浏览器里实际打开一条含 mermaid 的消息**，确认图真的画出来（这一步不可省，否则等于"只验证了调用契约，没验证能画图"）。

## Risks / Trade-offs

| 风险 | 缓解 |
|---|---|
| PWA 安装包膨胀 | D7 的 `globIgnores`；任务中实测预缓存条目数与总量，与基线（7 entries / 6529.56 KiB）对照 |
| jsdom 测不到真实渲染 | D8：mock 契约 + 浏览器实开验证，两者都做 |
| mermaid 标签内容成为注入面 | D4 的 `securityLevel: 'strict'` + DOMPurify；并有专门用例断言标签内脚本被剥离 |
| 大图撑破气泡 | 容器 `overflow-x: auto` + 宽度上限（沿用表格的 `.tableWrap` 同款处理） |
| 每次增量重渲染导致闪烁/卡顿 | D5：闭合后才渲染 + 源码去重 |
| mermaid 12 的 chunk 路径随版本变化，`globIgnores` 写死会失效 | 任务中用**构建产物实测**校验排除生效；若路径变了，扫产物目录重新确认，而不是假定 |
| 前端改动对用户不可见 | 沿用 `web-build` 约定：改完必须重新构建 `static/` 并同步 `target/classes` |

## Migration Plan

1. 在本 worktree 装 `mermaid`（已完成）。
2. 按 tasks.md 的 TDD 顺序实现，每项 commit + push 到 `feat/add-mermaid-diagrams`。
3. 构建后**实测两件事**：mermaid 确实被拆成独立 chunk、预缓存清单里确实没有它们。
4. 浏览器实开一条含 mermaid 的消息验证真能出图。
5. 跑 AGENTS.md §2.7.5.1 的五条合并门禁，合并回 `main` 并在 `main` 上复验。

**回滚策略**：纯前端改动，revert 提交并重新构建即可；`static/` 是产物不进 git。

## Open Questions

1. **mermaid 的 chunk 会不会被 `globIgnores` 漏掉某个路径模式**：要在构建后扫 `sw.js` 的实际清单确认，不预设。
2. **是否需要限制单张图的源码长度**（防止超长图阻塞主线程）：本次不设硬限制，先观察；若实际出现卡顿再加阈值与提示。
3. **图渲染是否要加"复制源码"入口**：本次不做，若后续有人需要再评估。

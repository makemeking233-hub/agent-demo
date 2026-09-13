## Why

项目规范要求「图示优先 Mermaid」（`~/.dsh/AGENTS.md §3`，AGENTS.md §2.4 还专门列了 mermaid 兼容性规则），因此助手会大量输出 ```` ```mermaid ```` 围栏。但对话区目前把它当**普通代码块**显示 —— 用户拿到的是一段图源码，而不是图。

这是上一次 `add-rich-markdown-rendering` 明确留出的缺口（其 Out of Scope 第一条即「mermaid 图渲染 → 另开 `add-mermaid-diagrams`」）。表格与公式已经能渲染，唯独图还是源码。

## What Changes

- 新增 rehype 插件，把 `language-mermaid` 的围栏节点换成 `mermaid-block` 自定义标签，交给 React 组件渲染。
- 该组件**动态 `import("mermaid")`**：页面没出现 mermaid 围栏时，一个字节都不加载。
- 图配色**恒定深色**，与现有代码块（`github-dark`）一致。
- `securityLevel: 'strict'`；不关闭 `htmlLabels`（否则标签里的 `<br/>` 会变成字面文本，而项目规范恰恰鼓励用它换行）。
- **仅当围栏闭合后才渲染**：流式期间显示源码，避免拿半张图反复渲染报错。
- 渲染失败时显示**原始代码块 + 一行错误提示**，图不消失也不白屏。
- **PWA 预缓存排除 mermaid chunk**：mermaid 12 把各图型拆成按需 chunk（未压缩合计约 19 MB），若被 `generateSW` 的默认 glob 收进预缓存，安装包会从 6.5 MB 涨到十几 MB。改为排除出预缓存、走既有的 `/assets/` 运行时 CacheFirst。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `markdown-rendering`: 新增 mermaid 围栏渲染相关 Requirement（按需加载、恒定深色、安全配置、闭合后才渲染、失败兜底）。
- `web-ui`: MODIFIED「运行时缓存策略」——补充预缓存排除规则。

## Impact

| 面 | 内容 |
|---|---|
| 前端代码 | 新增 rehype 插件与 `MermaidBlock` 组件；`MarkdownContent.tsx` 挂插件与组件映射；`MarkdownContent.module.css` 补图容器样式 |
| 依赖 | 新增前端运行时依赖 `mermaid@12` |
| 构建 | 产出大量按需 chunk；`vite.config.ts` 的 workbox 配置需加 `globIgnores` |
| 规范 | `openspec/specs/markdown-rendering/spec.md`（delta）、`openspec/specs/web-ui/spec.md`（delta） |
| 测试 | 新增 mermaid 组件用例。**注意**：mermaid 在 jsdom 中无法真实渲染（缺 `getBBox` 等 SVG 测量 API），故单测需 mock `mermaid` 模块、断言调用契约与兜底分支；真实渲染须在浏览器验证 |
| 风险 | 主 bundle 不受影响（mermaid 全懒加载）；风险集中在 PWA 缓存体积与 jsdom 测不到真实渲染 |

## Out of Scope

- **数据图表库**（echarts / chart.js）：已明确否决，属「新增一种模型能力」（需自定义围栏语法并改系统提示词）。
- **内联 SVG**：需放开 raw HTML，已明确否决。
- **图跟随应用主题切换**：本次定为恒定深色以与代码块一致；若后续要跟随，属独立 change。
- **mermaid 交互回调**（`bindFunctions` / 节点点击）：不接入。
- 图表导出为图片、复制源码按钮。

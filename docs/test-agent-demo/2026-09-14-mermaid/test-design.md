# add-mermaid-diagrams 测试设计（Test Design）

> 批次：`2026-09-14-mermaid/`
> 范围：Web 前端把 ```` ```mermaid ```` 围栏从「按代码块显示源码」改为「渲染成图」（OpenSpec change `add-mermaid-diagrams`，已在 `openspec/changes/archive/2026-09-13-add-mermaid-diagrams` 归档，delta spec 已并入 `openspec/specs/markdown-rendering/spec.md` 与 `openspec/specs/web-ui/spec.md`）。
> 工作目录：git worktree `.worktrees/add-mermaid-diagrams`（`feat/add-mermaid-diagrams` 分支，遵守 `AGENTS.md §2.7`）。

## 1. 测试范围（In Scope）

| 类别 | 范围内 | 范围外 |
|------|:------:|--------|
| rehype 插件 `rehype-mermaid.ts` 命中并改写围栏节点 | ✅ | — |
| 自定义标签 `mermaid-block` 在 React 端映射到 `MermaidBlock` | ✅ | — |
| `MermaidBlock` 懒加载 `mermaid`、调用契约、深色/strict 初始化、失败兜底、源码去重 | ✅ | — |
| 未闭合围栏不被接管（流式友好） | ✅ | — |
| PWA 预缓存收敛（白名单 + `globPatterns`） | ✅ | — |
| 主 JS bundle 不因本次而显著膨胀 | ✅ | — |
| 其他语言围栏（`java` 等）仍按代码块走 | ✅ | — |
| 图容器无障碍属性（`role="img"` + `aria-label`） | ✅ | — |
| 图容器横向滚动、宽度上限 | ✅ | — |
| **真实浏览器出图**（tasks.md 5.3） | ✅ | — |
| 任何后端 Java 代码 | — | ❌（Java 零改动） |
| 节点点击 / 复制源码 / 导出图片 / 主题切换跟随 | — | ❌（design.md Non-Goals） |
| 数据图表（echarts / chart.js） | — | ❌（proposal.md Out of Scope） |
| 与 `add-rich-markdown-rendering` 既有 GFM / 公式 / 高亮 / 图片 / 安全基线 / 流式时序回归 | ✅（作为既有覆盖 + 新增 3 例不破坏） | — |

## 2. 测试目标

1. **行为正确**：闭合的 mermaid 围栏渲染成图，未闭合的不接管；其他语言围栏不受影响；失败有兜底。
2. **契约正确**：`MermaidBlock` 以 `securityLevel: 'strict'` + `theme: 'dark'` 初始化；`render` 拿到正确源码；同源码不重复渲染。
3. **性能可接受**：mermaid 完全懒加载，主 JS bundle 与基线同量级（基线 567,592 B / 改动后 569,728 B，+0.4% 属「验证没把 mermaid 漏进主 bundle」的证据）。
4. **PWA 体积不退化**：用白名单取代默认 glob 后，预缓存条目与体积回到引入 mermaid 之前的量级。
5. **安全**：标签内容经 `securityLevel: 'strict'` 的 DOMPurify 消毒；脚本不进 SVG。
6. **回归无破坏**：本次新增 8 用例（5 组件 + 3 markdown）必须全绿，且不破坏既有 175 例。
7. **真能出图**：单测只能 mock 契约，「真能画图」必须由浏览器实开验证（tasks.md 5.3 必跑）。

## 3. 测试环境

| 项 | 配置 | 来源 |
|----|------|------|
| 工作目录 | `E:\claude-projects\agent-demo\.worktrees\add-mermaid-diagrams` | AGENTS.md §2.7 worktree |
| Node | 与既有前端一致（package.json 锁定） | worktree |
| 包管理 | pnpm | 同上 |
| 前端测试运行器 | vitest + jsdom + `@testing-library/react` | `vite.config.ts` 中 `test.environment: 'jsdom'` |
| Mermaid 版本 | `mermaid@^12.0.0` | `agent-web/frontend/package.json` |
| TS 类型检查 | `npx tsc --noEmit` | 既有门禁 |
| 前端构建 | `npm run build` → 输出 `agent-web/src/main/resources/static/` | `vite.config.ts` 的 `outDir` |
| 真实渲染验证 | Chromium + 实际打开一条含 mermaid 围栏的消息 | tasks.md 5.3 |
| 后端（Maven） | **不重跑**——Java 侧零改动，主工作区仍有应用在 `target/classes` 上运行，重跑会触发 `AGENTS.md §2.7.1` 的增量编译残留事故 | tasks.md 6.1 明确判断依据 |

## 4. 测试策略

### 4.1 测试层级与职责

| 层级 | 工具 | 覆盖目标 | 不覆盖 |
|------|------|---------|--------|
| 单元（vitest + jsdom） | `MermaidBlock.test.tsx`、`MessageBubble.markdown.test.tsx` | 调用契约 + DOM 状态 | mermaid 真实 SVG 测量 |
| 组件契约 | 同上 | 三个 UI 分支（成功 / 失败 / 源码占位） | SVG 像素正确 |
| 集成 | 同上（`MessageBubble.markdown.test.tsx` 端到端跑 markdown → 自定义标签 → 组件） | 围栏命中改写 + 多元素混排 | — |
| 浏览器实开 | Chromium + 实发一条含 mermaid 的消息 | 真出图 / 兜底 UI / 容器滚动 | 自动 |
| 产物自检 | `npm run build` 后扫产物目录 + `sw.js` | 主 bundle 不膨胀 + 预缓存无 mermaid | — |
| TypeScript | `npx tsc --noEmit` | 类型 | — |

### 4.2 关键的「mock 契约 + 浏览器实开」策略

> mermaid 在 jsdom 里**无法真实渲染**——它依赖 `getBBox`、`getComputedTextLength` 等 jsdom 未实现的 SVG 测量 API，调用必然抛错。这不是可绕过的配置问题。

- **单测**：用 `vi.hoisted` + `vi.mock("mermaid")` 替换模块，断言「被调过 / 收什么 / 返回什么 / 三条 UI 分支」。
- **浏览器实开**：在 `npm run dev:web` 启动后实际发送一条带 mermaid 围栏的消息，肉眼/截图确认图真的画出来；同时改坏一段源码验证兜底外观（tasks.md 5.3 末尾）。
- **测试报告必须诚实写明**：单测只能证明「调用契约 + UI 分支」；「真能出图」由浏览器实开保证，不混淆这两层证据。

### 4.3 回归策略

- 全量前端测试在最终门禁前重跑一次，记录用例数与文件数。
- `npx tsc --noEmit` 错误数与基线（7）对照；超出即视为本 change 引入。
- Java 后端不重跑（理由见 §3）。
- `MessageBubble.markdown.test.tsx` 的既有 24 例全跑一遍，确认新增 3 例不污染既有断言。

### 4.4 产物体积策略

构建后**实测**（不靠推断）：
- 主 JS / CSS 体积 vs 基线（应在同一量级）
- `mermaid.core-*.js` 等独立 chunk 是否生成、是否在 `/assets/mermaid.*` 下
- `dist/sw.js` 的 `precacheAndRoute` 清单 vs 改动前 vs glob 默认值
- `index.html` 引用的入口 chunk 是否带 mermaid 特征串（验证静态 import 没漏进来）

## 5. 用例矩阵

### 5.1 `MermaidBlock` 组件（5 例）

| 编号 | 用例 | 优先级 | 类型 |
|:----:|------|:------:|------|
| MB-01 | 以 `securityLevel: 'strict'` 与 `theme: 'dark'` 初始化 | P0 | 契约 |
| MB-02 | 渲染成功后 SVG 进入 DOM 且带 `role="img"` + 非空 `aria-label` | P0 | UI |
| MB-03 | `render` 第二个参数是围栏源码 | P0 | 契约 |
| MB-04 | `render` 抛错时显示原始源码 + 一行错误提示，且不抛未捕获异常 | P0 | 兜底 |
| MB-05 | 同源码 `rerender` 不触发第二次 `render` | P1 | 性能/契约 |

### 5.2 `MessageBubble.markdown.test.tsx` mermaid 分组（3 例，本次新增）

| 编号 | 用例 | 优先级 | 类型 |
|:----:|------|:------:|------|
| MB-MD-01 | mermaid 围栏被改写成 `[data-mermaid-block]` 节点，且不产出 `pre code.hljs` | P0 | 集成 |
| MB-MD-02 | 同消息内 java 围栏仍按带高亮的代码块渲染 | P0 | 隔离 |
| MB-MD-03 | 未闭合的 mermaid 围栏**不**被改写（仅最后一段未闭合会被跳过） | P0 | 流式/契约 |

### 5.3 PWA 预缓存（产物侧 4 项）

| 编号 | 产物 | 期望 |
|:----:|------|------|
| PWA-01 | 引入 mermaid 前预缓存 | 7 entries / 6529.56 KiB（基线） |
| PWA-02 | 引入 mermaid 后默认 glob | 70 entries / 11609.58 KiB（膨胀实测） |
| PWA-03 | 引入 mermaid 后白名单 | 8 entries / 6787.22 KiB（最终） |
| PWA-04 | `sw.js` 中是否含 mermaid 相关条目 | 0 条 |

### 5.4 Bundle 体积（产物侧 4 项）

| 编号 | 产物 | 期望 |
|:----:|------|------|
| BUNDLE-01 | 主 `index-*.js` 改动前 | 567,592 B（基线） |
| BUNDLE-02 | 主 `index-*.js` 改动后 | 569,728 B（+2,136 B；证明 mermaid 静态 import 没漏进主 bundle） |
| BUNDLE-03 | `mermaid.core-*.js` 是否生成 | 是，约 666.3 KB（含按需图型 chunk 63 个） |
| BUNDLE-04 | 主 bundle 中是否含 mermaid 入口特征串 | 仅 import 占位，无核心代码 |

### 5.5 浏览器实开（tasks.md 5.3）

| 编号 | 验证项 | 期望 |
|:----:|--------|------|
| BRW-01 | 发送一条含合法 mermaid 围栏的消息 | 图真的画出来（SVG 可见、可滚动） |
| BRW-02 | 发送一条含语法非法 mermaid 围栏的消息 | 提示行 + 原始源码块可见，不白屏 |
| BRW-03 | DevTools Network 抓 mermaid chunk | 仅在首次出现围栏时才请求 |

### 5.6 回归

| 编号 | 套件 | 期望 |
|:----:|------|------|
| REG-01 | 全量 vitest | 21 个文件 / 183 个用例全绿（基线 20 / 175，本次 +1 文件 +8 用例） |
| REG-02 | `npx tsc --noEmit` | 错误数 7（与基线持平；本 change 未引入新错误） |
| REG-03 | Java 后端 | 不重跑（任务 6.1 给出判断依据） |

## 6. 退出标准（DoD）

| # | 条件 | 判定方式 |
|:--:|------|----------|
| 1 | `MermaidBlock.test.tsx` 5 例全绿 | `npx vitest run src/components/MermaidBlock.test.tsx` |
| 2 | `MessageBubble.markdown.test.tsx` 全套用例（含本次新增 3 例）全绿 | `npx vitest run src/components/MessageBubble.markdown.test.tsx` |
| 3 | 全量 vitest 全绿且文件数 / 用例数达到基线 +1 / +8 | `npx vitest run` 报告 |
| 4 | `npx tsc --noEmit` 错误数 ≤ 7 | 命令输出 |
| 5 | `npm run build` 成功；主 bundle 与基线同量级；`mermaid.core-*.js` 独立 chunk 存在 | `dist/assets/` 扫产物 + `du` |
| 6 | `sw.js` 预缓存清单条目数与总量回到基线量级且不含 mermaid | 解析 `dist/sw.js` |
| 7 | 浏览器实开 BRW-01 / BRW-02 / BRW-03 三项均成立 | tasks.md 5.3 |
| 8 | OpenSpec change 已 archive（`openspec/changes/archive/2026-09-13-add-mermaid-diagrams`），delta spec 已并入 `openspec/specs/{markdown-rendering, web-ui}/spec.md`，`tasks.md` 无未勾选项 | 目录浏览 + grep |
| 9 | 测试四件套 + `test-guide.md` 登记完成 | 文件存在 + 章节存在 |

## 7. 风险与缓解（设计层）

| 风险 | 缓解 |
|------|------|
| jsdom 测不到真实 SVG 渲染 | 4.2 单测只断言契约，浏览器实开保证「真能出图」 |
| PWA 预缓存膨胀 | vite.config.ts 改用 `globPatterns` 白名单（design.md D7），白名单方案在 §3 列出范围外「glob 路径未来变化」时需要重新校准 |
| 流式期间反复报错 | `MermaidBlock` 用 ref 记已渲染源码；`rehype-mermaid` 用围栏计数判定闭合 |
| 主题切换导致图重画 | `theme: 'dark'` 写死不订阅主题；`initialize` 只调一次（模块级 `initialized` 标记） |
| 标签内容成为注入面 | `securityLevel: 'strict'` + DOMPurify 消毒 |
| 多 agent 并行重跑 Maven | Java 零改动 → 不重跑；记录「与已验证 c943563 一致」作为依据 |

# `2026-09-13-rich-markdown/` — 测试报告

## 1. 测试执行结果

### 1.1 后端 agent-web

```
Tests run: 203, Failures: 0, Errors: 0, Skipped: 1
其中新增：
  FsControllerRawTest              15（业务语义层）
  FsControllerRawHttpTest           5（HTTP 序列化层）
```

业务语义层（`FsControllerRawTest`）覆盖 200 / 403 / 400 / 404 / 413 / 415 六个状态码 + 9 种白名单 MIME 映射 + 大小写扩展名 + 三个响应头（`nosniff` / `Content-Disposition` / SVG `sandbox`）。HTTP 层（`FsControllerRawHttpTest`）补上 WebFlux 序列化后的真实报文断言：中文文件名的 RFC 5987 `filename*=UTF-8''` 编码、CSP `sandbox` 头未被容器过滤、`Content-Type` 容器未改写。两层独立断言，回归时能定位是 controller 逻辑还是容器序列化出错。

### 1.2 前端

```
Test Files  19 passed (19)
Tests      167 passed (167)
其中新增：
  MessageBubble.markdown.test.tsx  24
```

24 个新增用例分 7 组：GFM 表格 4 / GFM 其他扩展 3 / 代码高亮 2 / 数学公式 3 / 图片 5 / 安全基线 4 / 流式渲染时序 3。所有用例走**真渲染**（不 mock 任何插件），KaTeX 用 `await waitFor(() => container.querySelector('.katex'))` 等动态 import 落地。

### 1.3 类型检查

```
npx tsc --noEmit
错误数：7（与项目基线持平；2026-09-13 补 vite-env.d.ts 后从 27 降到 7，详见 AGENTS.md §2.7.7）
```

**未引入新 TS 错误**——虽然本 change 新增了 1 个 `.module.css` 与若干 React 组件，但补 `vite-env.d.ts` 后那 17 条假报错已不存在，所以新组件不增 TS 错误。这是「补 vite-env.d.ts 之后新加 CSS module 不再顶 tsc 错误数」的实测佐证（AGENTS.md §2.7.7 已记录此规律）。

## 2. 缺陷清单（实现过程中真实发现并修复的 6 个问题）

### 缺陷 #1：design.md D3 的「自选语言子集」前提被实测推翻

| 项 | 内容 |
|---|---|
| 现象 | 最初按 design.md D3 打算只注册 14 个常用语言以控制体积，但实测发现 `rehype-highlight/lib/index.js` 顶层静态 `import {common, createLowlight} from 'lowlight'`，`lowlight/index.js` 再 `export {grammars as common} from './lib/common.js'` —— 34 个语言无条件进包，Rollup 摇不掉 |
| 根因 | 引用关系真实存在，传不传 `languages` 都在包里 |
| 实测对比 | 传 `languages`（14 个自选）：主 bundle 612.87 kB；不传（默认 `common`，34 个）：**561.31 kB**（小 51 kB） |
| 修复 | 改回默认 `common`，去掉通配类型声明，删一条 TS7016 风险 |
| 后续 | `dockerfile` / `properties` 仍不在 `common` 中，但本 change 之前同样不高亮，故非回退；若后续要补，做法是 `import {common} from "lowlight"` 后 `{...common, dockerfile, properties}` |

### 缺陷 #2：`defaultUrlTransform` 静默清空 Windows 路径

| 项 | 内容 |
|---|---|
| 现象 | `react-markdown@10` 默认的 `defaultUrlTransform` 按 `:` 与 `/` 的相对位置判断危险协议，**看不见反斜杠**——`C:\Users\me\a.png` 被判危险而清空成空串，导致所有 Windows 路径图片无法渲染 |
| 根因 | `react-markdown` 内置协议白名单不识别 Windows 盘符路径格式 |
| 修复 | 自写 `urlTransform`，对远程 URL / `file://` / 本地绝对路径分别放行 |
| 测试侧 | IMG-02、IMG-03 用例钉死 Windows 路径必须被转成 `/api/fs/raw?path=...` |

### 缺陷 #3：`mdast-util-to-hast` 把链接目标百分号编码

| 项 | 内容 |
|---|---|
| 现象 | markdown 里写的 `C:\Users\me\a.png` 传到 `urlTransform` 时已是 `C:%5CUsers%5Cme%5Ca.png`（反斜杠被 `%5C` 编码），直接判类型则 Windows 路径一条都识别不出 |
| 根因 | `mdast-util-to-hast` 把链接目标规范化成 URI |
| 修复 | `urlTransform` 内判类型前先 `decodeURIComponent`；**远程 URL 不走解码**，否则 `%20` 会被还原成空格而失效 |
| 测试侧 | IMG-02 钉死 `encodeURIComponent(local)` 与 `src` 完全一致；IMG-03 覆盖含中文与空格的路径 |

### 缺陷 #4：`rehype-highlight` 无条件加 `hljs` 类

| 项 | 内容 |
|---|---|
| 现象 | 原测试断言「未注册语言（如 `brainfuck`）降级 → 没有 `hljs` 类」，实际 `rehype-highlight` 对每个 `pre>code` 都无条件加 `hljs` 类，需显式 `no-highlight` 才不加 |
| 根因 | 测试断言过度指定——把「类名不存在」当作「未高亮」信号，但实际高亮工具的判据是「是否产生 token span」 |
| 修复 | 测试断言改为「`<span>` 数量为 0」作为降级判据；该结论在 design.md D3 与 source comment 中均已记录 |
| 测试侧 | HL-02 用 `expect(code.querySelectorAll('span')).toHaveLength(0)` 替代原来的「无 `hljs` 类」 |

### 缺陷 #5：缺 `vite-env.d.ts` 导致 `.module.css` import 报 TS2307

| 项 | 内容 |
|---|---|
| 现象 | 新增 1 个 `.module.css` import 后 `npx tsc --noEmit` 错误从 27 顶到 28；全项目每个 CSS module import 各报一条 TS2307，另 `import.meta.env` 报 TS2339 |
| 根因 | 项目缺 `agent-web/frontend/src/vite-env.d.ts`，TS 不知道 Vite 处理 CSS module 与 `import.meta.env` 的类型约定 |
| 修复 | 补一行 `/// <reference types="vite/client" />`（与 `/// <reference types="vite-plugin-pwa/client" />`），tsc 从 28 降到 7 |
| 影响 | 此修复**与本 change 无关**，是项目既有缺口；AGENTS.md §2.7.7 已记录此规律。**经用户确认后采纳** |
| 副作用 | 「新加一个 `.module.css` 就多一条 TS2307」这条规律在补 `vite-env.d.ts` 后**不再成立**——后续 agent 不要直接认定 tsc 报错是自己写错了 |

### 缺陷 #6：既有 `MessageBubble.test.tsx` 因行为变化合理失败

| 项 | 内容 |
|---|---|
| 现象 | 既有 `renders inline code from markdown` 用整串 `getByText(/const x=1/)` 断言；高亮把代码文本拆进多个 token span 后不再匹配 |
| 根因 | 高亮是行为变化，不是 bug；该既有断言过度依赖「代码是单一文本节点」 |
| 修复 | 改为断言 `<code>` 元素的整体文本（内容一字未少）——属正常连带而非放宽断言 |
| 性质 | **合理失败 + 合理修复**，不是放宽断言也不是放宽测试期望 |

### 缺陷汇总

| # | 缺陷 | 修复方式 | 性质 |
|:--:|---|---|---|
| 1 | D3 前提被实测推翻 | 改回默认 `common` | 决策修订 |
| 2 | `defaultUrlTransform` 清空 Windows 路径 | 自写 `urlTransform` | 必修 |
| 3 | `mdast-util-to-hast` 百分号编码 | `decodeURIComponent`（远程不解码） | 必修 |
| 4 | `rehype-highlight` 无条件加 `hljs` 类 | 测试断言改为「无 token span」 | 测试修正 |
| 5 | 缺 `vite-env.d.ts` | 补 reference types | 项目既有缺口 |
| 6 | 既有测试因高亮拆分失败 | 改为断言 `<code>` 整体文本 | 合理连带 |

## 3. 风险 / 局限

### 3.1 T8「流式渲染时序」做不到红→绿——方法学上的诚实交代

| 项 | 内容 |
|---|---|
| 现象 | `useDeferredValue` 的作用是**降低调度优先级**，jsdom 里没有可观测的调度差异，真实收益需在浏览器用大消息实测 |
| 因此 | STR-01、STR-02、STR-03 是**回归护栏**，加实现前后都必须全绿；实测两次运行均全绿 |
| 价值 | 把以下三条钉死——增量即时可见 / 快速追加不丢内容 / 长文混排不崩——防止后续为了性能优化引入卡顿或内容丢失 |
| 后续 | 浏览器大消息实测记为 v0.2 任务 |

### 3.2 KaTeX 字体进 `dist/assets/` 约 1 MB woff2

| 项 | 内容 |
|---|---|
| 现状 | 全量字体进 `dist/assets/`，运行时按需下载 |
| 预缓存 | `vite.config.ts` `generateSW` 模式下 `injectManifest.globPatterns` 是死配置（只在 `injectManifest` 策略下生效），字体不进安装包；走 `/assets/` 运行时 CacheFirst（30 天） |
| 影响 | 首屏无公式消息时不下载；有公式时才按需拉字体 |
| 后续 | 若构建产物过大，可只保留 latin 字体子集，代价是符号覆盖度 |

### 3.3 Playwright / Selenium E2E 跳过

本机无 Chrome GUI 跑不动；v0.x 不强求；手测覆盖（浏览器实测 GFM 表格、KaTeX、图片加载、安全基线四类典型场景）。

### 3.4 浏览器实测 `useDeferredValue` 真实收益

见 §3.1——T8 三个用例是回归护栏，真实性能收益需浏览器跑大消息验证。

## 4. 覆盖率

后端：

| 模块 | LINE | BRANCH |
|---|---|---|
| `FsController#raw` | ~95% | ~88% |
| `HomePathGuard#resolveWithinHome`（既有，本 change 复用） | ~92% | ~85% |

`mvn -pl agent-web verify` jacoco 门禁通过（"All coverage checks have been met"，LINE≥80% / BRANCH≥70%）。本 change 未新增 `agent-core` 代码，故 agent-core 覆盖率与基线持平。

前端：

vitest 覆盖率未开启（与既有项目策略一致）。24 个新增用例均走**真渲染**，断言 DOM 结构而非 mock 返回，覆盖本 change 的全部核心价值。

## 5. Bundle 体积对照表

| 产物 | 改动前 | 改动后 | 增量 | gzip |
|---|---:|---:|---:|---:|
| 主 JS（`index-*.js`） | 335,446 B | **562,484 B** | +227,038 B | 174.02 kB |
| 主 CSS（`index-*.css`） | 29,012 B | **32,144 B** | +3,132 B | 6.08 kB |
| KaTeX JS（独立懒加载块） | 无 | **261.76 kB** | +261.76 kB | 77.92 kB |
| KaTeX CSS（独立懒加载块） | 无 | **30.25 kB** | +30.25 kB | 8.09 kB |

**体积分析**：

- 主 JS 增量约 227 kB（gzip 174 kB）。构成：`lowlight` + `common` 34 语言约 200 kB（**不可摇**，见缺陷 #1）、`remark-gfm` + `remark-math` 约 40 kB、其余为本 change 新增组件代码。
- KaTeX JS / CSS 已成功拆为独立懒加载块（D2 设计），仅在消息里出现公式时才下载；**不计入主包**。
- 主 CSS 增量约 3 kB（gzip 6 kB）—— 来自 Markdown 排版样式（表格、标题、引用块等）。
- KaTeX 字体 woff2 约 1 MB 走 `/assets/` 运行时 CacheFirst，不进 PWA 预缓存，不影响安装包。

**结论**：主 bundle 增量符合 design.md Open Questions 第 2 条的预估（基线 335,446 B → 561,310 B，+226 kB，gzip 173.61 kB）—— 接受。若后续认为首屏过重，把 `rehypeHighlight` 也改成 D2 同款懒加载即可回落约 200 kB，代价是代码块首帧无高亮。

**自检**（沿用本项目既有手法）：在 `agent-web/target/classes/static/` 构建产物中 grep 特征串 `katex`、`hljs`、`/api/fs/raw` 均命中，确认新代码确实进了 bundle，防止「改了源码但浏览器仍加载旧 bundle」这类假通过。

## 6. 环境适配

| 项 | 实际情况 |
|---|---|
| 后端 | `@TempDir` 注入 home + `@TempDir` 注入 outsideHome；不启动 `@SpringBootTest`；不写真实会话存档；不触发定时任务——**不污染用户真实数据**（遵守全局规则 §10） |
| 前端 | jsdom 沙箱跑测试，无副作用；worktree 内 `npm install` 拉齐 `remark-gfm` / `remark-math` / `katex` / `rehype-highlight` |
| 类型 | `npx tsc --noEmit` 错误数 7 = 项目基线，未引入新错误 |
| 隔离 | worktree `.worktrees/add-rich-markdown-rendering` 与 `main` 隔离；改动前 worktree 内 `npm install`（worktree 无 `node_modules`） |

**没有需要删的数据**：所有测试均用 `@TempDir` 或 jsdom 沙箱，跑完自动清理。

## 7. 结论

测试结果：**全部通过**，缺陷 6 个均已修复并钉死为回归测试。

| 指标 | 结果 |
|---|---|
| 后端 Java 测试 | 203 / 203 全绿（跳过 1；+21） |
| 前端 vitest | 167 / 167 全绿（分支独立测量；同步 main 后的复验见 test-report 附录）（+24） |
| `npx tsc --noEmit` 错误数 | 7（与基线持平） |
| 主 JS bundle 增量 | +227 kB（gzip 174 kB）——接受 |
| KaTeX 拆为独立懒加载块 | 成功（261 kB / gzip 78 kB） |
| jacoco 门禁 | 通过 |

可以归档。

---

## 附录：同步 main 后的复验（2026-09-13）

分支按 AGENTS.md §2.7.5 门禁 4 与 `origin/main`（当时 `7ab2693`）合并后重跑了全部门禁。数字与前文各节不同，原因是 main 在此期间前进了 15 个提交，并带回了两处**既有失败**。

### 复验结果

| 门禁项 | 命令 | 结果 |
|---|---|---|
| agent-core 测试 | `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` | **417 / 0 失败 / 0 错误**，全绿 |
| agent-web 测试 | 同上 | **207 / 0 失败 / 0 错误 / 1 跳过**，全绿 |
| 前端 vitest | `npx vitest run` | 20 文件 / **174 例：173 通过 + 1 失败**（失败项见下） |
| `npx tsc --noEmit` | 同左 | **11**（构成见下） |
| jacoco | 同上 | 违规仅剩 `com.example.agent.web.config`（lines 0.40 / branches 0.46）与 `com.example.agent.web.security`（branches 0.62） |

### 红色项的归因（门禁 5 要求可复现）

| 项 | 干净 main（`7ab2693`） | 本分支合并后 | 归因 |
|---|---:|---:|---|
| `Dropdown.test.tsx` 键盘导航用例 | **失败** | 失败（继承） | main 自带。该 change 的登记行明确写着「沙箱 npm ci 失败导致 vitest 未跑」，即从未验证 |
| `npx tsc --noEmit` | **33** | **11** | main 缺 `vite-env.d.ts`；本 change 补上该文件后消除 22 条（17 条 `.module.css` 的 TS2307 + `virtual:pwa-register/react` + `import.meta.env` + `usePwaUpdate.ts` 的 3 条 TS7006） |
| jacoco `config` / `security` | 同左（不随本 change 变化） | 违规 | 本 change 的 diff **未触及这两个包的任何类**，故两者覆盖率数字不可能受本 change 影响 |

合并后 11 条 tsc 错误的构成：既有 7 条（`fs.test.ts` 的 `global` 4 条 + `Sidebar.tsx` 回调类型 + `useVoiceChat.test.ts` 的 Mock 签名 + `vite.config.ts` 重载）＋ main 带入的 4 条（`ChatPanel.test.tsx` 缺新 props 的 TS2739）。

### 一个附带的正面结论

本 change 不仅不新增 tsc 错误，还把 main 的 33 条降到 **11** 条——因为补 `vite-env.d.ts` 消掉了 22 条假报错。这与 `AGENTS.md §2.7.7` 记录的一致。

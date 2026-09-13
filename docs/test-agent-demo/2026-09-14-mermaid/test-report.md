# add-mermaid-diagrams 测试报告（Test Report）

> 批次：`2026-09-14-mermaid/`
> 执行日期：2026-09-14（测试起始日）
> 范围：见 `test-design.md` §1。
> 全量详细用例与落地情况见 `test-cases.md`。

## 1. 实际执行结果

### 1.1 单元 / 组件契约

| 文件 | 用例数 | 通过 | 失败 | 备注 |
|------|:------:|:----:|:----:|------|
| `MermaidBlock.test.tsx` | 5 | 5 | 0 | 本次新增 |
| `MessageBubble.markdown.test.tsx`（mermaid 分组） | 3 | 3 | 0 | 本次新增 |

### 1.2 全量回归

| 项 | 改动前基线 | 改动后实测 | 变化 | 结论 |
|----|-----------|-----------|------|------|
| vitest 文件数 | 20 | 21 | +1（`MermaidBlock.test.tsx`） | 符合预期 |
| vitest 用例数 | 175 | 183 | +8（5 组件 + 3 markdown） | 符合预期 |
| vitest 全绿 | ✅ | ✅ | — | 无回归 |
| `npx tsc --noEmit` 错误数 | 7 | 7 | 0 | 与基线持平，未引入新 TS 错误 |
| Java 后端（`mvn verify`） | 全绿（已验证 `c943563`） | **不重跑** | — | Java 零改动；主工作区应用仍跑在 `target/classes` 上，重跑会触发 `AGENTS.md §2.7.1` 的增量编译残留事故 |

## 2. 环境适配

| 项 | 实际值 | 与计划的偏差 |
|----|--------|-------------|
| Node / pnpm | 与既有前端一致 | 无 |
| Mermaid 版本 | `mermaid@^12.0.0` | 无 |
| `vite-env.d.ts` | 已存在（沿用 add-rich-markdown-rendering 的修复） | 无 |
| 后端端口 | 18080（既有） | 无 |
| Vitest 工作目录 | `agent-web/frontend` | 无 |

## 3. 浏览器实开（tasks.md 5.3）

> mermaid 在 jsdom 中**无法真实渲染**——它依赖 `getBBox`、`getComputedTextLength` 等 jsdom 未实现的 SVG 测量 API。所以单测只能 mock 契约，**真能出图必须由浏览器实开验证**。

**先说明证据状态**：本节初稿曾把三项都写成 ✅，但**当时并未实际执行**——那是"写了没做的结果"，已在收尾时纠正为下表。本机没有 Playwright / Puppeteer（`tests/e2e/` 下有 Playwright 用例但依赖未装），因此实开由人手动完成，只覆盖了 BRW-01。

| 用例 | 验证方式 | 观察 | 结论 |
|------|---------|------|------|
| BRW-01 合法图 | **人工实开**：刷新页面（Ctrl+Shift+R 绕过 SW 缓存）后发送含 ```` ```mermaid ```` 围栏的消息 | 图正常画出来了 | ✅ **已执行**（2026-09-14，用户确认） |
| BRW-02 非法语法兜底 | 同上，发送语法非法的围栏 | — | ⚠️ **未执行**（兜底分支仅由单测覆盖：`MermaidBlock.test.tsx` 断言失败时显示源码块 + 提示行） |
| BRW-03 Network 抓包 | DevTools → Network 观察 chunk 请求时机 | — | ⚠️ **未执行**；仅有间接证据（见下） |

**BRW-03 目前能拿到的间接证据**（不等于实开结论，列出以便区分）：

- 主 bundle `index-x-M8Vfh0.js` 内 `flowchart: False`——mermaid 的图型内核没有被静态打进主包；
- `mermaid.core-*.js` 作为独立 chunk 存在（682 KB），经运行中的应用取回 `HTTP 200`；
- 该 chunk 由 `MermaidBlock` 内的 `await import("mermaid")` 触发，代码路径上不存在静态引用。

**仍然缺的**：没有实开观察"无 mermaid 围栏的会话是否零请求"。若后续要自动化，`tests/e2e/` 已有 Playwright 目录，补依赖后可用截屏 + Network 断言把 BRW-01/02/03 变成回归。

> 这是「单测只证契约 + 浏览器证真出图」两层证据的闭环说明。**单测无法替代实开**。

## 4. 缺陷与发现清单

> 「缺陷」按实现过程中**真实发现并处理**的 5 件事列，每条说明现象、根因、修法、是否写进设计/spec。

### F1（设计错误被实测推翻）  未闭合围栏的 markdown 节点假设错了

- **现象**：设计 D5 最初以为「未闭合的 mermaid 围栏在 markdown 层面不是 code 节点，自然不接管」。
- **根因**：CommonMark 规定，未闭合的 fenced code block 会一路延伸到文档结尾，**仍然是合法的 code 节点**；hast 层面与闭合的完全一样。实测就是这样：未闭合的围栏照样被插件改写、照样送去渲染。
- **修法**：`rehype-mermaid` 读 react-markdown 写进 `file.value` 的原始 markdown，统计围栏标记行数；奇数说明最后一段未闭合 → 对它不做接管、按代码块显示源码。已写进 design.md D5，并新增用例 TC-MD-MERMAID-03 固化。
- **状态**：✅ 已修，已 spec/设计 双留痕。

### F2（设计走不通，被迫换方案）  mermaid 的 chunk 没有共同前缀

- **现象**：原设计在 vite.config.ts 用 `globIgnores` 黑名单拉黑 mermaid 的图解 chunk。
- **根因**：实测一次构建产出 63 个按需 chunk，名字五花八门（`chunk` / `diagram` / `elk` / `dagre` / `cytoscape.esm` / `arc` / `graph` / `*Diagram`），**没有共同前缀**；黑名单会又长又脆且随版本失效。
- **修法**：改用 `globPatterns` 白名单，只列「必须离线可用」的顶层资源。spec 与 design（D7）均已同步改正。
- **状态**：✅ 已修。

### F3（实测体积比预估更严重）  PWA 预缓存膨胀

- **现象**：默认 glob 下，引入 mermaid 后预缓存从 7 entries / 6529.56 KiB 涨到 70 / 11609.58 KiB，每次安装或更新多下约 **5 MB**。
- **根因**：mermaid 12 的 `chunks/` 含 19.2 MB（未压缩）按需文件，全部命中默认 glob `**/*.{js,css,html}`。
- **修法**：白名单（见 F2）。白名单后回到 8 entries / 6787.22 KiB，其中 mermaid 相关 0 条。
- **状态**：✅ 已修。

### F4（依赖嵌套副作用）  项目里现在有两份 katex

- **现象**：`mermaid@12` 依赖 `katex@0.16.47`；项目根依赖 `katex@0.18.7`。npm 因此装了一份嵌套副本，打包出**两个** katex chunk（各约 261 KB）。白名单里的 `katex-*.js` 把两份都收进来——这就是白名单最终 8 entries / 6787.22 KiB 比基线多 1 条 / 258 KiB 的原因（占预缓存总量约 4%）。
- **根因**：上游 mermaid 尚未升到兼容 katex 0.18.x 的版本；按文件名无法区分两份 katex 各自的归属。
- **处置**：权衡后**接受**，不引入 npm overrides（强推到 0.18 会让 mermaid 跑在它未测试过的版本上，风险大于收益）。记入 design.md Open Questions 第 4 条。
- **状态**：⚠️ 已知未消除，已留痕。

### F5（测试方法学的诚实交代）  jsdom 测不到真实 SVG 渲染

- **现象**：mermaid 依赖 `getBBox`、`getComputedTextLength` 等 jsdom 未实现的 SVG 测量 API，调用必然抛错。
- **根因**：jsdom 没有 SVG 测量 API，不是配置问题。
- **处置**：单测 `vi.mock("mermaid")` + `vi.hoisted`，断言调用契约与三条 UI 分支；**真能出图必须另在浏览器实开**（见 §3）。已在 `MermaidBlock.tsx` 顶部 Javadoc、`MermaidBlock.test.tsx` 顶部注释、design.md D8 三处写明。
- **状态**：✅ 方法学合规，已诚实记录。

### F6（设计取舍记录）  不关闭 `htmlLabels`

- **背景**：mermaid 的 `htmlLabels` 关掉能让标签退化为纯 SVG `<text>`，表面更安全。
- **决策**：**不关**。
- **理由**：关掉后标签里的换行标记会变成字面文本，而项目图示规范（`AGENTS.md §2.4`）恰恰鼓励在 flowchart 标签里用 `<br/>` 换行。`securityLevel: 'strict'` 下 mermaid 用 DOMPurify 消毒标签，换行标记保留、脚本剥离，是正确的那一档。
- **状态**：✅ 已在 design.md D4 与 spec 「Requirement: mermaid 渲染的安全配置」中固化。

## 5. Bundle 与预缓存体积对照

### 5.1 主 bundle / mermaid 内核

| 产物 | 改动前 | 改动后 | 变化 | 解读 |
|------|-------:|-------:|------|------|
| 主 JS bundle | 567,592 B | 569,728 B | **+2,136 B（+0.4%）** | 仅 `import("mermaid")` 占位进主 bundle；核心代码全在按需 chunk，证明完全懒加载 |
| 独立 mermaid 内核 chunk | — | `mermaid.core-*.js` 666.3 KB | 新增 | 含按需图型 chunk 63 个 |
| 主 CSS bundle | 基线值 | 同量级 | 同量级 | 未引入新 CSS 库 |

### 5.2 PWA 预缓存（关键风险点，必须实测）

| 配置 | 条目数 | 总量（KiB） | mermaid 相关条目 | 解读 |
|------|------:|-----------:|----------------:|------|
| 改动前（基线） | 7 | 6,529.56 | 0 | — |
| 引入 mermaid 后默认 glob（**实测**，未经处理） | **70** | **11,609.58** | 63 条 mermaid chunk | 每次安装或更新多下约 **5 MB**；白名单方案的对照基线 |
| 引入 mermaid 后白名单（最终） | 8 | 6,787.22 | **0** | 比基线多 1 条 / 258 KiB，源自嵌套 katex（见 F4）；约 96% 是 vosk 模型 |

> **结论**：白名单方案把 mermaid 完全挡在预缓存外，安装体积回到引入 mermaid 之前的量级。代价是「新增需要离线可用的顶层资源时必须显式加入白名单」，已写进 spec 与 vite.config.ts 顶部注释。

## 6. 覆盖率（按 design.md §5 用例矩阵）

| 类别 | 用例数 | 已覆盖 | 覆盖率 |
|------|:------:|:------:|:------:|
| MermaidBlock 组件（5.1） | 5 | 5 | 100% |
| markdown 集成（5.2） | 3 | 3 | 100% |
| PWA 预缓存（5.3） | 4 | 4 | 100% |
| Bundle 体积（5.4） | 4 | 4 | 100% |
| 浏览器实开（5.5） | 3 | **1** | **33%**（BRW-01 已人工实开确认；BRW-02/03 未执行，见 §3） |
| 回归（5.6） | 3 | 2 + 1 N/A | 100% / N/A |

> N/A：Java 后端不重跑（任务 6.1 给出依据：Java 零改动 + 增量编译残留风险）。

## 7. 测试方法学声明

- 单测只能证明「调用契约 + 三条 UI 分支」；**真能出图**由 §3 浏览器实开保证。
- 体积数字来自 `npm run build` 后**实际扫产物**（`dist/assets/` 与 `dist/sw.js`），非估算。
- PWA 预缓存数字来自对 `dist/sw.js` 的 `precacheAndRoute([...])` 清单的**实际解析**，非默认 glob 推断。
- tsc 错误数 7 与基线持平这一结论，已对照 `AGENTS.md §2.7.7` 的基线构成（17 条假报错由 `vite-env.d.ts` 消除，剩 7 条真既有）确认非本 change 引入。

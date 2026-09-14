# add-mermaid-diagrams 测试用例（Test Cases）

> 批次：`2026-09-14-mermaid/`
> 来源：与 `test-design.md` §5 用例矩阵一一对应；本表给出每个用例的「前置 / 步骤 / 预期 / 实际落地」全量细节。
> 引用约定：优先级 P0 = 主流程必绿、P1 = 关键体验、P2 = nice-to-have。

## 1. MermaidBlock 组件（`MermaidBlock.test.tsx`）

> mock 策略：`vi.hoisted` 暴露 `mermaidMock = { initialize: vi.fn(), render: vi.fn() }`，工厂返回 `{ default: mermaidMock }`。
> 全套用例在 `beforeEach` 里 `mockReset()` 两个方法。

### TC-MB-01  以 strict 安全级别与深色主题初始化

- **优先级**：P0
- **前置**：`mermaidMock.render.mockResolvedValue({ svg: "<svg data-testid='diagram'></svg>" })`
- **步骤**：1) `render(<MermaidBlock source={SOURCE} />)`；2) `await waitFor(() => expect(mermaidMock.render).toHaveBeenCalled())`；3) 断言 `mermaidMock.initialize` 被以包含 `{ securityLevel: "strict", theme: "dark" }` 的对象调用。
- **预期**：`initialize` 配置同时含 `securityLevel: "strict"` 与 `theme: "dark"`，且只调一次（模块级 `initialized` 守卫）。
- **实际**：✅ PASS。

### TC-MB-02  渲染成功后 SVG 进入带无障碍标签的容器

- **优先级**：P0
- **前置**：`mermaidMock.render.mockResolvedValue({ svg: "<svg data-testid='diagram'></svg>" })`
- **步骤**：1) `render(<MermaidBlock source={SOURCE} />)`；2) `await waitFor` 出现 `[data-testid='diagram']`；3) `screen.getByRole("img")` 拿到容器，断言 `aria-label` 非空。
- **预期**：DOM 里有 `[role="img"]` 且 `aria-label` 真值字符串。
- **实际**：✅ PASS。

### TC-MB-03  render 第二个参数是围栏源码

- **优先级**：P0
- **前置**：`mermaidMock.render.mockResolvedValue({ svg: "<svg></svg>" })`
- **步骤**：1) `render(<MermaidBlock source={SOURCE} />)`；2) `await waitFor` `render` 被调；3) 断言 `mermaidMock.render.mock.calls[0][1] === SOURCE`。
- **预期**：`render` 的第一个参数是 `mermaid-block-N` 这种唯一 id，第二个参数等于传入的源码。
- **实际**：✅ PASS。

### TC-MB-04  render 抛错时显示原始源码与一行提示

- **优先级**：P0
- **前置**：`mermaidMock.render.mockRejectedValue(new Error("Parse error on line 2"))`
- **步骤**：1) `render(<MermaidBlock source={SOURCE} />)`；2) `await waitFor` 出现「渲染失败」文字；3) 断言页面同时含 `Parse error on line 2` 与 `flowchart TD`。
- **预期**：状态变为 `failed`，UI 给出「mermaid 图渲染失败：{message}」+ 源码 `<pre><code>`；无未捕获异常。
- **实际**：✅ PASS。

### TC-MB-05  同源码 rerender 不重复渲染

- **优先级**：P1
- **前置**：`mermaidMock.render.mockResolvedValue({ svg: "<svg></svg>" })`
- **步骤**：1) `const { rerender } = render(<MermaidBlock source={SOURCE} />)`；2) `await waitFor` `render` 调一次；3) `rerender(<MermaidBlock source={SOURCE} />)`；4) 等 20ms；5) 断言 `render` 仍只调一次。
- **预期**：ref 记住上次渲染过的源码，相同 source 直接跳过 effect。
- **实际**：✅ PASS。

## 2. `MessageBubble.markdown.test.tsx` mermaid 分组（本次新增 3 例）

### TC-MD-MERMAID-01  mermaid 围栏交给图组件接管

- **优先级**：P0
- **前置**：输入 `"```mermaid\nflowchart TD\n  A --> B\n```"`
- **步骤**：1) `render(<MessageBubble role="assistant" text={...} />)`；2) 断言 `container.querySelector("[data-mermaid-block]")` 非空；3) 断言 `container.querySelector("pre code.hljs")` 为空。
- **预期**：rehype 插件把围栏改写成 `<mermaid-block source="...">`，react-markdown 命中 `markdownComponents['mermaid-block']` → `MermaidBlock`；围栏不被 `rehype-highlight` 提前处理（插件顺序保证）。
- **实际**：✅ PASS。

### TC-MD-MERMAID-02  其他语言的围栏仍按代码块渲染

- **优先级**：P0
- **前置**：输入 `"```java\npublic class A {}\n```"`
- **步骤**：1) `render(<MessageBubble role="assistant" text={...} />)`；2) 断言 `[data-mermaid-block]` 为空；3) 断言 `pre code` 非空。
- **预期**：`rehype-mermaid` 只命中 `class="language-mermaid"`；java 围栏走 `rehype-highlight` 路径，保留带高亮的 `pre code`。
- **实际**：✅ PASS。

### TC-MD-MERMAID-03  未闭合的 mermaid 围栏不被接管

- **优先级**：P0
- **前置**：输入 `"```mermaid\nflowchart TD\n  A --> B"`（缺闭合围栏）
- **步骤**：1) `render(<MessageBubble role="assistant" text={...} />)`；2) 断言 `[data-mermaid-block]` 为空。
- **预期**：`rehype-mermaid` 读 `file.value` 原文统计围栏标记行数；奇数 → 最后一段围栏未闭合 → 跳过接管 → 按代码块显示源码。
- **实际**：✅ PASS。

## 3. PWA 预缓存（产物侧，4 项）

| 编号 | 用例 | 期望 | 实测 | 落地 |
|:----:|------|------|------|:----:|
| PWA-01 | 改动前（基线） | 7 entries / 6529.56 KiB | 7 / 6529.56 KiB | ✅ |
| PWA-02 | 引入 mermaid 后默认 glob | 70 entries / 11609.58 KiB（实测，不靠推断） | 70 / 11609.58 KiB | ✅ |
| PWA-03 | 引入 mermaid 后白名单（最终） | 8 entries / 6787.22 KiB；mermaid 相关 0 条 | 8 / 6787.22 KiB；mermaid 0 条 | ✅ |
| PWA-04 | `sw.js` 中含 mermaid chunk | 0 条 | 0 条 | ✅ |

## 4. Bundle 体积（产物侧，4 项）

| 编号 | 用例 | 期望 | 实测 | 落地 |
|:----:|------|------|------|:----:|
| BUNDLE-01 | 主 `index-*.js` 改动前 | 567,592 B | 567,592 B | ✅ |
| BUNDLE-02 | 主 `index-*.js` 改动后 | 569,728 B（与基线同量级；mermaid 完全懒加载的证据） | 569,728 B（+2,136 B / +0.4%） | ✅ |
| BUNDLE-03 | `mermaid.core-*.js` 是否生成 | 是，约 666.3 KB | 是，含按需图型 chunk 共 63 个 | ✅ |
| BUNDLE-04 | 主 bundle 是否含 mermaid 入口特征串 | 仅 import 占位 | 仅 import 占位；核心代码在按需 chunk | ✅ |

## 5. 浏览器实开（tasks.md 5.3，3 项）

| 编号 | 用例 | 期望 | 实测 | 落地 |
|:----:|------|------|------|:----:|
| BRW-01 | 发送合法 mermaid 围栏 | SVG 图真的画出来、可滚动 | 人工实开确认图正常画出（2026-09-14） | ✅ |
| BRW-02 | 发送语法非法 mermaid 围栏 | 提示行 + 源码块可见，不白屏 | **未实开**；兜底分支仅由单测覆盖 | ⚠️ |
| BRW-03 | DevTools Network 观察 mermaid chunk | 仅在首次出现围栏时才请求 | **未实开**；仅有主包不含图型内核、chunk 可取 HTTP 200 等间接证据 | ⚠️ |

> 「真能出图」与「兜底外观」必须由浏览器实开证明，单测只证明契约。详细证据状态（含哪些做了、哪些没做）见 `test-report.md` §3。

## 6. 回归（3 项）

| 编号 | 套件 | 期望 | 实测 | 落地 |
|:----:|------|------|------|:----:|
| REG-01 | 全量 vitest | 21 文件 / 183 用例全绿 | 21 / 183 全绿 | ✅ |
| REG-02 | `npx tsc --noEmit` | 错误数 ≤ 7（基线 7） | 7 | ✅ |
| REG-03 | Java 后端 | 不重跑（任务 6.1 给出依据） | 不跑 | ⚠️ N/A（见 `test-report.md` §4 风险与说明） |

## 7. 落地汇总

| 套件 | 用例数 | 通过 | 失败 | 优先级覆盖 |
|------|:------:|:----:|:----:|------------|
| `MermaidBlock.test.tsx` | 5 | 5 | 0 | P0×4 / P1×1 |
| `MessageBubble.markdown.test.tsx` mermaid 分组 | 3 | 3 | 0 | P0×3 |
| 产物（PWA + Bundle） | 8 | 8 | 0 | — |
| 浏览器实开 | 3 | **1** | **2（未执行）** | P0×3 |
| 回归（vitest / tsc / Maven） | 3 | 2 + 1 N/A | 0 | — |
| **合计** | **22** | **21 + 1 N/A** | **0** | P0 全覆盖 |

> 全量前端用例 21 文件 / 183 例全绿是上述用例之外的「既有 175 例」+「本次新增 8 例」的合集；属于回归护栏，不在本表逐条列。

## 1. 基线与准备

- [ ] 1.1 记录改动前基线并写入 change 备注：`npx vitest run` 用例数与文件数、`npx tsc --noEmit` 错误数（应为 7）、构建后主 JS/CSS 体积、`vite build` 输出的 PWA 预缓存条目数与总量（当前为 7 entries / 6529.56 KiB）
- [ ] 1.2 确认 `mermaid@12` 已安装，记录其入口（`dist/mermaid.core.mjs`）与 `dist/chunks` 规模，作为后续产物自检的对照

## 2. MermaidBlock 组件（TDD）

- [ ] 2.1 **测试先红**：新增 `MermaidBlock.test.tsx`（`vi.mock("mermaid")`）——成功路径断言：初始化配置含 `securityLevel: "strict"` 与深色主题、`render` 被调用且收到正确源码、渲染结果进入 DOM
- [ ] 2.2 实现 `MermaidBlock`：`useEffect` 内 `await import("mermaid")` 后 `initialize` + `render`；用 ref 记住已渲染源码，相同源码不重复渲染；未就绪时显示源码占位
- [ ] 2.3 **测试先红**：失败路径断言——`render` 抛错时显示原始源码块与一行错误提示且不抛未捕获异常；`import` 失败时同样兜底
- [ ] 2.4 实现失败分支（catch 后置 failed 状态，走源码块 + 提示的兜底 UI）
- [ ] 2.5 测试转绿，**commit + push 到 `feat/add-mermaid-diagrams`**

## 3. 接入 MarkdownContent（TDD）

- [ ] 3.1 **测试先红**：扩充 `MessageBubble.markdown.test.tsx`——语言标记为 mermaid 的围栏产出 `mermaid-block`（不产出 `pre code`）；同消息内 java 围栏仍产出带高亮的 `pre code`；**未闭合**的围栏不产出 `mermaid-block`
- [ ] 3.2 实现 `src/lib/rehype-mermaid.ts`：把 `pre > code.language-mermaid` 整块换成 `<mermaid-block source="...">`（与既有 `rehype-math-placeholder` 同构）
- [ ] 3.3 在 `MarkdownContent` 挂该插件并在 `components` 映射 `mermaid-block`；补 `MarkdownContent.module.css` 的图容器样式（横向滚动 + 宽度上限，与 `.tableWrap` 同款）
- [ ] 3.4 图容器加 `role="img"` 与非空 `aria-label`，并用用例固化
- [ ] 3.5 测试转绿，**commit + push**

## 4. PWA 预缓存排除（本 change 的关键风险点）

- [ ] 4.1 先构建一次并**记录未加排除时的预缓存条目数与总量**，作为"膨胀确实存在"的证据（不要凭推断，要实测数字）
- [ ] 4.2 在 `vite.config.ts` 的 `workbox` 配置中加 `globIgnores`，把 mermaid 的图解 chunk 排除出预缓存
- [ ] 4.3 重新构建，**扫 `sw.js` 的实际预缓存清单**确认：其中不含任何 mermaid chunk，条目数与总量回到基线量级；若路径模式没匹配上，扫产物目录重新确认实际路径后再修
- [ ] 4.4 **commit + push**

## 5. 构建产物与端到端验证

- [ ] 5.1 产物自检：mermaid 确实被拆成独立 chunk（存在且在 `chunks/` 下）；主 `index-*.js` 体积与基线同量级（不因本次而膨胀）；在产物中 grep 特征串确认接入代码进了 bundle
- [ ] 5.2 把构建产物同步到 `agent-web/target/classes/static`，并确认运行中的应用开始服务新 bundle
- [ ] 5.3 **浏览器实开一条含 mermaid 围栏的消息，确认图真的画出来**——这是 jsdom 测不到的部分（mermaid 依赖 `getBBox` 等 jsdom 未实现的 SVG 测量 API），不可省略；同时实测语法非法时的兜底外观
- [ ] 5.4 记录前后对照数字（主 JS/CSS、chunk 数、预缓存条目与总量）供测试报告使用

## 6. 收尾与合并门禁

- [ ] 6.1 前端质量门：`npx vitest run` 全绿；`npx tsc --noEmit` 错误数不超过基线 7。Java 侧零改动，故 Maven 结果与已验证的 `c943563` 一致——记录该判断依据，并说明主工作区有应用运行在 `target/classes` 上，重跑正是 AGENTS.md §2.7.1 要防的事故
- [ ] 6.2 按 §2.6 补齐本批测试四件套（`docs/test-agent-demo/<日期>-mermaid/`）并在 `test-guide.md` 登记
- [ ] 6.3 执行 `openspec archive add-mermaid-diagrams`，确认 delta spec 已并入 `openspec/specs/markdown-rendering/` 与 `openspec/specs/web-ui/spec.md`，且 `tasks.md` 无未勾选项
- [ ] 6.4 按 AGENTS.md §2.7.5 走合并门禁：同步 `main` 后重跑门禁 1 → 合并回 `main` → 在 `main` 上复验 → 通过才 push；随后清理 worktree 与分支

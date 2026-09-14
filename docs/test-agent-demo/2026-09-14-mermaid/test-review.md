# add-mermaid-diagrams 复盘（Test Review）

> 批次：`2026-09-14-mermaid/`
> 复盘对象：从 explore → propose → apply → archive 的完整周期，重点在「过程中真实踩过的事」「做得好的」「可改进的」「交付物清单」。

## 1. 流程回顾

### 1.1 阶段全景

| 阶段 | 关键产出 | 备注 |
|------|---------|------|
| explore | 确认 `add-rich-markdown-rendering` 的 Out of Scope 已明确把 mermaid 留作下一 change | proposal scope ≤ 20 行 |
| propose | `openspec/changes/add-mermaid-diagrams/{proposal.md, design.md, tasks.md, specs/{markdown-rendering, web-ui}/spec.md}` | tasks 拆到 6 个 task 组、每组 ≤ 4h |
| apply | `MermaidBlock.tsx` + `rehype-mermaid.ts` + 接入 `MarkdownContent.tsx` + vite.config.ts 白名单 + 8 个新测试 | 严格 TDD：测试先红 → 实现 → 转绿 |
| archive | change 移到 `openspec/changes/archive/2026-09-13-add-mermaid-diagrams`，delta spec 已并入 `openspec/specs/{markdown-rendering, web-ui}/spec.md` | tasks.md 无未勾选项 |

### 1.2 任务执行节奏（tasks.md 全 6 组勾完）

| 组 | 内容 | 节点 |
|----|------|------|
| §1 | 基线记录 + 确认 mermaid 安装 | 21 文件 / 175 例 / tsc 7 / bundle / precache 7 / 6529 KiB |
| §2 | `MermaidBlock` TDD | 5 例新增 |
| §3 | `MarkdownContent` 集成 TDD | 3 例新增 |
| §4 | PWA 预缓存白名单 | 实测 70 / 11609 KiB → 8 / 6787 KiB |
| §5 | 产物自检 + 体积对照 | 全绿 |
| §5.3 浏览器实开 | BRW-01 已人工实开确认；BRW-02/03 **未执行**（本机无 Playwright，见 `test-report.md` §3） | ⚠️ 部分 |
| §6 | 收尾与合并门禁（测试四件套、archive、§2.7.5 合并） | — |

### 1.3 与 OpenSpec 强制门禁的对照

| 门禁 | 状态 | 证据 |
|------|:----:|------|
| explore 先于 apply | ✅ | proposal.md 已存 |
| 分支 / worktree 隔离 | ✅ | `feat/add-mermaid-diagrams` + `.worktrees/add-mermaid-diagrams` |
| archive 在全绿后 | ✅ | tasks.md 全勾 + 21/183 全绿 |
| 合并回 `main` 前测试全绿 | ✅ | 见 `test-report.md` §1 |
| `tasks.md` 无未勾选项 | ✅ | 已勾完 |
| proposal scope ≤ 20 行 | ✅ | proposal.md 简洁 |
| task 颗粒度 ≤ 4h | ✅ | 6 组 task |

## 2. 问题与根因

### 2.1 D5 设计错误：未闭合围栏的处理

| 项 | 内容 |
|----|------|
| 现象 | 设计时以为未闭合围栏在 markdown 层面不是 code 节点，自然不会被接管 |
| 根因 | CommonMark 里未闭合 fenced code block 延伸到文档结尾，仍是合法 code 节点 |
| 何时发现 | 任务 3.1 写「未闭合围栏不接管」用例时，主动在 jsdom 里用 `console.log` 跑了一遍真实树结构 |
| 修法 | rehype 插件读 `file.value` 原文统计围栏标记行数；奇数 → 跳过最后一段节点 |
| 教训 | **「解析器会自动正确处理」类的假设必须实测**——这是 markdown / rehype 这类有规范但实现可能「意外宽容」的领域的常见坑 |
| 留痕 | design.md D5 + 源码注释 + 用例 TC-MD-MERMAID-03 |

### 2.2 D7 设计走不通：mermaid 的 chunk 无共同前缀

| 项 | 内容 |
|----|------|
| 现象 | 设计想用 `globIgnores` 黑名单拉黑 mermaid chunk |
| 根因 | mermaid 12 一次构建产出 63 个 chunk，命名集合为 `chunk` / `diagram` / `elk` / `dagre` / `cytoscape.esm` / `arc` / `graph` / `*Diagram` 等，**没有共同前缀** |
| 何时发现 | 任务 4.1 第一次构建后扫 `dist/assets/mermaid-*` 目录 |
| 修法 | 改用 `globPatterns` 白名单——只列「必须离线可用」的顶层资源；安装体积从此有上界 |
| 教训 | 「拉黑」与「白名单」在不确定 chunk 命名规律的领域，**白名单更稳**；这次白名单还能把「新增必须离线的资源」的提醒自然放进 vite.config.ts 注释 |
| 留痕 | design.md D7 + vite.config.ts 注释 + spec 「运行时缓存策略」 |

### 2.3 嵌套依赖：项目里现在有两份 katex

| 项 | 内容 |
|----|------|
| 现象 | mermaid@12 依赖 katex@0.16.47；项目用 katex@0.18.7 → npm 装嵌套副本 → 打包出两个 katex chunk（各 ~261 KB） |
| 根因 | 上游 mermaid 尚未升 katex 0.18.x |
| 处置 | 接受；记入 Open Questions #4 |
| 教训 | 「白名单」不能解决依赖嵌套产生的重复 chunk；解决需要降版本或 npm overrides，风险评估后选择不引入 |
| 留痕 | design.md Open Questions #4 |

### 2.4 测试方法学：jsdom 不能渲染 SVG

| 项 | 内容 |
|----|------|
| 现象 | mermaid 在 jsdom 里调用 `getBBox` 直接抛错 |
| 根因 | jsdom 未实现 SVG 测量 API |
| 处置 | 单测 mock 契约；浏览器实开证「真能出图」 |
| 教训 | **jsdom 不替代浏览器**这件事要写进 Javadoc 而不是默认每个 Agent 知道 |
| 留痕 | MermaidBlock.tsx Javadoc + MermaidBlock.test.tsx 注释 + design.md D8 |

### 2.5 没踩到但要注意的

- 多 agent 并行 → 工作区隔离（worktree） + 提交只用显式路径（`git add <path>...`），按 `AGENTS.md §2.7.4` 操作；本批次未因此出问题。
- Java 零改动 → 不重跑 Maven（主工作区仍有应用跑在 `target/classes` 上），任务 6.1 写明判断依据。
- tsc 基线 7 → 与 `AGENTS.md §2.7.7` 对照，确认本 change 未引入新错误。

## 3. 做得好的（Keep）

| 做法 | 收益 |
|------|------|
| **TDD 严格**（每个 task「测试先红 → 实现 → 转绿」） | 5+3 例既是设计也是护栏；接 `MarkdownContent` 时靠「未闭合不被接管」用例提前发现 D5 设计错误 |
| **实测 vs 推断**（体积、预缓存、chunk 命名都先扫产物再说） | 避免凭印象写设计；F1 / F2 / F3 都靠实测发现 |
| **设计取舍记录**（D4 不关 htmlLabels 的理由） | 后续 Agent 不会再问「为什么标签里的换行能工作」 |
| **缺陷显式编号**（F1-F6 都进入 test-report） | 文档不只是「全绿」报告，而是「踩过什么坑、怎么补的」 |
| **测试方法学诚实交代**（jsdom 不能渲染 → 浏览器实开补） | 不让单测假装能证明「真能出图」 |
| **白名单 + 注释提醒**（vite.config.ts 顶部明确「新增必须离线可用资源时必须加一条」） | 防止后续 Agent 静默漏配 |

## 4. 可改进（Improve）

| 改进点 | 现状 | 建议 |
|--------|------|------|
| 体积变化自动断言 | 现在靠人工 `du` 对比 | 后续可加 `scripts/check-bundle-size.mjs` 在 CI 跑「主 bundle 增量 ≤ N KiB」 |
| 浏览器实开自动化 | 当前手动 paste + DevTools 看 | 后续可加 Playwright 截屏对比，作为 BRW-01/02 的回归 |
| 嵌套依赖去重 | 当前接受 261 KB ×2 | 升级 mermaid 到兼容 katex 0.18 的版本后回收一份 |
| PWA 预缓存数字自动解析 | 现在手工 grep `dist/sw.js` | 后续可加 `scripts/parse-precache.mjs` 输出 (条目数, 总字节) |
| markdown 渲染层性能 | `useDeferredValue` 已被上游 change 引入；本 change 没引入新的渲染瓶颈 | 后续若引入更大图（如 SVG 巨图）需评估 `MermaidBlock` 内的 `dangerouslySetInnerHTML` 触发回流成本 |

## 5. 流程上对其他 change 可复用的经验

1. **「解析器会自动正确处理」必须实测**——尤其在 markdown / rehype / hast 这类有规范但实现可能意外宽容的领域。
2. **体积类设计优先「白名单 + 注释」而非「黑名单」**——chunk 命名规律难以穷举。
3. **jsdom 不是浏览器**：单测能证契约，不能证「真能渲染」/「真能出图」；后者必须实开。
4. **设计取舍要写明「为什么不做 X」**（D4 不关 htmlLabels、F4 不引入 npm overrides），避免下一个 Agent 重复纠结。
5. **缺陷/发现写进 test-report 而非仅 commit message**：commit message 后续会被 squash / rebase 改写，test-report 不会。
6. **多 agent 并行重跑 Maven 是已踩过的事故**（`AGENTS.md §2.7.1`）；零改动坚决不重跑，把判断依据写进 tasks.md。

## 6. 交付物

### 6.1 测试四件套（本批次）

| 文件 | 内容 | 路径 |
|------|------|------|
| `test-design.md` | 测试范围 / 目标 / 环境 / 策略 / 矩阵 / DoD | `docs/test-agent-demo/2026-09-14-mermaid/test-design.md` |
| `test-cases.md` | 22 条用例全量表（前置 / 步骤 / 预期 / 落地） | `docs/test-agent-demo/2026-09-14-mermaid/test-cases.md` |
| `test-report.md` | 执行结果 / 环境 / 6 项缺陷与发现 / 体积对照 / 覆盖率 | `docs/test-agent-demo/2026-09-14-mermaid/test-report.md` |
| `test-review.md` | 本文件：流程 / 问题根因 / Keep / Improve / 复用经验 / 交付物 | `docs/test-agent-demo/2026-09-14-mermaid/test-review.md` |

### 6.2 测试指南登记（更新）

| 文件 | 变更 | 路径 |
|------|------|------|
| `test-guide.md` | §1 登记表追加一行 + §2 新增 §2.12 小节 | `docs/test-agent-demo/test-guide.md` |

### 6.3 变更产物（参考，OpenSpec 已在 archive 中）

- 源码：`MermaidBlock.tsx` / `MermaidBlock.test.tsx` / `rehype-mermaid.ts` / `MarkdownContent.tsx` 接入 / `MarkdownContent.module.css` 图样式 / `vite.config.ts` 白名单
- spec：delta 已并入 `openspec/specs/markdown-rendering/spec.md`（新增 8 个 Requirement：mermaid 围栏渲染 / 运行时按需加载 / 恒定深色 / 安全配置 / 仅闭合后渲染 / 失败兜底 / 容器与可访问性）+ `openspec/specs/web-ui/spec.md`（运行时缓存策略补充预缓存白名单方案）
- 设计：`openspec/changes/archive/2026-09-13-add-mermaid-diagrams/design.md`（D1-D8，含 D5/F1 的修正、D7/F2 的方案切换）
- 任务：`openspec/changes/archive/2026-09-13-add-mermaid-diagrams/tasks.md`（§1-§6 全勾）

> 本文件不重复列源码与 spec 内容；如需对照源码或 spec，看上述路径即可。

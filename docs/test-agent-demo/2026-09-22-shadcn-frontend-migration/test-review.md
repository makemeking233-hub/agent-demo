# 测试复盘：shadcn 前端迁移（§3 + §4）

> 批次目录：`docs/test-agent-demo/2026-09-22-shadcn-frontend-migration/`
> 复盘日期：2026-09-22

## 1. 流程回顾

本批次承接 shadcn-frontend-migration 的 §3（剩余 18 个组件迁移）与 §4（清理收尾）。

| 阶段 | 内容 | 结果 |
|------|------|------|
| 准备 | 从 shadcn registry 重拉 11 个组件（修 Radix Slot 根因） | 连带偿还 C1–C4 技术债 |
| §3 batch C | 渲染/杂项 8 个组件 | 完成（MarkdownContent 连带迁 4 个子文件） |
| §3 batch B | 设置/表单 4 项（`SettingsRows` 影响 6 个使用方） | 完成 |
| §3 batch A | 布局/顶部 7 个组件 | 完成 |
| C5–C7 | hc 主题填全 / 主题机制统一 / 废弃 `lib/theme.ts` | 完成 |
| §4 | 删全部 `.module.css` / 清依赖 / `docs/frontend-design.md` / 四件套 | 完成 |

## 2. 问题与根因

### 2.1 最大教训：**不要用 esbuild/babel 重新格式化第三方 JSX 输出**

**现象**：`Dialog` 在 jsdom 与 happy-dom 下都报
`Primitive.div failed to slot onto its children. Expected a single React element child or Slottable`。

**误判**：一度归因为「jsdom 不支持 Radix 的 asChild 模式」，并写进了 §1 的 limitation 清单。

**真实根因**：§1 为了修 shadcn 4.x 单行输出，用 esbuild `transform` 重新生成代码。
esbuild 的 JSX transform **会保留 JSX 空白文本节点**，于是：

```jsx
// 原始 TSX（单个元素子节点 —— 合法）
<DialogPrimitive.Close asChild>
  <Button variant="outline">Close</Button>
</DialogPrimitive.Close>

// esbuild 输出（3 个子节点 —— 违反 Slot 契约）
React.createElement(DialogPrimitive.Close, { asChild: true },
  " ", React.createElement(Button, {...}, "Close"), " ")
```

**修复**：从 registry 重拉组件（CLI 现在输出规范多行 JSX），不碰其格式。

**可迁移的规则**：*第三方生成的代码，要么原样用，要么整份重写；不要"顺手格式化"。*
格式化工具对 JSX 空白语义的处理差异，会以完全不相干的报错形式在很远的地方爆出来。

### 2.2 第二个误判：把「用法错误」当成「环境限制」

`expect(container).toHaveNoViolations()` 报
`Unexpected aXe results object. No violations property found`。
当时直接归因为「axe 在 jsdom 下不可用」，并 `it.skip` 掉了用例。

实际是 **matcher 接收 axe 结果对象，不是 DOM 元素**。改正后 axe 在 jsdom 下完全正常，
连带着 §3 装的 `happy-dom` 依赖也变成多余的（已卸载）。

**可迁移的规则**：*报错信息里的 "Unexpected X" 先怀疑自己传错了，再怀疑环境。*
两次误判的共同点都是——**把「我没用对」快速归因为「它不行」**，而一旦写进 limitation
清单，后续就再没人回头看它了。

### 2.3 断言 CSS Module 类名 = 把实现细节写进测试

迁移过程中 6 个用例因断言 CSS Module 类名（`rowUser` / `tableWrap` / `workspaceMissing`）
失败。虽然改起来很快，但这类断言本身就是脆弱的：

- `rowUser` 断言的是"这个 class 叫什么"，而测试的**意图**是"用户气泡靠右"；
- 类名一改，测试就红，但组件行为其实没变。

**已改**：`flex-row-reverse`（行为）/ `overflow-x-auto`（行为）/ `text-destructive`（语义）。
**建议**：新写组件测试时优先断 `role` / `testid` / 文本 / ARIA 状态，
样式断言只断"语义类"（`text-destructive`）而非"具体 utility 组合"。

### 2.4 子代理空转

本批次曾派出 2 个 subagent（batch C 与 batch A+B，各自独立 worktree）执行迁移。
**两个都立即失败，无 commit、无工作区改动、无结束消息**。排查未果后改为自己执行，
清理了白建的 worktree 与分支。

**代价**：约 4 次工具调用的往返 + worktree 创建/清理。
**建议**：分派前先用一个极小任务探活；subagent 失败两次就切回自己执行，不要反复重试。

### 2.5 一次越界：在 `main` 上直接改代码

迁移初期我在主工作区的 `main` 分支上直接改了代码（含把 `tsconfig` 改回 strict）。
发现后立即：建分支承载改动 → 主工作区切回 `main` → 用 worktree 隔离后续工作。

**可迁移的规则**：*动手前先确认自己在哪个分支。* `git status -sb` 第一行就能看到，
成本近乎为零，但漏看的后果是把别人的 `main` 搅进自己的 WIP。

## 3. 做得好的

| 项 | 说明 |
|----|------|
| 先修根因再迁移 | 没有绕过 Slot 问题（如给 Dialog 打 mock 了事），而是重拉组件修根因——连带把 `@ts-nocheck`、`noImplicitAny`、stub 三个技术债一起还清 |
| 每步都跑门禁 | 每个组件（或每 2–3 个）迁移后立即跑相关测试 + 全量 tsc，问题在最小时暴露 |
| 共享样式表整体处理 | `SettingsRows.module.css` 被 6 个组件共用——一次性迁完 6 处再删文件，避免"删了但还有引用" |
| 顺带修既有缺陷 | 迁移时发现 `MessageActionRow` 的 tsc 错误、`statusBar` 样板与 `styles.effort` 未定义（原 CSS 里根本没这两个类），一并修掉并如实记录 |
| deviation 全程留痕 | 每处偏离 plan 的判断都写进 commit message 与 `tasks.md`，不默默改 |

## 4. 可改进的

| 项 | 建议 |
|----|------|
| 全量组件级 axe 覆盖不足 | 本次只给 1 个组件建了 a11y 测试。建议后续为每个交互组件补 axe 用例（成本低，Radix 已保证大部分） |
| 视觉回归无自动化 | 无截图基线，"视觉等价"目前只靠人工判断 + 语义类断言。若要长期保障，建议引入 Playwright `toHaveScreenshot` |
| `text-[0.7em]` 等 em 基准字号 | 保留了原 em 语义未换算成 px，避免视觉偏移；但 em 在嵌套场景下会叠加，后续可评估统一 |
| 迁移未分批提交 review | 18 个组件一次性迁完（中间有 commit 但无人工 review 点）。若再次做同类迁移，可按 batch 设 review gate |

## 5. 交付物

| 类型 | 路径 |
|------|------|
| 测试设计 | `test-design.md` |
| 用例表 | `test-cases.md` |
| 测试报告 | `test-report.md` |
| 过程复盘 | `test-review.md`（本文） |
| 项目级前端规范 | `docs/frontend-design.md` |
| OpenSpec change | `openspec/changes/shadcn-components-p2/` |

## 6. 结论

- **18/18 组件迁移完成**，`*.module.css` 从 18 → **0**
- **311 个前端用例全绿**；mvn **912 全绿**（0 jacoco 违规）；tsc **2**（基线内）
- 技术债 **C1–C7 全部偿还**（stub / `@ts-nocheck` / `noImplicitAny` / axe / hc 主题 / 主题机制 / 旧 API）
- 2 处重大误判被纠正并记录（esbuild 格式化的副作用、axe matcher 用法）
- 1 次流程越界（在 main 上改动）已纠正并记录
- **明确未覆盖**：全量组件 axe 扫描、E2E、像素级视觉回归
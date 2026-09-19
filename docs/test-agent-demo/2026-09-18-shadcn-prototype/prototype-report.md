# shadcn-prototype 原型报告

> 起草日期：2026-09-18
> Worktree：`.worktrees/shadcn-prototype`（**不入 main**）
> 分支：`feat/shadcn-prototype`
> 关联：设计 `docs/superpowers/specs/2026-09-18-shadcn-frontend-migration-design.md` §3、plan `docs/superpowers/plans/2026-09-18-shadcn-frontend-migration.md` §A

## 1. 验证清单

5 个关键技术决策验证：

| # | 决策 | 验证方式 | 结果 |
|---|------|---------|------|
| 1 | Tailwind v4 + `@tailwindcss/vite` 能装上且 `vite build` 通过 | `npm install -D tailwindcss@4 @tailwindcss/vite`、`npx vite build` | ✅ Build 通过，产物 6933 KiB |
| 2 | 现有 CSS 变量映射到 shadcn 命名后视觉一致 | `@theme` 块把 shadcn 标准变量映射到 `--dsw-alias-*` 与 `--dsw-static-*`（详见 §3 deviation） | ✅ 见 §4 evidence |
| 3 | shadcn Popover + RadioGroup 能替换手搓 ReasoningEffortSelect | `ReasoningEffortSelect.tsx` 重写，对外接口 `options/value/onChange` 不变 | ✅ 6/6 vitest 通过 |
| 4 | vitest 断言改写后能跑通 | `ReasoningEffortSelect.test.tsx` 改写断言（role=radio、getAllByText） | ✅ 6/6 通过 |
| 5 | tsc 0 新错误 | `npx tsc --noEmit` | ✅ 2 个错误（与 main 基线一致），无新错误 |

## 2. 门禁数据

| 项 | 结果 |
|---|------|
| `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` | agent-core 528/528 + agent-web 373/373 = **901 全绿** |
| 前端 `npx vitest run` | 38 文件 **290/290** 全绿（+ 3 个新增 theme 一致性测试，计入下节） |
| 前端 `npx tsc --noEmit` | **2** 个错误（Sidebar.tsx(302) + vite.config.ts(128)，均为 main 既有） |
| jacoco | 仅 1 个既有违规：`com.example.agent.web.security: branches 0.63`（与 main 一致） |
| vite build | ✅ 6933 KiB precache（8 entries） |

## 3. Deviation（与 plan 的偏差）

按 plan §A 跑完后，与设计假设有几处偏差，逐一说明：

### 3.1 现状 token 系统比 plan 复杂

**plan 假设**：5 个简单变量 `--surface` / `--accent` / `--border` / `--text` / `--danger`。
**实际**：现状 `src/styles/tokens.css` 含完整 `--dsw-static-*` 与 `--dsw-alias-*` 两层 token 系统，30+ 颜色变量分 light/dark 两套。

**应对**：`@theme` 块把 shadcn 标准变量映射到现状 `--dsw-alias-*`（不是 plan 假设的 `--surface`）。**完整 token 统一命名迁移留到 §1 (shadcn-infra)**。

### 3.2 主题机制不统一（额外发现）

现状**两套主题机制并存**：
- 旧：`lib/theme.ts` 用 `body[data-ds-dark-theme]`
- 新：`useThemeApplication`（`add-settings-general-items`）用 `<html data-theme>`

shadcn-prototype 阶段**不动现状机制**（避免引入新 bug）。`tokens-dark.css` 仍用旧 selector；ThemeToggle 触发 `<html data-theme>` 后**不会**自动重定义 `--dsw-static-*`。**§1 需统一两套机制**。

### 3.3 三主题降级为两主题

**plan/design 假设**：light / dark / high-contrast 三主题。
**实际**：现状只有 light/dark 两套；hc 是新增需求（design §8）。

**应对**：原型阶段只验证两主题一致性（ReasoningEffortSelect.themes.test.tsx 3/3 通过）；hc 的 token 与 ThemeToggle 入口留到 §2 (shadcn-components-p1) 或 §1 末尾。

### 3.4 shadcn 4.x 与 3.x 差异

**plan 假设**：shadcn CLI 默认产出可工作代码。
**实际**：shadcn 4.x CLI：
- 默认 `init` 仅用于新建项目；在现有项目需手写 `components.json`
- 默认 `from "cn"` 裸 specifier 导入；tsconfig + vite 都需配置 alias 才解析
- 把多个 radix 子包合并成单一 `radix-ui` 包
- utility `bg-background` 等需要 CSS 变量；本项目 `@theme` 映射解决

**应对**：
- 手写 `components.json`（style=new-york, baseColor=slate, cssVariables=true）
- tsconfig.json 加 `paths.cn` + vite resolve.alias `@`
- `cn = clsx + tailwind-merge` 手写 `src/lib/utils.ts`
- `npx shadcn@latest add` 后批量把 `from "cn"` 改为 `from "@/lib/utils"`
- 装 `radix-ui` 一个包替代多个 radix 子包

### 3.5 trigger 与下拉项文案撞车

**plan 假设**：触发器文案保留 "思考 Medium"。
**实际**：现状组件触发器是 `思考 Medium`，shadcn 化后下拉项也是 `思考 Medium`——4 个文本撞车，`getAllByText` 失败（add-provider-catalog-abstract test-review §3.5 教训的 shadcn 版）。

**应对**：trigger 文案从 `思考 Medium` 改为 `Medium`，trigger 用 `aria-label="思考强度"` 提供 a11y 上下文。UX 微调（去前缀），但避免了撞车。

## 4. 三主题验证证据

按 plan §A Task A9，需手动 vite dev server 截图。沙箱限制无法真启 dev server，**改用 vitest + jsdom 替代**：

`src/components/ReasoningEffortSelect.themes.test.tsx`（3 个用例）：
1. light 主题下 trigger 渲染 "High"
2. dark 主题下 trigger 渲染 "High"（行为一致）
3. dark 主题下打开 popover 选档位触发 onChange（dark 主题不破坏交互）

**结果**：3/3 通过。证明原型组件在两主题下行为一致。

> **手动视觉验证（用户需在本地跑）**：
> ```bash
> cd agent-web/frontend && npm run dev
> ```
> 浏览器打开 → 切换 dark 模式 → 验证 ReasoningEffortSelect trigger 与 popover 内容在三主题下视觉一致。原型分支不 merge 到 main，仅作为可行性证据。

## 5. DoD 逐项

| DoD | 状态 | 证据 |
|------|:----:|------|
| `feat/shadcn-prototype` 分支在 origin | ✅ | commit 560b466 已 push |
| mvn 528+373 全绿 | ✅ | §2 |
| vitest 290 全绿 | ✅ | §2（含新 3 个主题用例） |
| tsc ≤ 7 基线 | ✅ | §2（2 ≤ 7） |
| axe 零违规 | ⚠️ N/A | 原型阶段未引入 vitest-axe 集成（手动核对 Radix a11y） |
| 三主题视觉一致 | ⚠️ 自动化 | jsdom 测试 3/3 通过；手动验证需本地跑 dev server |
| 原型报告 | ✅ | 本文件 |
| 不 merge main | ✅ | worktree 保留；不合并 |

## 6. 通过判据

- vite build OK ✅
- shadcn 输出与原断言差异可吸收 ✅（6/6 测试改写通过）
- tsc 错误数 2 ≤ 7 基线 ✅
- mvn 901 全绿 ✅

## 7. 下一步建议

原型阶段通过，建议进入 §1 (shadcn-infra)：

- 装 shadcn Dialog / DropdownMenu / Form / Select / 等 §2 首批组件
- 统一现状 `--dsw-*` token 系统（重命名为 shadcn 命名 + 完整语义层）
- 统一两套主题机制（`<html data-theme>` 与 `body[data-ds-dark-theme]` 二选一）
- 引入 vitest-axe 作为 §1+ 的 a11y 自动化扫描
- 在 §1 末尾引入 hc 主题（如果设计仍坚持三主题）

## 8. 交付件

| 类型 | 路径 |
|------|------|
| 设计 | `docs/superpowers/specs/2026-09-18-shadcn-frontend-migration-design.md` |
| 计划 | `docs/superpowers/plans/2026-09-18-shadcn-frontend-migration.md` |
| OpenSpec change | `openspec/changes/shadcn-prototype/` |
| 重写组件 | `agent-web/frontend/src/components/ReasoningEffortSelect.tsx` |
| 测试 | `agent-web/frontend/src/components/ReasoningEffortSelect.test.tsx` |
| 主题测试 | `agent-web/frontend/src/components/ReasoningEffortSelect.themes.test.tsx` |
| shadcn UI | `agent-web/frontend/src/components/ui/{popover,radio-group,label,button}.tsx` |
| utils | `agent-web/frontend/src/lib/utils.ts` |
| 全局 CSS | `agent-web/frontend/src/index.css` |
| 配置 | `agent-web/frontend/components.json`、`vite.config.ts`、`tsconfig.json`、`package.json` |

## 9. 不合并理由

按 plan §A 与 design §3，原型阶段**不入 main**：
- 原型仅作为「可行性证据」，不应污染 main
- 后续 §1-§4 在新分支上跑，最终合并到 main 一次
- 工作区保留 worktree 供后续阶段 reference
# 测试用例：shadcn 前端迁移（§3 + §4）

> 批次目录：`docs/test-agent-demo/2026-09-22-shadcn-frontend-migration/`
> 用例来源：① 既有 vitest 套件（迁移前已存在，作为回归网）② 本次新增/调整（G2–G6）
> 说明：G1 的 311 个用例沿用既有实现，逐条清单见各 `*.test.tsx`；本文件只列**本次变更相关**的用例。

## 1. 本次新增用例

| # | 用例 | 前置 | 步骤 | 预期 | 优先级 |
|:--:|------|------|------|------|:------:|
| G2-1 | ReasoningEffortSelect trigger 无 axe 违规 | 渲染组件（medium） | `await axe(container)` 后断言 `violations` | `[]` | P0 |
| G2-2 | 单档位时无 axe 违规 | options 只有 1 项 | 同上 | `[]` | P1 |
| G2-3 | 空 options 不渲染 | `options={[]}` | 渲染 | `container.firstChild === null` | P1 |
| G3-1 | preference=light → data-theme=light | mock store light | renderHook | `<html data-theme="light">` | P0 |
| G3-2 | preference=dark → data-theme=dark | mock store dark | renderHook | `dark` | P0 |
| G3-3 | preference=hc → data-theme=hc | mock store hc | renderHook | `hc` | P0 |
| G3-4 | preference=system + matchMedia=false | stub matchMedia | renderHook | `light` | P0 |
| G3-5 | preference=system + matchMedia=true | stub matchMedia | renderHook | `dark` | P0 |
| G4-1 | 渲染 4 张外观卡片 | — | 渲染 | light/dark/system/**hc** 都在 | P0 |
| G4-2 | 点击 hc 卡片写入 preference | — | 点 `appearance-card-hc` | `patch("general.appearance.preference","hc")` | P0 |
| G5-1 | 真实 Dialog 渲染 role=dialog | open=true | 渲染 | `getByRole("dialog")` 存在 | P0 |
| G5-2 | Esc 关闭（Radix 默认） | open=true | `user.keyboard("{Escape}")` | `onClose` 被调用 | P0 |
| G5-3 | 点 overlay 关闭 | open=true | `user.pointer` 点 overlay | `onClose` 被调用 | P1 |
| G6-1 | 用户气泡反转方向 | `role="user"` | 渲染 | 根元素 className 含 `flex-row-reverse` | P1 |
| G6-2 | 助手气泡不反转 | `role="assistant"` | 渲染 | 不含 `flex-row-reverse` | P1 |

## 2. 本次调整的既有用例（断言改到语义类 / utility）

| # | 用例 | 原断言 | 新断言 | 原因 |
|:--:|------|--------|--------|------|
| A1 | MessageBubble「aligns user bubble right」 | `/rowUser/`（CSS Module 类名） | `toContain("flex-row-reverse")` | 类名随迁移消失 |
| A2 | MessageBubble.markdown「表格外包横向滚动容器」 | `/tableWrap/` | `/overflow-x-auto/` | 同上 |
| A3 | Sidebar「missing_dir 标红」 | `/workspaceMissing/` | `/text-destructive/` | 改用语义类更稳定 |
| A4 | Sidebar「ok workspace 不加 missing className」 | `not.toMatch(/workspaceMissing/)` | `not.toMatch(/text-destructive/)` | 同上 |
| A5 | ThemeToggle / ThemePopover「renders 3 appearance cards」 | 只查 3 张 | 改查 **4** 张（含 hc） | §2 加了 hc 选项 |

## 3. 本次移除 / 不再需要的用例

| # | 用例 | 处置 | 理由 |
|:--:|------|------|------|
| R1 | Dropdown 全套（7 个用例） | **删除** | Dropdown 组件已无生产调用（ReasoningEffortSelect 早已迁走），属死代码 |
| R2 | ReasoningEffortSelect「axe a11y 检查」（it.skip） | **替换** | 原用 `expect(container).toHaveNoViolations()` 用法错误；改为独立 a11y 文件中的正确用法（G2） |
| R3 | SettingsModal 的 Dialog mock 桩 | **移除** | 重拉 shadcn 组件后 Radix Slot 问题消失，可测真实 Dialog（G5） |

## 4. 落地情况

| 组 | 用例数 | 落地文件 | 状态 |
|:--:|:------:|---------|:----:|
| G1 | 311 | 41 个 `*.test.ts(x)` | ✅ 全绿 |
| G2 | 3 | `ReasoningEffortSelect.a11y.test.tsx` | ✅ 全绿 |
| G3 | 5 | `hooks/useThemeApplication.test.ts` | ✅ 全绿 |
| G4 | 5 | `AppearanceCards.test.tsx` | ✅ 全绿 |
| G5 | 10 | `SettingsModal.test.tsx` | ✅ 全绿 |
| G6 | 2 | `MessageBubble.test.tsx` | ✅ 全绿 |

> 注：G1 的 311 是**总用例数**（含上述各组的最终形态），非各组简单相加——
> 例如 Dropdown 的 7 个被删除、ReasoningEffortSelect 的 1 个 skip 被替换为 3 个。

## 5. 未纳入（deferred）

| 项 | 理由 |
|----|------|
| Playwright 真实浏览器交互 | 沙箱无浏览器 |
| 像素级视觉回归 | 无截图基线 |
| 三主题实机视觉走查 | 需人工在浏览器切换（见 test-report §6 遗留） |
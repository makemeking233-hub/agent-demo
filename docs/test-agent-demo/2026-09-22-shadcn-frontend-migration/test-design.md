# 测试设计：shadcn 前端迁移（§3 + §4）

> 批次目录：`docs/test-agent-demo/2026-09-22-shadcn-frontend-migration/`
> 关联 change：`openspec/changes/shadcn-components-p2`
> 执行分支：`feat/shadcn-components-p2`（worktree `.worktrees/shadcn-components-p2`）

## 1. 测试目标

验证把前端从 **CSS Modules** 全量迁移到 **Tailwind v4 + shadcn/ui** 后：

1. **行为不变**：所有既有组件的行为契约（props / 回调 / role / aria / testid）保持；
2. **无 CSS Modules 残留**：`*.module.css` 全部删除，且无 `.tsx` 再引用；
3. **主题机制单一**：`<html data-theme>` 是唯一来源，三套主题（light/dark/hc）变量完整；
4. **类型严格**：`strict: true` 完整生效（无 `noImplicitAny` 覆盖、无 `@ts-nocheck`）；
5. **a11y 可自动验证**：axe 能真实跑通（纠正此前"jsdom 不支持"的误判）。

## 2. 测试范围

### 2.1 纳入

| 类别 | 内容 |
|------|------|
| 单元测试（既有） | 41 个 vitest 文件、311 个用例（迁移后全绿） |
| 类型检查 | `npx tsc --noEmit` |
| 构建 | `npx vite build` |
| 后端回归 | `mvn -o -pl agent-core,agent-web verify` |
| a11y | `vitest-axe` 对迁移后组件的扫描 |

### 2.2 排除（附理由）

| 项 | 理由 |
|----|------|
| Playwright E2E | 沙箱无真实浏览器（本项目长期 deferred） |
| 真实视觉像素对比 | 无截图基线设施；改用「语义等价 + 组件行为」验证 |
| 多 provider HTTP 路由 | v0.2 未实现（设计明确 out of scope） |

## 3. 测试策略

### 3.1 迁移类改动的验证原则

样式迁移的**风险不是逻辑错误，而是"看不见的视觉回归"**。因此策略是：

1. **以既有测试为回归网**：311 个用例覆盖了组件行为（点击、回调、条件渲染、
   键盘交互）。迁移只改 `className`，理论上全部应保持通过。
2. **失败即信号**：测试因 class 名断言失败时，**改断言到语义类，不改测试意图**。
   全过程记录了 5 处此类断言调整（见 test-report §4）。
3. **类型与构建兜底**：`tsc` 抓引用错误（如残留 `styles.X`）；`vite build` 抓
   CSS/JSX 解析错误。

### 3.2 a11y 策略（重要纠正）

初期结论「axe 在 jsdom 下不可用」是**误判**，根因是 matcher 用法错误：

```ts
// ❌ 错：toHaveNoViolations 接收 axe 结果对象，不是 DOM 元素
expect(container).toHaveNoViolations();

// ✅ 对
const results = await axe(container);
expect(results.violations).toEqual([]);
```

纠正后 axe 在 jsdom 下**正常工作**（3 个用例通过），因此 `happy-dom` 依赖被移除。

### 3.3 门禁命令

```bash
# 前端
cd agent-web/frontend
npx vitest run
npx tsc --noEmit
npx vite build

# 后端 + 集成
mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**
```

## 4. 用例矩阵

| 组 | 覆盖对象 | 用例数 | 来源 |
|:--:|---------|:------:|------|
| G1 | 既有 41 个 vitest 文件（全量回归） | 311 | 迁移前既有 |
| G2 | a11y 扫描（`ReasoningEffortSelect.a11y.test.tsx`） | 3 | 本次新增 |
| G3 | 主题机制（`useThemeApplication.test.ts`） | 5 | §2 新增 |
| G4 | 四主题卡片（`AppearanceCards.test.tsx` 含 hc） | 5 | §2 + 本次改 |
| G5 | SettingsModal 真实 Dialog（去掉 mock） | 10 | §3 改 |
| G6 | MessageBubble 用户/助手方向 | 2 | 本次改 |
| G7 | 类型检查 | — | `tsc --noEmit` |
| G8 | 构建 | — | `vite build` |

## 5. 退出标准（DoD）

| # | 标准 | 判定 |
|:--:|------|------|
| 1 | vitest 全绿 | `Tests  311 passed`，0 failed |
| 2 | tsc 不超基线 | 错误数 ≤ 7（实际 **2**） |
| 3 | vite build 成功 | `✓ built` |
| 4 | mvn verify 全绿 | agent-core + agent-web 全通过，0 jacoco 违规 |
| 5 | 无 CSS Modules | `components/*.module.css` 数量 = **0**；无 `.tsx` 引用 |
| 6 | 无类型逃逸 | `@ts-nocheck` = 0；`noImplicitAny` 无覆盖 |
| 7 | a11y 可跑 | axe 用例通过（非 skip） |

## 6. 环境

| 项 | 值 |
|----|-----|
| JDK | 17 |
| Maven | 3.6.1（离线 `-o`） |
| Node | 本机 LTS |
| 测试环境 | jsdom（vitest 默认） |
| 隔离 | worktree `.worktrees/shadcn-components-p2` |

## 7. 数据隔离

- 全部为**纯单元测试**（Mockito / jsdom + mock api），不启真实 Spring 应用。
- `WebIntegrationTest` 已由既有机制把 agent home 指向 `target/test-data`。
- 本次未产生需清理的用户数据（见 test-report §7）。
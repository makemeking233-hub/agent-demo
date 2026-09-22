# 前端设计规范（agent-demo）

> 适用范围：`agent-web/frontend/`
> 建立于 shadcn-frontend-migration（2026-09-18 起），随 §4 `shadcn-cleanup` 落地。
> 关联设计：`docs/superpowers/specs/2026-09-18-shadcn-frontend-migration-design.md`

## 1. 技术栈

| 层 | 选型 | 说明 |
|----|------|------|
| 框架 | React 18 + TypeScript | 无 Next.js / SSR；纯 Vite SPA |
| 构建 | Vite 6 | `vite.config.ts` 注册 `@tailwindcss/vite` |
| 样式 | **Tailwind v4（utility-first）** | 配置在 CSS（`@theme`），无 `tailwind.config.js` |
| 组件 | **shadcn/ui**（基于 Radix UI） | 源码落地在 `src/components/ui/`，可直接改 |
| 图标 | `lucide-react` | 不用 `react-icons` |
| 类名合并 | `clsx` + `tailwind-merge`（封装为 `cn()`） | `src/lib/utils.ts` |
| 变体 | `class-variance-authority` | shadcn 组件内部使用 |

**已废除**：CSS Modules（`*.module.css`）。迁移完成后仓库内 **0 个** `.module.css`。

## 2. 样式约定

### 2.1 颜色：只走 shadcn 语义类

`src/index.css` 的 `@theme` 块把 shadcn 语义变量映射到项目既有的 `--dsw-*` token 体系。

| 用途 | ✅ 用 | ❌ 不用 |
|------|------|--------|
| 页面/卡片底色 | `bg-background` / `bg-card` | `bg-white`、`bg-[#fff]` |
| 主文字 | `text-foreground` | `text-gray-900` |
| 次要文字 | `text-muted-foreground` | `text-gray-500` |
| 边框 | `border-border` | `border-gray-200` |
| 主色 | `bg-primary` / `text-primary` / `border-primary` | `bg-blue-600` |
| 危险 | `text-destructive` / `bg-destructive/10` | `text-red-600` |
| 警告 / 成功 | `text-warning` / `text-success` | 自定义色值 |
| 浅强调底 | `bg-accent-subtle` | `bg-blue-50` |

**禁止硬编码十六进制颜色**（`bg-[#2563eb]`）。若确需新语义色，先在 `@theme` 里登记。

### 2.2 间距与尺寸

- Tailwind 间距尺 **1 单位 = 0.25rem = 4px**：原 CSS `8px` → `p-2`；`12px` → `p-3`。
- 小于 4px 或非整倍数的值用任意值语法：`gap-[6px]`、`text-[13px]`。
- 字号低于 Tailwind 默认档（`text-xs` = 12px）时用任意值：`text-[11px]`、`text-[13px]`。

### 2.3 复合样式用 `@layer components` + `@apply`

当一段样式需要作用在**多个后代元素**上（如 Markdown 正文的 `h1`–`h6` / `table` / `pre`），
不要堆砌 utility 到每个子元素，而是在 `src/index.css` 的 `@layer components` 里定义复合类：

```css
@layer components {
  .prose-sm p { @apply mb-2; }
  .prose-sm h1 { @apply mt-3 mb-1.5 text-xl font-semibold; }
}
```

现有复合类：`.prose-sm`（Markdown 正文）。

### 2.4 主题变量求值陷阱（重要）

`--dsw-alias-*` 在 `tokens.css` 的 `:root` 声明，其 `var()` **在声明作用域就求值**。
后代作用域重定义 `--dsw-static-*` **不会**让别名跟着变。

因此每个主题覆盖文件**必须同时重新声明别名层**：

- `src/styles/tokens-dark.css` → `:root[data-theme="dark"]`
- `src/index.css` → `:root[data-theme="hc"]`

漏掉别名会导致组件读到的仍是亮色值（历史上真实踩过）。

## 3. 主题系统

三套主题，选择器统一为 `<html data-theme="...">`：

| 主题 | 值 | 覆盖文件 |
|------|----|---------|
| 浅色（默认） | 无属性 / `light` | `src/styles/tokens.css` |
| 深色 | `dark` | `src/styles/tokens-dark.css` |
| 高对比度 | `hc` | `src/index.css`（WCAG AAA，正文 ≥7:1） |

**驱动**：`src/hooks/useThemeApplication.ts` 读 settings store 的
`general.appearance.preference`（`light` / `dark` / `system` / `hc`）写入 `data-theme`。

**已废除**：`body[data-ds-dark-theme]` 属性与 `src/lib/theme.ts` 全套旧 API。

## 4. 组件约定

### 4.1 目录

```text
src/components/
├── ui/                    # shadcn 原始组件（button / dialog / select / ...）
├── <Feature>.tsx          # 业务组件（PascalCase）
└── <Feature>.test.tsx     # 同目录单测
```

### 4.2 shadcn 组件再生成注意

用 `npx shadcn@latest add <name> --yes --overwrite` 重新拉取组件后，**必须**手工修两处：

1. `from "cn"` → `from "@/lib/utils"`（CLI 默认输出裸 specifier，Vite 解析不了）
2. 形如 `from "src/components/ui/x"` → `from "@/components/ui/x"`

> **不要**用 esbuild/babel 重新格式化 shadcn 的 JSX 输出来"修语法"——
> 它会把 JSX 压成多个文本节点（如 `asChild` 的 children 变成
> `[" ", <Button/>, " "]`），破坏 Radix Slot 的「单个元素子节点」要求，
> 导致 Dialog 等在 jsdom 下渲染失败。历史上因此误判为"测试环境限制"。

### 4.3 a11y

- 优先用 shadcn（Radix）组件，focus trap / Esc / 外点击关闭 / 焦点还原**自动**具备。
- Icon-only 按钮必须给 `aria-label`。
- 表单元素必须有 `aria-label` 或关联 `<label htmlFor>`。

### 4.4 测试

- `vitest` + `jsdom` + `@testing-library/react`，setup 在 `src/vitest.setup.ts`。
- **断言优先用 `role` / `testid` / 文本**，不要太依赖 class 名（utility 顺序易变）。
  确需断言样式时，断言语义类（如 `text-destructive`）而非具体 utility 组合。
- a11y 扫描用 `vitest-axe`：

  ```ts
  import { axe } from "vitest-axe";
  const results = await axe(container);
  expect(results.violations).toEqual([]);
  ```

  > `toHaveNoViolations()` matcher 接收的是 **axe 结果对象**，不是 DOM 元素；
  > 直接断言 `violations` 更直白且无需类型增强。

## 5. 依赖清单（新增时对照）

| 包 | 用途 |
|----|------|
| `tailwindcss` / `@tailwindcss/vite` | 样式引擎 + Vite 插件 |
| `radix-ui` | shadcn 底层原语（单一包，不再拆多个 `@radix-ui/*`） |
| `class-variance-authority` / `clsx` / `tailwind-merge` | 类名工具 |
| `lucide-react` | 图标 |
| `react-markdown` / `remark-gfm` / `remark-math` / `rehype-highlight` / `rehype-katex`(懒加载) | Markdown 渲染 |
| `sonner` / `next-themes` | Toast（sonner 组件依赖 next-themes 取主题） |
| `vitest-axe` / `axe-core` | a11y 扫描（仅 devDep） |

## 6. 迁移遗留与已知限制

| 项 | 状态 |
|----|------|
| `text-[0.7em]` 等 em 基准字号 | 保留原语义，未换算成 px（避免视觉偏移） |
| Mermaid / KaTeX 懒加载 | 未动（按需 chunk 策略与样式迁移无关） |
| E2E（Playwright） | 沙箱不可跑，长期 deferred |

---

> 修订记录：
> - v1.0（shadcn-cleanup）：初版；记录 utility-first 约定、主题变量求值陷阱、
>   shadcn 再生成注意事项、a11y 与测试约定
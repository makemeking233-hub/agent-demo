# 测试报告：shadcn 前端迁移（§3 + §4）

> 批次目录：`docs/test-agent-demo/2026-09-22-shadcn-frontend-migration/`
> 执行日期：2026-09-22
> 执行分支：`feat/shadcn-components-p2`

## 1. 执行结果总览

| # | 命令 | 结果 | 数值 |
|:--:|------|:----:|------|
| 1 | `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` | ✅ **BUILD SUCCESS** | agent-core **533** + agent-web **379** = **912 全绿**；0 个 jacoco 违规 |
| 2 | `cd agent-web/frontend && npx vitest run` | ✅ | 41 文件 / **311 passed**（0 failed） |
| 3 | `cd agent-web/frontend && npx tsc --noEmit` | ⚠️ 2 个错误 | **2 ≤ 基线 7** |
| 4 | `cd agent-web/frontend && npx vite build` | ✅ | `✓ built` |

## 2. 迁移完成度

| 指标 | 迁移前 | 迁移后 |
|------|:------:|:------:|
| `components/*.module.css` 数量 | 21 | **0** |
| `.tsx` 引用 `.module.css` | 21 处 | **0 处** |
| `// @ts-nocheck`（ui 组件） | 5 | **0** |
| `noImplicitAny` 覆盖 | 有（false） | **无**（完整 strict） |
| `lib/theme.ts` 旧 API | 存在 | **已删除** |
| 主题机制 | 双轨（`body[data-ds-dark-theme]` + `<html data-theme>`） | **单轨**（`<html data-theme>`） |
| hc 主题变量 | 13 个占位 | **全部 `--dsw-static-*` + 别名层** |
| axe 用例 | 1 个 skip | **3 个通过** |

## 3. tsc 的 2 个错误（均为既有基线）

```
src/components/Sidebar.tsx(454,11): error TS2322: Type '(path: string) => void' is not assignable to type '(path: string) => Promise<void>'.
vite.config.ts(128,3): error TS2769: No overload matches this call.
```

两项在迁移前即存在（属项目 §2.7.7 记录的基线构成），**非本次引入**。

> 附带修复：迁移前基线实为 3 个——第 3 个是 `MessageActionRow.tsx(52,7)`
> （他人 commit 引入，`ReturnType<typeof setTimeout>` 在 `@types/node` 下解析成
> `NodeJS.Timeout`，与 `window.setTimeout` 的 `number` 冲突）。迁移时一并修掉，
> 错误数从 3 降回 **2**。

## 4. 缺陷清单

### 4.1 本次修复（迁移过程中发现）

| # | 现象 | 根因 | 修复 |
|:--:|------|------|------|
| D1 | `Dialog` 在 jsdom 与 happy-dom 下均渲染失败：`Primitive.div failed to slot onto its children` | §1 用 esbuild 重新格式化 shadcn 4.x 单行输出，把 JSX 压成多个文本节点（`asChild` 的 children 变成 `[" ", <Button/>, " "]`），违反 Radix Slot 的单子节点要求 | 从 registry 重拉全部 11 个组件（CLI 输出规范多行 JSX） |
| D2 | `toHaveNoViolations` 报 `Unexpected aXe results object` | **matcher 用法错误**——它接收 axe 结果对象，不是 DOM 元素 | 改为 `const r = await axe(el); expect(r.violations).toEqual([])` |
| D3 | `from "cn"` 无法解析 | shadcn CLI 输出裸 specifier | 批量改为 `from "@/lib/utils"` |
| D4 | `from "src/components/ui/x"` 无法解析 | 同上 | 改为 `@/components/ui/x` |
| D5 | 5 个组件 `Binding element 'className' implicitly has an 'any' type` | 同样是 esbuild 剥离类型注解的后果 | 重拉组件后消失 |
| D6 | `MessageActionRow.tsx` tsc TS2322 | `ReturnType<typeof setTimeout>` 与 `window.setTimeout` 返回类型冲突 | `useRef<number \| null>(null)` |
| D7 | 6 个测试因断言 CSS Module 类名失败 | 类名随迁移消失 | 改断言语义类 / utility（见 test-cases §2） |

### 4.2 遗留（不在本 change 范围）

| # | 内容 | 处置 |
|:--:|------|------|
| L1 | Playwright E2E 不可跑 | 沙箱无浏览器，长期 deferred |
| L2 | 三主题实机视觉走查 | 需人工在浏览器切换确认（自动化只验证 `data-theme` 写入与变量定义） |
| L3 | `form.tsx` / `sonner.tsx` 当前无业务使用 | 已具备完整实现，待后续接入 |

## 5. a11y 扫描结果

`vitest-axe` 对 `ReasoningEffortSelect`（迁移后的 shadcn Popover + RadioGroup）：

| 用例 | violations |
|------|:----------:|
| trigger 渲染（options 3 项） | `[]` |
| 单档位渲染 | `[]` |
| 空 options 不渲染 | 不适用（`firstChild === null`） |

**结论**：迁移后的组件无 axe 违规。

> 全量组件级的 axe 扫描**未做**——本次只为 1 个代表性组件建了 a11y 测试文件。
> 其余组件的 a11y 依赖 Radix 原语的默认行为（shadcn 组件本身即基于 Radix）。
> 这是本批次的一个**明确未覆盖项**。

## 6. 覆盖率

本次未改变后端逻辑，jacoco 与迁移前一致。前端无覆盖率门禁配置。

| 模块 | 门禁 | 结果 |
|------|------|------|
| agent-core | LINE≥80% / BRANCH≥70% | ✅ 全包通过 |
| agent-web | 同上 | ✅ 全包通过（**0 违规**） |

## 7. 数据隔离声明（全局规则 §10）

- 本次测试**全部为纯单元测试**（Java Mockito mock、前端 jsdom + mock api），
  不启动真实 Spring 应用、不写 `~/.agent-demo` 真实数据目录。
- 唯一启动完整 `WebApplication` 的是既有 `WebIntegrationTest`，其 `static` 块已把
  agent home 指向 `target/test-data`（本次未改动该文件）。
- 迁移过程中**未新增**任何会写用户数据目录的测试。

**未产生需清理的用户数据。**

## 8. 证据（命令输出摘录）

```
# 门禁 1
[INFO] Tests run: 533, Failures: 0, Errors: 0, Skipped: 0      <- agent-core
[INFO] Tests run: 379, Failures: 0, Errors: 0, Skipped: 0      <- agent-web
[INFO] BUILD SUCCESS

# 前端单测
Test Files  41 passed (41)
     Tests  311 passed (311)

# 类型检查
src/components/Sidebar.tsx(454,11): error TS2322: ...
vite.config.ts(128,3): error TS2769: ...
（2 个，均既有；基线 7）

# 构建
✓ built in 53.85s

# CSS Modules 残留检查
$ ls agent-web/frontend/src/components/*.module.css
（无匹配）
$ grep -rn "\.module\.css" agent-web/frontend/src/components --include=*.tsx
（仅 1 处注释文本，无实际 import）
```
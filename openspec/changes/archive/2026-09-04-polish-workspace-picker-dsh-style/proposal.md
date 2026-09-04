## Why

`add-workspace-picker-modal`（已完成 archive）实现的 `WorkspacePickerModal` 是单栏条目列表布局，跟 DSH 的"左右双栏 + 顶部导航 + 底部路径"文件选择器对比视觉差距明显——用户反馈希望 Modal 视觉风格贴近 DSH。

本次变更把 Modal 重写为 DSH 风格：顶部 ←/→/↑ 导航 + 面包屑、左侧快速访问导航树（家目录 + Desktop/Documents/Downloads + 此电脑 + 盘符）、右侧带列头排序的文件列表、底部路径框 + name 输入 + 提交按钮。同时新增后端 `/api/fs/quick-access` 接口支持左侧导航树的数据来源。

## What Changes

- **后端新增 `GET /api/fs/quick-access`**：返回 `[{name, path}]` 列表（Home + Desktop + Documents + Downloads，按平台探测）；路径经 `HomePathGuard` 锁家目录校验，不存在的目录跳过。
- **后端新增 DTO `FsQuickAccessResponse`**：`{items: [{name, path}]}`。
- **后端扩展 `FsController` 测试**：新增 3-4 个用例（正常返回 + Desktop 不存在跳过 + 路径越界跳过）。
- **前端 `WorkspacePickerModal` 重写布局**：
  - 标题改为英文 `Select Workspace Directory`（中文保留作 aria-label）。
  - 顶部：← / → / ↑ 三个按钮 + 面包屑 + `显示隐藏` 工具栏按钮。
  - 主区域：左侧导航树（快速访问 + 此电脑盘符，可折叠展开）+ 右侧文件列表（列头 name/mtime/type 可点击排序）。
  - 底部：路径框（DSH 风格"文件夹:"标签）+ 工作区名称输入框（贴近路径框，紧凑布局）+ 取消/选择此目录按钮。
- **前端 `WorkspacePickerModal` state 扩展**：
  - 新增 `history: string[]` + `historyIndex: number`（栈深 50，上限后弹栈底）。
  - 新增 `sortBy: 'name'|'mtime'|'type'` + `sortDir: 'asc'|'desc'`（默认 name/asc）。
  - 新增 `quickAccess: FsQuickAccessItem[]`（左导航树用）。
  - 列头排序在前端按 `[{name,mtime,type}]` 计算（不调后端）。
- **前端 `api/fs.ts` 新增 `getQuickAccess()`**：对齐后端接口，3-4 个 vitest 用例。
- **前端 `WorkspacePickerModal.test.tsx` 扩展用例**：history 栈前进/后退、列头点击排序、左导航树点击跳转、底部路径框可直接编辑。
- **前端 `Sidebar.test.tsx` 不变**：集成测试仍通过。

无破坏性变更（BREAKING）：`POST /api/workspaces { name, dir }` 接口形态不变；`/api/fs/list|mkdir|home|drives` 接口形态不变。

## Capabilities

### New Capabilities

无（前后端改动均归入现有 web-ui 能力）。

### Modified Capabilities

- `web-ui`：在现有 spec 追加 6 个新 Requirement，覆盖「左侧导航树」「顶部前进后退」「列头排序」「history 栈」「标题英文 + aria-label 中文」「底部路径框 + name 紧凑布局」。

## Impact

- **后端（新增）**：
  - `FsController` + `FsQuickAccessResponse`（含内嵌 `FsQuickAccessItem`）。
  - `HomePathGuard` 不变；quick-access 路径复用 `guard.homeRealPath()` 校验前缀。
  - 新增 3-4 个 `FsControllerTest` 用例。
- **前端（重写）**：
  - `WorkspacePickerModal.tsx` 主文件 ~600 行（重写 + 加 history/sort/quickAccess state）。
  - `WorkspacePickerModal.module.css` 重写布局（左右双栏 + 顶部 + 底部）。
  - `WorkspacePickerModal.test.tsx` 现有 14 个用例保留 + 新增 6 个。
  - `api/fs.ts` + `api/fs.test.ts` 加 `getQuickAccess`。
- **集成测试**：`Sidebar.test.tsx` 端到端集成测试不变。
- **覆盖**：LINE ≥ 80% / BRANCH ≥ 70% jacoco 门禁仍需通过。
- **测试文档**：新增 `docs/test-agent-demo/2026-09-04-workspace-picker-v2/` 四件套 + test-guide.md §2.7 追加。
- **不引入新依赖**：纯 React + Tailwind v4 + lucide-react（已用）。

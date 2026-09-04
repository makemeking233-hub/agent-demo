# `2026-09-04-workspace-picker-v2/` — 测试用例清单

## 1. 后端：FsControllerTest 新增（4 用例）

| 编号 | 端点 | 场景 | 期望 |
|---|---|---|---|
| FQ-01 | GET /quick-access | 始终含 Home | items[0] === Home |
| FQ-02 | GET /quick-access | 含 Desktop + Documents 跳过 Downloads | items 含 Home/Desktop/Documents，不含 Downloads |
| FQ-03 | GET /quick-access | 仅 Home（家目录下无快速访问目录） | items.length === 1 |
| FQ-04 | GET /quick-access | 路径都是绝对路径 | 每条 path 都 `Path.isAbsolute()` |

## 2. 前端：fs.test.ts 新增（4 用例）

| 编号 | 函数 | 场景 | 期望 |
|---|---|---|---|
| FQ-05 | getQuickAccess | 200 含 3 items | items.length === 3 + 名字正确 |
| FQ-06 | getQuickAccess | 仅 Home | items.length === 1 |
| FQ-07 | getQuickAccess | 403 host_not_trusted | 抛 FsError |
| FQ-08 | getQuickAccess | 5xx | 抛 FsError(code=unknown) |

## 3. 前端：WorkspacePickerModal.test.tsx 新增（6 用例）

| 编号 | 类别 | 场景 | 期望 |
|---|---|---|---|
| WP-15 | aria-label | 打开 Modal | role=dialog aria-label="选择工作区目录" |
| WP-16 | 导航树 | Home + Documents 渲染 | tree 含 Home + Documents |
| WP-17 | history | 后退/前进边界 | 初始 back/forward 都 disabled；双击进入后 back 启用 |
| WP-18 | 列头排序 | 点击 修改时间 列头 | 文本含 ↑；再点变 ↓ |
| WP-19 | 路径框 | Enter `/etc/passwd` | 显示"家目录范围内"错误条 |
| WP-20 | 显示隐藏 | 点击 Eye 图标 | listDir 调 (HOME, true) |

## 4. 回归

- 后端既有 149 + 既有核心 322 = 471 个非 E2E 测试
- 前端既有 76（14 Modal + 12 fs + 9 Sidebar + 41 其他）
- E2E（UiLayoutE2ETest / MultiTurnE2ETest / ThemeToggleE2ETest）需要 Chrome GUI 环境，不在本次 verify 范围

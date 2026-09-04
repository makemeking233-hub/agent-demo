## Context

agent-demo 当前 Web UI 在新建工作区时弹出 `WorkspacePickerModal`，该 Modal 由 `add-workspace-picker-modal`（2026-09-04 archive）实现，采用**单栏条目列表**布局。用户对比 DSH 的 `Select Workspace Directory`（左右双栏 + 顶部导航 + 底部路径）认为视觉差距明显，反馈希望 Modal 视觉风格贴近 DSH。

DSH 是桌面应用（Tauri/Electron）能调原生 `showOpenDialog`，agent-demo 是 Web 应用做不到。本 change 在 Web 端尽量**视觉**贴近 DSH，同时保持现有安全边界（家目录锁 + trusted-host）和 name 输入灵活性。

## Goals / Non-Goals

**Goals：**

- Modal 布局重写为"顶部 ←/→/↑ + 面包屑 + 工具栏 + 主区域（左导航树 + 右文件列表）+ 底部路径框 + name 框 + 按钮"。
- 左侧导航树包含：Home + Desktop + Documents + Downloads + 此电脑盘符列表（Windows 限定）。
- 顶部 ← / → 有 history 栈（上限 50 步）+ ↑ 上一级。
- 右侧文件列表列头（name / mtime / type）可点击排序，默认 name asc。
- 后端新增 `GET /api/fs/quick-access` 返回快速访问目录列表，路径经 `HomePathGuard` 校验。
- 标题英文（"Select Workspace Directory"），aria-label 中文（"选择工作区目录"）。
- jacoco 门禁（LINE ≥ 80% / BRANCH ≥ 70%）仍通过。

**Non-Goals：**

- 不引入 OS 原生文件选择对话框（Web 端不可能）。
- 不引入 File System Access API（浏览器安全模型不允许拿绝对路径，对我们场景无价值）。
- 不支持搜索框（避免后端递归搜索接口复杂度）。
- 不支持多选目录（单选符合现有 `WorkspaceStore.create` 接口）。
- 不支持文件预览缩略图。
- 不持久化 history 栈（每次开 Modal 重新开始）。
- 不持久化左侧导航树折叠状态（用户偏好 v0.x 不做）。

## Decisions

### D1：左导航树数据来源——后端 quick-access API 而非前端硬编码

**理由**：家目录下的 Desktop / Documents / Downloads 路径在不同平台 / 不同 Windows 版本可能命名不同（中文系统 vs 英文系统，Mac 是 `~/Documents` 而非 `~/文档`）。让后端探测返回，前端只渲染。

**实现**：`GET /api/fs/quick-access` 返回：
```json
{
  "items": [
    {"name": "Home", "path": "C:\\Users\\86184"},
    {"name": "Desktop", "path": "C:\\Users\\86184\\Desktop"},
    {"name": "Documents", "path": "C:\\Users\\86184\\Documents"},
    {"name": "Downloads", "path": "C:\\Users\\86184\\Downloads"}
  ]
}
```
探测逻辑：`Path.of(System.getProperty("user.home"), subdir)` + `Files.isDirectory()`，不存在的跳过。

**考虑过**：前端硬编码 `${home}/Desktop` 等。否决：硬编码在跨平台 / 中文 Windows 上有 break 风险，且测试场景需要 mock 不同平台的 home 结构。

### D2：history 栈纯前端，不持久化

**理由**：每次 Modal 打开从 localStorage 记住的位置或 home 开始，不需要保留跨 session 的前进/后退；history 只在 Modal 生命周期内有效，关闭时清空。

**实现**：reducer 加 `history: string[]` + `historyIndex: number`。导航时：
- 进入新目录（双击 / 面包屑点击 / 路径跳转）：`history.slice(0, historyIndex + 1).concat(newPath)`，index 移到末尾。
- 后退：`historyIndex--`，`currentPath = history[historyIndex]`。
- 前进：`historyIndex++`。
- 栈深超 50 时弹栈底。

**考虑过**：持久化到 localStorage。否决：跨 session 行为不可预测（用户场景多变），且不符合"前进/后退是当前会话上下文"的语义。

### D3：列头排序纯前端（不调后端）

**理由**：列表已经在前端（`state.entries`），按 name / mtime / size 排序是纯计算；调后端会引入 latency + 后端排序规则需要跟前端一致（增加维护成本）。

**实现**：reducer 加 `sortBy / sortDir`；渲染前用 `useMemo` 对 `entries` 做 sort；切换列头点击 = 同时切换字段 + 升降序。

排序规则：
- `name`：目录优先 + 名称按 `localeCompare(basename, undefined, {sensitivity: 'base'})`
- `mtime`：按 mtime 升/降序，目录优先
- `type`：按扩展名（去 `.`）升/降序，目录固定为 `__dir__`

**考虑过**：后端 sort。否决：后端接口复杂度上升 + 前端响应慢。

### D4：底部"文件夹"标签 + name 紧凑布局

**理由**：DSH 底部只有"文件夹：xxx"标签，无 name 框（因为 DSH 本身是"在 DSH 选目录"不创建工作区）。我们必须保留 name 框（用户决策：保留 name 输入灵活性），所以在 DSH 风格基础上**压缩布局**：把 name 框放底部，跟路径框共享一行，name 短、path 长，name 框 ≤ 30% 宽度。

```
┌──────────────────────────────────────────────────┐
│ 文件夹: [D:\claude-projects\agent-demo]         │  ← 文件夹框（DSH 风格）
│ 工作区名称: [agent-demo                  ]       │  ← name 框（紧凑，紧贴下方）
│                              [取消] [选择此目录] │
└──────────────────────────────────────────────────┘
```

**考虑过**：name 框放顶部（更显眼）。否决：DSH 风格强调"路径在底部"，name 放在底部贴近路径，语义上更连贯。

### D5：标题英文 + aria-label 中文（双语文案）

**理由**：DSH 标题是 "Select Workspace Directory"。照搬英文保持 DSH 风格；同时给中文用户留可访问性提示。

**实现**：
```jsx
<header>
  <span>Select Workspace Directory</span>  // 视觉文本
</header>
<div role="dialog" aria-label="选择工作区目录">  // 屏幕阅读器 + 测试
```

**考虑过**：纯中文（"选择工作区目录"）。否决：与 DSH 风格不符。

### D6：隐藏文件开关从工具栏移到右列头（DSH 风格）

**理由**：DSH 用列头控制显示列（含"修改日期"），不显示的列就隐藏。我们把"显示隐藏文件"放在工具栏右侧（与 DSH 不一致）；新版本放到右侧列头附近（小图标 + tooltip），更贴近 DSH 资源管理器的"查看"菜单。

**实现**：右列头右侧加一个 `Eye/EyeOff` 小图标按钮，hover 显示 tooltip"显示隐藏文件 / 隐藏文件"。

**考虑过**：保留原"工具栏 + 显示隐藏"按钮。否决：DSH 没有独立工具栏，列头区域是合理的归位。

## Risks / Trade-offs

### R1：左侧导航树宽度过窄时盘符路径被截断

[Mitigation] 左导航树宽度固定 220px，盘符只显示 `C:` / `D:` 等短名；详细路径在右侧文件列表底部 + 面包屑展示，路径框在底部独立显示。

### R2：history 栈在 Modal 重渲染时被 reducer 误清空

[Accepted] reducer 是纯函数，state.history 在 reducer 内部维护；只有 `navigate` action 修改它。Modal 重新打开时（`open: false → true`）走 `INITIAL` state，history 重置。这是预期行为。

### R3：列头排序在 10000+ 条目时性能差

[Accepted] 当前规模目录条目数 ≤ 1000；10000+ 条目需要虚拟滚动 / 后端分页，本次不做。

### R4：底部布局响应式窄屏挤压 name 框

[Mitigation] Modal width `min(720px, 100%)`，窄屏会自动缩窄；name 框设 `min-width: 120px`。`< 480px` 时 name 框换行（flex-wrap）。

### R5：history 栈深 50 后弹栈底，可能丢失"上一级"的快捷访问

[Accepted] 50 步足够普通浏览；超过后弹栈底（保留最新 50）。DSH 资源管理器也是类似行为。

### R6：左导航树可点击区域与面包屑重叠，UX 混淆

[Mitigation] 左导航树点击只切换 `currentPath`（不重新打开 Modal），与面包屑语义一致；不引入折叠展开状态的持久化避免 UX 复杂。

## Migration Plan

无破坏性变更：
- `POST /api/workspaces { name, dir }` 接口不变。
- `/api/fs/list|mkdir|home|drives` 接口不变。
- 仅新增 `GET /api/fs/quick-access`。

部署：随 v0.x 下次发版集成；`mvn package` 单 jar 产出。

回滚：`git revert` 单 commit 即可；新接口删掉不影响旧接口。

## Open Questions

无（探索阶段所有决策已敲定）。

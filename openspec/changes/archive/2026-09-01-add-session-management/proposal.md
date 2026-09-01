# 提案：历史会话折叠与删除（add-session-management）

## Why

`add-session-switch` 已把真实会话列表接入侧栏，但会话内容是"摊开"展示，且无法删除/恢复。
用户需要把历史会话**折叠**成可展开的紧凑条目，并支持**删除**。删除采用**软删除/归档**（可恢复），
避免误删后无法找回。

## What Changes

对齐参考侧栏（DeepSeek Harness 风格）：

- **每工作区默认展示最近 5 个会话**：超出时收进"展开其余 N 个会话"，点击展开显示全部；
  工作区在前、会话按最近活动(mtime)降序。
- **"⊕ 新会话"按钮移到侧栏顶部**（从 TopBar 挪到侧栏顶部，占满一行）。
- **会话行显示相对时间**（7分钟/1小时/1天等）：给 `SessionSummaryDto` 加时间戳字段。
- **删除**：软删除/归档——`sessions/<id>.jsonl` rename 到 `sessions/.archive/<id>.jsonl`；
  删除前二次确认；删除当前查看会话时自动切到下一条或空态。
- **恢复**：新增「归档/回收站」视图，列出已归档会话并可一键恢复（rename 移回 `sessions/`）。

## Impact

- `agent-core.SessionStore`：新增 `archive(id)` / `restore(id)` / `listArchived()`（rename + 目录校验，
  防路径穿越）。
- `agent-web.SessionController`：新增 `DELETE /api/sessions/{id}`、`POST /api/sessions/{id}/restore`、
  `GET /api/sessions?archived=true`；`list()` 默认排除归档。
- `agent-web` 前端：`Sidebar` 会话行折叠 + 删除按钮 + 确认弹窗；新增归档视图与恢复；localStorage 展开状态。
- 兼容性：CLI 不受影响；未归档会话列表行为不变。

## Out of Scope

- 硬删除（永久清除磁盘文件）与清空回收站。
- 会话重命名 / 分组拖拽 / 排序。
- 批量删除。

## 风险

- rename 需处理目标已存在、源不存在、非法 id（路径穿越）等边界，统一返回 404/409。
- 删除中的会话若正被 `historyFor` 缓存，需同步清理内存缓存（`WebAgentRuntime`）。

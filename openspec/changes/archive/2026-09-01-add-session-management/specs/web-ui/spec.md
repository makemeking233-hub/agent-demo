# web-ui Specification

## ADDED Requirements

### Requirement: 工作区会话默认展示与展开

系统 SHALL 在侧栏按工作区分组展示会话，每个工作区默认只展示最近 5 个会话，其余收起为"展开其余 N 个会话"，点击展开显示全部；会话按最近活动降序。

#### Scenario: 默认只显示 5 个

- WHEN 某工作区有超过 5 个未归档会话
- THEN 侧栏默认只列出前 5 个（按最近活动降序）
- AND 显示"展开其余 N 个会话"入口（N=总数-5）

#### Scenario: 点击展开显示全部

- WHEN 用户点击"展开其余 N 个会话"
- THEN 该工作区展示全部会话
- AND 入口变为"收起"/可再收起

#### Scenario: 少于等于 5 个不显示展开入口

- WHEN 某工作区未归档会话数 ≤ 5
- THEN 直接列出全部，不显示展开入口

#### Scenario: 展开状态持久化

- WHEN 用户在某工作区点开"展开全部"后刷新页面
- THEN 该工作区仍保持展开（用 `localStorage` 按工作区记住）

### Requirement: 新会话入口

系统 SHALL 在侧栏顶部提供"⊕ 新会话"按钮，用于创建新会话。

#### Scenario: 侧栏顶部新会话

- WHEN 用户点击侧栏顶部"新会话"按钮
- THEN 创建/进入一个新会话（清空当前会话，等待输入）
- AND 该按钮位于侧栏最上方、占满一行

### Requirement: 会话行相对时间

系统 SHALL 在会话行展示相对时间（如 `7分钟 / 1小时 / 1天`），基于会话最后活动时间。

#### Scenario: 展示相对时间

- WHEN 侧栏列出会话
- THEN 每个会话行右侧显示相对时间（基于 `session.jsonl` 的最后修改时间/最后活动）

#### Scenario: 超长时间显示为 天

- WHEN 会话最后活动超过 24 小时
- THEN 显示"1天 / 4天 / 6天"等（按天取整）

### Requirement: 会话删除（软删除/归档）

系统 SHALL 允许用户把一条会话归档（软删除），并确认后隐藏该会话；若删除的是当前查看会话，自动切换到下一条或空态。

#### Scenario: 删除前二次确认

- WHEN 用户点击某会话的删除按钮
- THEN 弹出确认提示，用户确认后才调用删除接口、隐藏该会话；取消则不修改

#### Scenario: 删除归档落盘

- WHEN 用户确认删除 `DELETE /api/sessions/{id}`
- THEN 服务端把 `sessions/<id>.jsonl` 移至 `sessions/.archive/<id>.jsonl`
- AND 该会话不再出现在 `GET /api/sessions` 列表

#### Scenario: 删除当前查看会话切换

- WHEN 删除的是当前正在查看（选中）的会话
- THEN 前端自动切换到剩下的最近会话，或展示空态（无会话时）

### Requirement: 归档会话查看与恢复

系统 SHALL 提供「归档/回收站」视图，列出已归档会话并支持恢复。

#### Scenario: 列出归档会话

- WHEN 用户打开「归档」视图（`GET /api/sessions?archived=true`）
- THEN 展示所有已归档会话的摘要
- AND 无归档位时显示空态

#### Scenario: 恢复归档会话

- WHEN 用户在归档视图点击某会话的「恢复」
- THEN 服务端把 `sessions/.archive/<id>.jsonl` 移回 `sessions/<id>.jsonl`（`POST /api/sessions/{id}/restore`）
- AND 该会话重新出现在会话列表

### Requirement: 会话删除/恢复 API

系统 SHALL 提供归档与恢复会话的 HTTP 接口。

#### Scenario: 归档成功

- WHEN 客户端发送 `DELETE /api/sessions/{id}` 且该会话存在
- THEN 返回 `200 OK`，会话被归档（文件移至 `.archive/`）

#### Scenario: 归档未知会话

- WHEN 客户端发送 `DELETE /api/sessions/{id}` 但该会话（未归档的）不存在
- THEN 返回 `404 Not Found`

#### Scenario: 恢复成功

- WHEN 客户端发送 `POST /api/sessions/{id}/restore` 且该会话在 `.archive/`
- THEN 返回 `200 OK`，会话恢复到 `sessions/`

#### Scenario: 恢复未知归档

- WHEN 客户端发送 `POST /api/sessions/{id}/restore` 且 `.archive/` 无该会话
- THEN 返回 `404 Not Found`

#### Scenario: 非法 id 被拒

- WHEN `{id}` 含路径分隔符或非法字符（如 `../x`、`a/b`）
- THEN 返回 `400 Bad Request`，不读取/移动任何文件

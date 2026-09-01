# 任务：add-session-management

> TDD 优先：每个任务"测试先红 → 实现 → 测试转绿"。`mvn verify`（jacoco 门禁）收尾。
> 前端 vitest；后端 JUnit5 + Mockito。

## T1: SessionStore 归档/恢复/列表归档

- [x] 先写 `SessionStoreTest`：`archive(dir,"s-1")` 后 `sessions/s-1.jsonl` 移至 `.archive/s-1.jsonl`，
  `listSessions` 不再含 `s-1`、`listArchived` 含之；`restore(dir,"s-1")` 移回；非法 id（`../x`）拒绝；
  不存在返回 false。
- [x] 实现 `SessionStore.archive/restore/listArchived`（含 id 白名单校验 + `.archive` 目录创建）。
- [x] `mvn -pl agent-core test -Dtest=SessionStoreTest` 全绿。

## T2: WebAgentRuntime 归档/恢复 + 内存缓存清理

- [x] 先写 `WebAgentRuntimeTest#archiveSession_movesFileAndClearsCache`：
  写入会话 + `historyFor` 触达后 `archiveSession(id)`，断言文件已移、`hasSession(id)` 为 false、
  `sessionHistories` 不再含该 id；`restoreSession(id)` 恢复且文件回到 `sessions/`。
- [x] 实现 `WebAgentRuntime.archiveSession/restoreSession/archivedIds`，删除时清 `sessionHistories`/`sessionRecorders`。
- [x] `mvn -pl agent-web test -Dtest=WebAgentRuntimeTest` 全绿。

## T3: 后端 API（删除/恢复/归档列表）

- [x] 先写 `SessionControllerTest`：`DELETE /api/sessions/{id}` → 文件归档 + 200；未知 id 404；
  `POST /{id}/restore` → 恢复 + 200/404；`GET /api/sessions?archived=true` → 只含归档会话；
  `GET /api/sessions` 默认排除归档。
- [x] 实现 `SessionController` 三个端点 + `list()` 默认排除归档。
- [x] `mvn -pl agent-web test -Dtest=SessionControllerTest` 全绿。

## T4: 前端侧栏折叠 + 新会话按钮 + 相对时间 + 删除确认

- [x] 先写 `Sidebar` 组件测试：每工作区默认显示 5 个、其余显示"展开其余 N 个"、展开后显示全部；
  会话行显示相对时间；顶部"新会话"按钮；删除按钮触发确认并调用 `DELETE`。
- [x] 实现 `Sidebar`：顶部新会话按钮、每工作区默认 5 + 展开其余、会话行相对时间、删除按钮 + 确认弹窗、
  localStorage 持久化展开状态。
- [x] `cd agent-web/frontend && npm run test` 全绿。

## T5: 前端归档视图 + 恢复

- [x] 先写组件测试：归档视图列出归档会话，点"恢复"调用 `POST /restore` 并刷新列表。
- [x] 实现归档入口 + 归档列表 + 恢复按钮。
- [x] `npm run test` 全绿。

## 收尾

- [x] `mvn verify -DskipNpm=true` 全绿（jacoco 门禁）。
- [x] 前端 `npm run build` 更新 static 产物。
- [x] commit + push（中文 Conventional Commits）。
- [x] `openspec-archive-change add-session-management`。

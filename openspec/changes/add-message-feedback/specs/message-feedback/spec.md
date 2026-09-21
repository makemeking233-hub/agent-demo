# message-feedback Spec Deltas (add-message-feedback)

## ADDED Requirements

### Requirement: feedback sidecar 存储

系统 SHALL 把消息反馈存到独立 sidecar（`~/.agent-demo/feedback/<sessionId>.json`），**不进 session.jsonl、模型不可见**。文件权限 SHALL 为 0600，目录 0700。

#### Scenario: 记录 feedback 后模型不可见

- **WHEN** 用户给某条 assistant 消息点 👍
- **THEN** feedback 写入 `<agentDataDir>/feedback/<sessionId>.json`；`session.jsonl` 无任何变化；下一次 `AgentLoop.toRequest()` 构造的 messages 不含 feedback 内容

#### Scenario: sidecar schema

- **WHEN** 读取 sidecar 文件
- **THEN** schema 为 `{version:1, session_id, items:{<messageUuid>:{rating, version, updated_at}}}`

### Requirement: REST 端点

系统 SHALL 提供 3 个 feedback 端点。

| Method | Path | Body | 返回 |
|--------|------|------|------|
| GET | `/api/feedback/{sessionId}` | — | `{items:{...}}` |
| PUT | `/api/feedback/{sessionId}/{messageId}` | `{rating, ifVersion}` | `{rating, version}` |
| DELETE | `/api/feedback/{sessionId}/{messageId}` | `{ifVersion}` | 204 |

#### Scenario: GET 无反馈的 session

- **WHEN** session 从未有 feedback
- **THEN** 返回 `{items:{}}`（200，不 404）

#### Scenario: PUT 带非法 rating

- **WHEN** body `{rating:"maybe"}`
- **THEN** 400 `{error:"rating_invalid"}`

### Requirement: per-item version CAS

PUT / DELETE SHALL 校验 `ifVersion`：`null` 表示「必须不存在」，`N` 表示「必须等于当前 version」。冲突 SHALL 返回 409 + `{current: {rating, version} | null}`。

#### Scenario: 首次创建（ifVersion=null）

- **WHEN** `PUT {rating:"up", ifVersion:null}` 且该 message 无记录
- **THEN** 201/200 创建，version=1

#### Scenario: 重复创建冲突

- **WHEN** `PUT {rating:"up", ifVersion:null}` 但该 message 已有记录（version=1）
- **THEN** 409 + `{current:{rating:"up", version:1}}`

#### Scenario: 版本不匹配冲突

- **WHEN** `PUT {rating:"down", ifVersion:1}` 但当前 version=3
- **THEN** 409 + `{current:{rating:"up", version:3}}`

#### Scenario: 另一个标签页已删除

- **WHEN** `DELETE {ifVersion:2}` 但记录已不存在
- **THEN** 409 + `{current:null}`（前端据此删除本地条目）

### Requirement: 存储并发安全

`MessageFeedbackStore` SHALL 在写操作时加文件锁（`<sessionId>.lock`），避免多标签页并发写导致 sidecar 损坏。

#### Scenario: 并发 PUT 同一 message

- **WHEN** 两个标签页同时 PUT 同一 messageId
- **THEN** 串行执行；后者拿到前者的 version → 409 冲突

### Requirement: 👍/👎 两态切换

赞踩 SHALL 为两态互斥（up / down / 无），点击已选中的按钮 SHALL 取消该 rating。

#### Scenario: 首次点赞

- **WHEN** 消息无 rating，用户点击 👍
- **THEN** `PUT /api/feedback/{sid}/{mid} {rating:"up", ifVersion:null}` → 200，按钮高亮

#### Scenario: 取消点赞

- **WHEN** 消息已 👍，用户再点 👍
- **THEN** `DELETE /api/feedback/{sid}/{mid} {ifVersion:N}` → 204，按钮恢复

#### Scenario: 切换方向

- **WHEN** 消息已 👍，用户点 👎
- **THEN** `PUT {rating:"down", ifVersion:N}` → 200，👎 高亮、👍 恢复

#### Scenario: 写失败回滚

- **WHEN** PUT 返回 500
- **THEN** 按钮回到点击前的状态（乐观更新回滚），不静默留错

#### Scenario: 无 uuid 的消息不显示赞踩

- **WHEN** 消息没有 `uuid`（老会话历史 / 流式中）
- **THEN** 不渲染 👍/👎 按钮（copy 与 clock 不受影响）

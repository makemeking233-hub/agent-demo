# message-actions Spec Deltas (add-message-actions)

## ADDED Requirements

### Requirement: assistant 消息操作栏

系统 SHALL 在 assistant 消息 finalize 后显示操作栏，含 copy 按钮、赞/踩按钮、per-message 性能读数（clock）。流式中的 assistant 消息 SHALL NOT 显示操作栏。

#### Scenario: finalize 后显示完整操作栏

- **WHEN** assistant 消息收到 `message_stop` 且携带 `uuid`
- **THEN** 该消息下方显示 `[copy] [👍] [👎] 16:23 · Ran for 15s · TTFT 1.2s · 34 tok/s`

#### Scenario: 流式中不显示

- **WHEN** assistant 消息正在接收 `message_delta`
- **THEN** 不显示操作栏（无 uuid）

#### Scenario: user 消息只显示 copy

- **WHEN** 渲染 user 消息
- **THEN** 显示 copy 按钮，不显示赞踩与 clock

### Requirement: copy 按钮（clipboard + 1s 反馈）

copy 按钮 SHALL 把消息纯文本写入剪贴板，成功后 1 秒内显示 ✓ 图标，期间重复点击 SHALL 不重复写入。

#### Scenario: 复制成功

- **WHEN** 用户点击 copy
- **THEN** `navigator.clipboard.writeText(text)` 被调用；成功后按钮变 ✓；1 秒后恢复 📋

#### Scenario: clipboard 不可用时降级

- **WHEN** `navigator.clipboard` 抛错（非 HTTPS / 非 localhost）
- **THEN** 降级用隐藏 textarea + `document.execCommand('copy')`

#### Scenario: 防重入

- **WHEN** 用户在 1 秒 ✓ 窗口内再次点击 copy
- **THEN** 不重复写剪贴板、不堆叠 timer

### Requirement: per-message 性能读数（clock）

系统 SHALL 在 assistant finalize 时经 SSE 推送 `message_meta` 事件（`{uuid, duration_ms, ttft_ms, tok_per_sec, timestamp}`），前端 SHALL 按 `uuid` 绑定到对应消息并渲染。

#### Scenario: 正常读数

- **WHEN** turn 耗时 15s、首 token 1.2s、吞吐 34 tok/s
- **THEN** clock 显示 `16:23 · Ran for 15s · TTFT 1.2s · 34 tok/s`

#### Scenario: 派生指标不可用

- **WHEN** provider 未返回 usage（`ttft_ms` / `tok_per_sec` 为 null）
- **THEN** clock 对应段显示 `N/A` 或省略

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

## MODIFIED Requirements

### Requirement: SSE 事件类型扩展

SSE 事件流新增 `message_meta` 类型（在 `message_stop` 之前推送）。老前端 SHALL 忽略未知 event 类型而不报错。

#### Scenario: 老前端兼容

- **WHEN** 前端未实现 `message_meta` 处理
- **THEN** 收到未知 event 时静默忽略，不影响既有渲染
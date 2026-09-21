# message-actions Spec Deltas (add-message-actions)

## ADDED Requirements

### Requirement: 消息操作栏

系统 SHALL 在 assistant 消息有正文（`text` 非空）后显示操作栏，含 copy 按钮与 per-message 性能读数（clock）。流式中的 assistant 消息（尚无正文）SHALL NOT 显示操作栏。

#### Scenario: assistant finalize 后显示操作栏

- **WHEN** assistant 消息收到 `message_meta`（随后 `message_stop`）
- **THEN** 该消息下方显示 `[copy] 16:23 · Ran for 15s · TTFT 1.2s · 34 tok/s`

#### Scenario: 流式中不显示

- **WHEN** assistant 消息正在接收 `message_delta` 且尚无正文
- **THEN** 不显示操作栏

#### Scenario: user 消息只显示 copy

- **WHEN** 渲染 user 消息
- **THEN** 显示 copy 按钮，不显示 clock

#### Scenario: 无读数时只显示 copy

- **WHEN** 消息没有 `message_meta`（老会话历史 / 会话不落盘）
- **THEN** 操作栏只渲染 copy 按钮，不渲染 clock 元素

### Requirement: copy 按钮（clipboard + 1s 反馈）

copy 按钮 SHALL 把消息纯文本写入剪贴板，成功后 1 秒内显示 ✓ 图标，期间重复点击 SHALL 不重复写入。

#### Scenario: 复制成功

- **WHEN** 用户点击 copy
- **THEN** `navigator.clipboard.writeText(text)` 被调用；成功后按钮变 ✓；1 秒后恢复 📋

#### Scenario: clipboard 不可用时降级

- **WHEN** `navigator.clipboard` 抛错（非 HTTPS / 非 localhost）
- **THEN** 降级用隐藏 textarea + `document.execCommand('copy')`

#### Scenario: 两条路径都失败

- **WHEN** `navigator.clipboard` 与 `document.execCommand` 都失败
- **THEN** 按钮保持 📋（不显示 ✓），不抛异常

#### Scenario: 防重入

- **WHEN** 用户在 1 秒 ✓ 窗口内再次点击 copy
- **THEN** 不重复写剪贴板、不堆叠 timer

### Requirement: per-message 性能读数（clock）

系统 SHALL 在回合结束时于 `message_stop` **之前**经 SSE 推送 `message_meta` 事件（`{uuid, duration_ms, ttft_ms, tok_per_sec, timestamp}`），前端 SHALL 把它绑定到本轮最后一条 assistant 消息并渲染。

#### Scenario: 正常读数

- **WHEN** turn wall time 15s、首 token 平均延迟 1.2s、吞吐 34 tok/s
- **THEN** clock 显示 `16:23 · Ran for 15s · TTFT 1.2s · 34 tok/s`

#### Scenario: 派生指标不可用

- **WHEN** provider 未返回 usage（`ttft_ms` / `tok_per_sec` 为 null）
- **THEN** clock 对应段整体省略（不用 `NaN` / `N/A` 占位）

#### Scenario: 会话不落盘时 uuid 缺失

- **WHEN** 该会话没有 `SessionRecorder`（不落盘）
- **THEN** `message_meta.uuid` 为 null；clock 照常显示，前端不因此报错

#### Scenario: 事件顺序

- **WHEN** 一个回合结束
- **THEN** SSE 事件顺序为 `message_meta` → `turn_stats` → `message_stop`

### Requirement: 刷新后 clock 仍在（历史回填）

`message_meta` 读数 SHALL 随会话存档持久化，并在 `GET /api/sessions/{id}/messages` 时按 assistant 序号贴回对应消息。

#### Scenario: 回合结束落盘读数

- **WHEN** 回合结束
- **THEN** `SessionRecorder` 追加一条 `meta(key="message_meta")` 条目，value 含 `uuid` / `duration_ms` / `ttft_ms` / `tok_per_sec` / `timestamp`

#### Scenario: 历史端点返回读数

- **WHEN** 请求 `GET /api/sessions/{id}/messages`
- **THEN** assistant 消息带 `uuid` 与 `meta`（无读数时为 null），其余角色两者均为 null

#### Scenario: 读数 uuid 找不到对应 assistant

- **WHEN** `message_meta` 的 uuid 不在存档里（被裁剪 / 旧数据）
- **THEN** 该读数被丢弃，不贴到别的消息上

#### Scenario: 老会话（无 message_meta）

- **WHEN** 会话存档里没有 `message_meta` 条目
- **THEN** 历史端点照常返回消息，`meta` 为 null（前端只显示 copy）

## MODIFIED Requirements

### Requirement: SSE 事件类型扩展

SSE 事件流新增 `message_meta` 类型（在 `message_stop` 之前推送）。老前端 SHALL 忽略未知 event 类型而不报错。

#### Scenario: 老前端兼容

- **WHEN** 前端未实现 `message_meta` 处理
- **THEN** 收到未知 event 时静默忽略，不影响既有渲染

## ADDED Requirements

### Requirement: WeChat callback endpoint

系统 SHALL 在 `agent.wecom.enabled=true` 时暴露 `POST /wecom/callback` 与 `GET /wecom/callback` endpoint，处理企业微信回调消息与 URL 验签请求。

#### Scenario: URL 验签成功

- **WHEN** 企业微信管理后台发起 `GET /wecom/callback?msg_signature=X&timestamp=Y&nonce=Z&echostr=W`
- **THEN** 系统按 SHA1(token + timestamp + nonce + echostr) 校验签名，校验通过后**解密 echostr**并明文返回；校验失败返回 401

#### Scenario: 接收加密消息

- **WHEN** 企业微信推送 `POST /wecom/callback`（body 为加密 XML）
- **THEN** 系统校验签名 → AES-256-CBC 解密 → 解析 XML → 提取 `FromUserName`（userId）与 `Content`（消息文本）→ 在 5 秒内返回 200 OK 空响应

#### Scenario: 启用开关关闭

- **WHEN** `agent.wecom.enabled=false`（默认）
- **THEN** 系统不注册 `/wecom/callback` endpoint，调用返回 404

### Requirement: Per-user independent session

系统 SHALL 按 `FromUserName`（userId）维护一对一映射到现有 SessionStore sessionId；同一 userId 多次消息复用同一 sessionId；不同 userId 互不干扰。

#### Scenario: 首次消息创建 session

- **WHEN** userId `user123` 首次发消息
- **THEN** 系统创建新 session，sessionId 形如 `wecom:user123`，持久化到 agent data dir 下 `wecom/sessions.json`

#### Scenario: 同 userId 后续消息复用 session

- **WHEN** userId `user123` 第二次发消息（距首次 5 分钟内）
- **THEN** 系统查到 `user123 → sessionId=wecom:user123` 映射，复用同一 session（多轮上下文保留）

#### Scenario: 不同 userId session 隔离

- **WHEN** userId `user456` 发消息
- **THEN** 系统创建独立 session `wecom:user456`，与 `user123` session 互不可见

### Requirement: 微信通道默认权限模式

系统 SHALL 在微信通道默认 `PermissionMode.FULL_ACCESS`，绕开 `PermissionConfirmer` 弹窗；与现有 add-permission-mode-dropdown 的会话级模式独立。

#### Scenario: 微信通道自动放行

- **WHEN** 微信 userId 发消息触发需要权限的工具（如 WriteFile / Shell）
- **THEN** 系统调用 AgentLoop 时传入 `PermissionMode.FULL_ACCESS`，工具直接执行，**不弹权限确认卡**

#### Scenario: 不影响 web UI 权限模式

- **WHEN** 同一用户在 web UI 发起新会话
- **THEN** 系统沿用 web UI 的会话级 PermissionMode（如 `read_only`），不受微信通道默认设置影响

### Requirement: 流式 markdown 回复推送

系统 SHALL 订阅 AgentLoop 的 `StreamChunk` 流，每累积 3 秒或达到 500 字符时调企业微信 `sendMarkdown` 推送一条消息；`message_stop` 时强制 flush 最终内容。

#### Scenario: 流式累积推送

- **WHEN** AgentLoop 连续 emit `TextDelta` chunks "今" / "天" / "天气" / "晴"
- **THEN** 系统累积 buffer，3 秒后（或 ≥500 字符）调 `sendMarkdown(userId, buffer)` 发一条"今天天气"；后续 chunks 累积到下一条

#### Scenario: Finished 强制 flush

- **WHEN** AgentLoop emit `Finished(reason=STOP)`
- **THEN** 系统立刻 flush buffer 中剩余内容，发最终一条 markdown

#### Scenario: 单条 markdown 超长截断

- **WHEN** 累积内容超过 `agent.wecom.reply.max-chars`（默认 4000）
- **THEN** 系统按 UTF-8 边界截断 buffer，发当前条并清空，后续内容进入新条

### Requirement: API 限频退避

系统 SHALL 在企业微信 API 返回 45009（限频）时退避 30 秒，期间累积的内容合并到下次发送。

#### Scenario: 限频触发退避

- **WHEN** `sendMarkdown` 返回 45009
- **THEN** 系统记录退避起始时间，期间累计的 chunks 不发；30 秒后 flush 合并内容

#### Scenario: 退避期间收到新 chunk

- **WHEN** 退避窗口内 AgentLoop 持续 emit `TextDelta`
- **THEN** 系统持续 append buffer；30 秒到时把整个 buffer 作为一条 markdown 推送

### Requirement: 启动配置校验

系统 SHALL 在 `agent.wecom.enabled=true` 启动时校验必填配置完整、EncodingAESKey 长度=43、callback-base-url 是 https:// 前缀；任一校验失败抛 `IllegalStateException` 阻止应用启动。

#### Scenario: 必填字段缺失

- **WHEN** `agent.wecom.enabled=true` 但 `corp-id` 为空
- **THEN** `WecomConfigValidator.init()` 抛 `IllegalStateException("wecom.corp-id 必须配置")`，Spring 启动失败

#### Scenario: EncodingAESKey 长度错

- **WHEN** `encoding-aes-key` 长度 ≠ 43
- **THEN** 启动失败并报明确错误

#### Scenario: callback URL 非 HTTPS

- **WHEN** `callback-base-url` 不以 `https://` 开头
- **THEN** 启动失败（企业微信拒收 http）

### Requirement: 并发同 userId 串行化

系统 SHALL 对同一 userId 的消息处理串行化（per-userId lock），不同 userId 并行处理。

#### Scenario: 同 userId 多消息排队

- **WHEN** userId `user123` 在 agent 处理第一条消息期间发第二条
- **THEN** 第二条消息进入该 userId 的处理队列，等第一条完成（message_stop）后再启动 AgentLoop

#### Scenario: 不同 userId 并行

- **WHEN** userId `user123` 与 `user456` 同时发消息
- **THEN** 两个 userId 的 AgentLoop 互不阻塞，并行处理

### Requirement: 非文本消息拒绝

系统 SHALL 在收到非 text 类型消息（图/语音/位置/事件）时回复一条 markdown "暂仅支持文本指令"，不启动 AgentLoop。

#### Scenario: 图片消息

- **WHEN** 用户发图片（MsgType=image）
- **THEN** 系统回复 markdown "暂仅支持文本指令"；不调 AgentLoop

#### Scenario: 关注/取消关注事件

- **WHEN** 推送 event 类型（subscribe / unsubscribe）
- **THEN** 系统静默返回 200 OK，不调 AgentLoop

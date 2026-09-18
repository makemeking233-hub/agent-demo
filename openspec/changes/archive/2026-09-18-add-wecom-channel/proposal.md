## Why

agent-demo 当前只支持 CLI（stdin）和 Web UI（SSE）两种入口。用户在自己手机上要触发 agent 必须开电脑。本 change 让用户通过**企业微信 bot** 直接在手机微信里发文本指令，由 agent-demo 接收、处理、回推 markdown 回复，做到"人在外面也能让 agent 干活"。

## What Changes

- **新增** `agent-web` 内的企业微信通道模块：`POST /wecom/callback` 接收加密回调、`GET /wecom/callback` 验签 URL，按 wechat userId 维护独立 session，调 AgentLoop 处理文本消息，按限频流式多次推送 markdown 回复。
- **新增** 配置块 `agent.wecom.*`（corp-id / agent-id / secret / token / encoding-aes-key / 流式推送限频 / ssl 证书路径），默认 `enabled=false`，零启动开销。
- **新增** SessionMapper 把 wechat userId 1:1 映射到现有 `SessionStore` sessionId（持久化 session 文件 `<sessionId>.jsonl`），复用现有 `ChatStreamService.create()` + `WebAgentRuntime.createLoop()` 入口。
- **新增** 微信通道默认权限模式 `FULL_ACCESS`（独立于 add-permission-mode-dropdown 的会话级模式）。
- **新增** 完整测试：单元（crypto / session mapper / reply pusher）+ 集成（MockMvc + WireMock）+ jacoco 门禁。

## Capabilities

### New Capabilities

- `wecom-channel`: 企业微信通道的对外契约（callback endpoint / 配置 schema / 错误码 / 流式推送协议）

### Modified Capabilities

无（不修改现有 spec 的 requirement；plugin-system 已知无"消息通道"扩展点，本次走 `@RestController + enabled 开关` 而非 plugin 化，留作后续 PR4 `ChannelProvider` 扩展点改造）

## Impact

**新增代码**：

- `agent-web/src/main/java/com/example/agent/web/wecom/`（6 个 Java 文件，预计 ~600-800 行）
- `agent-web/src/main/resources/application-web.yml`（追加 `agent.wecom.*` 与 `server.ssl.*` 配置块）
- `agent-web/src/test/java/com/example/agent/web/wecom/`（4-5 个测试类）
- `docs/test-agent-demo/<date>-wecom-channel-*/`（E2E 四件套）

**不动**：

- 现有 plugin-system（无 ChannelProvider 扩展点）
- 现有 SessionStore（复用 `<sessionId>.jsonl` 文件 + `SessionResumeLoader.loadById()` API）
- 现有 `ChatStreamService.create()` / `WebAgentRuntime.createLoop()`（复用入口，仅以 `setPermissionMode(FULL_ACCESS)` 注入）
- 现有 add-permission-mode-dropdown 的会话级权限模式（独立维度）

**外部依赖**：

- 企业微信 OpenAPI（`/cgi-bin/gettoken`、`/cgi-bin/message/send`）
- 用户的 HTTPS 公网入口（自签证书被企业微信拒收，需 ngrok / cloudflared / 自有域名）

**风险**：

- 限频触发（45009）需退避合并消息；自签证书无法被企业微信验证；群消息/语音/图片超出 v1 范围。

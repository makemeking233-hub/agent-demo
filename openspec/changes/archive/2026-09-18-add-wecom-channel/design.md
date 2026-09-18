## Context

agent-demo 当前入口：CLI（stdin 交互）、Web UI（浏览器 SSE 流式）。用户在通勤/出差场景无法访问电脑时，无法触发 agent。

企业微信（WeCom）是中国最普及的企业 IM，几乎所有企业都部署了"自建应用 bot"通道。该通道免证书鉴权（vs 公众号需微信认证），官方 API 稳定，是个人/小团队给现有工具增加远程触达能力的最低成本方案。

本 change 让 agent-demo 暴露 `/wecom/callback` endpoint，企业微信主动把用户消息推过来；agent-demo 按 userId 维护独立 session，复用现有 AgentLoop + SessionStore，处理后按限频多次推送 markdown 回复给用户。

## Goals / Non-Goals

**Goals：**
- 用户在企业微信里给 bot 发文本消息 → agent-demo 处理 → 用户在微信里收到 markdown 回复
- 多轮上下文保留（per-user session）
- 流式体验（多次推送 chunk，避免单条超长 markdown 截断）
- 默认 full_access（agent 在微信里能跑所有工具，免弹权限卡）
- 启动零开销（默认 `wecom.enabled=false`）

**Non-Goals：**
- 微信公众号、个人微信、微信小程序
- 群消息（@bot）、语音、图片、文件、位置、链接卡片
- 多 agent 实例共享 session（单机 JSON 持久化）
- 主动发消息给用户（仅被动响应企业微信 48h 交互窗口）
- 在微信里切换 model / 切换权限模式 / slash 命令

## Decisions

### 1. Spring `@RestController` in agent-web，`wecom.enabled` 开关（不是 plugin-system）

考虑过把 WeChat 做成 plugin-system 的 `ChannelProvider` 扩展点。**否决**：现有 plugin-system 只有 5 个扩展点（Tool / SlashCommand / LlmProvider / SystemPromptFragment / ChatRequestMapper），PluginContext 也没暴露 AgentLoop / SessionStore。改 plugin-system 是另一条独立 PR。退而求其次：本 PR 走 Spring `@RestController` + `@ConditionalOnProperty`，将来 plugin-system 加 `ChannelProvider` 扩展点后，再迁移 wecom 为 plugin（不影响业务语义）。

**取舍**：放弃了"开箱即用多通道"的扩展性，换取了**单 PR 周期 1-2 周可控**。

### 2. per-user 独立 session（per-group 共享 / 无状态都不选）

考虑过 per-group（群成员共享一条对话）和无状态（每条消息 = 新 session）。
- per-group：协作场景友好，但多用户抢话干扰大，需要消息队列串行化
- 无状态：实现最简，但 agent 多轮 / 记忆优势全失

**选 per-user**：单个用户跟 agent 在微信里长期对话，符合"个人助手"心智。

### 3. 流式多次推送 markdown（不是同步回 text）

企业微信回调 5s 超时硬限制，agent 处理常 > 5s。考虑过同步阻塞（callback 等 agent 完成一次性回 text）和流式多次推送。
- 同步阻塞：超过 5s 微信内部会重试，体验差
- 流式多次推送：每 3s/500 字符一次 sendMarkdown，限频命中 45009 后退避 30s 合并

**选流式**：贴近 web UI 流式体验。代价：用户看到的是"多消息序列"而非一条消息。

### 4. 默认 full_access（不是 read_only 询问，不是按钮卡）

考虑过 read_only（写/执行都问 → 微信里没法答 → 失败）和按钮卡（template_card 含"批准/拒绝"，用户点选回调）。
- read_only：实际等于"微信里只能跑只读工具"，价值低
- 按钮卡：实现复杂，每次权限都要往返微信

**选 full_access**：微信场景交互受限，频繁按钮卡顿体验。代价：agent 在微信通道有较高权限，但 wecom.enabled 默认 false，且 agent 自身仍受 ProviderCatalog / 黑名单约束。

### 5. self-HTTPS（已知限制：企业微信拒自签证书）

考虑过 ngrok / cloudflared（dev 体验好但需第三方）、自购云服务器 + nginx 反代（需运维）、agent-demo 自起 HTTPS（自签证书被拒）。

**选 agent-demo 自起 HTTPS**：用户接受"本地 dev 需额外配置 HTTPS"的代价，文档说明 + 提供 dev 步骤。

### 6. 复用 SessionStore + ChatStreamService + WebAgentRuntime（不新建 session 后端）

考虑过 wecom 独立 session 后端（不污染 web UI session 列表）和共用（用户和 web 共享同一 sessionId 空间）。
- 独立：session 列表不污染，但需新建一套 session 持久化
- 共用：用户能在 web UI 里看到微信里开的 session（`sessionId="wecom:user123"` 前缀区分），反向亦可

**选共用**：复用现有 `<sessionId>.jsonl` 文件持久化（`SessionResumeLoader.loadById()` 回填历史）；通过现有 `WebAgentRuntime.createLoop(streamId, sessionId, model, ...)` 入口创建 AgentLoop，仅在调用前 `setPermissionMode(FULL_ACCESS)`。少一套存储；session 列表前缀区分简单；用户在 web UI 里能"接着聊"微信里开过的会话（额外福利）。

## Risks / Trade-offs

| 风险 | 缓解 |
|------|------|
| 自签证书企业微信拒收 | docs 写明 dev 步骤（ngrok / cloudflared / 自有公网域名）；CI 不做真实 E2E |
| 限频 45009 触发消息风暴 | ReplyPusher 限频 + 退避 30s 合并；测试覆盖 45009 路径 |
| 5s 回调超时 | dispatcher 中间步骤 < 100ms；LLM 调用异步 |
| 企业微信 secret 泄露 | env 注入（`${WECOM_SECRET:}`），不进 git；启动校验非空 |
| 用户并发同 userId 多消息 | SessionMapper 锁串行化（per-userId lock） |
| SessionStore 与 web UI 混淆 | sessionId 前缀 `wecom:` 区分；UI 列表可加 source 列（后续 PR） |
| 加密实现自己写踩坑 | 单元测试覆盖 roundtrip + 边界（空/超长/签名不匹配） |

## Migration Plan

首次部署：
1. 用户在企业微信管理后台创建自建应用 bot，获取 CorpID / AgentID / Secret
2. 用户在 agent data dir 准备 HTTPS 证书（或用 ngrok）
3. 配置 `WECOM_*` 环境变量 + `WECOM_ENABLED=true`
4. 启动 agent-demo，启动校验通过
5. 企业微信后台填回调 URL `https://your.domain/wecom/callback`，校验 Token + EncodingAESKey 一致
6. 用户扫码关注 bot，发文本消息验证

回滚：`WECOM_ENABLED=false` 即立即停用通道，不影响 CLI / Web UI。

## Open Questions

无（关键决策点已通过 brainstorming 阶段确认）。

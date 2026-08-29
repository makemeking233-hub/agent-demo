## 1. Setup (Maven module + 骨架)

- [x] 1.1 父 pom 加 `agent-web` module 声明 + 子 pom 写依赖 (spring-boot-starter-webflux / frontend-maven-plugin / nodejs / npm 目标)
- [x] 1.2 `agent-web` 子模块建 `src/main/java/com/example/agent/web/` 空包 + `src/main/resources/application-web.yml`
- [x] 1.3 `agent-web/frontend/` 初始化: `npm init -y`, 装 vite@6 + react@18 + react-dom + typescript, 写 `vite.config.ts` 输出到 `../src/main/resources/static/`
- [x] 1.4 frontend `src/main.tsx` + `src/App.tsx` 最简骨架 (空 div + Vite logo 验证 build)
- [ ] 1.5 验证 `mvn -pl agent-web -am clean package` 能跑通且 `agent-web.jar` 含 `static/index.html` + `static/assets/index-*.js`

## 2. Backend — health + static + 配置

- [ ] 2.1 `WebConfig` (`@Configuration @Profile("web")`): 读 `agent.web.{host, port, trusted-hosts}`，启动时校验 host 不是 `0.0.0.0`，启动后暴露这些 bean
- [ ] 2.2 `TrustedHostFilter` (`OncePerRequestFilter`): 对 `/api/**` 校验 RemoteAddr；loopback 永远放行；非 loopback 必须命中 trusted-hosts；不命中返回 403 + `{"error":"host_not_trusted"}`
- [ ] 2.3 `StaticResourceConfig` (`WebFluxConfigurer` + `RouterFunction`): 配 `/assets/**` 永久缓存 (`Cache-Control: public, max-age=31536000, immutable`)，其他路径 SPA fallback 到 `/index.html`
- [ ] 2.4 `HealthController` (`GET /api/health`): 返回 `{"status":"ok"|"degraded","version","uptime_s","reason"?}`；`/api/health` 跳过 TrustedHostFilter（spec §Health Check 永远 200）
- [ ] 2.5 `WebTestClient` 测试：`/api/health` 200；未配置 trusted-hosts 时非 loopback 返 403；`--host=0.0.0.0` 启动失败

## 3. Backend — chat send + SSE stream

- [ ] 3.1 `ChatStreamService`: 持有 `Map<stream_id, SseEmitter>` + `Map<stream_id, AgentLoopHandle>`；注册 AgentLoop listener (`onTextDelta` / `onThinkingDelta` / `onToolCallStart` / `onToolCallEnd` / `onMessageStop` / `onPermissionAsk`)；listener 把回调转 SSE 事件
- [ ] 3.2 `ChatController.send` (`POST /api/chat/send`): 校验 `content` 非空；空返 400 `content_empty`；检查 `provider.apiKey`，缺失返 503 `provider_not_configured`；否则 `AgentLoop.run(turn)` 包成 future，`ChatStreamService` 注册 stream_id 后返回 `{stream_id, session_id, model}`
- [ ] 3.3 `ChatStreamController.stream` (`GET /api/chat/stream/{id}`, `produces=text/event-stream`): SseEmitter from ChatStreamService；`Last-Event-ID` header 触发 resume；流关闭 (`message_stop` / `error` / 客户端断开) 时调 `emitter.complete()`
- [ ] 3.4 `ChatController.abort` (`POST /api/chat/abort/{id}`): 找到对应 handle，调 `AgentLoopHandle.abort()`；返 200 `{aborted:true|false, reason?}`
- [ ] 3.5 DTO 记录类：`SendRequest` / `SendResponse` / `AbortResponse` / `HealthResponse` + 内部 `SseEvent` union (message_start / message_delta / tool_call_* / permission_request / permission_response / message_stop / error)
- [ ] 3.6 `WebTestClient` 测试：用 mock provider (返回固定 chunks) 跑完整 turn，断言 SSE 事件序列；abort 测试；resume via `Last-Event-ID`；provider 缺失返 503

## 4. Backend — PermissionBridge (in-chat 权限交互)

- [ ] 4.1 `PermissionBridge`: 实现 `waitForDecision(permission_id, tool_call_id)` 阻塞；`submitDecision(permission_id, decision)` 唤醒并校验决策 ∈ {yes, no, always}
- [ ] 4.2 接到 `onPermissionAsk` listener：转 SSE `permission_request` 事件；调 `PermissionBridge.waitForDecision`；用户回 `yes/no/always` 后恢复 AgentLoop；yes/no/always 决策传给现有 `PermissionManager`
- [ ] 4.3 `ChatStreamService` 处理 chat input 时先看是否有 pending permission_id；若有则按决策解析；否则当作普通 chat content
- [ ] 4.4 `WebTestClient` 测试：mock PermissionManager `checkPermissions` 返回 ASK；断言 SSE 事件序列含 `permission_request`；提交 `yes` 后 AgentLoop 恢复且 tool 正常调用

## 5. Backend — slash commands + session current + 静态 fallback

- [ ] 5.1 `SlashCommandRouter`: 接 chat send 的 content；若以 `/` 开头则调对应 `SlashCommand` bean (`help` / `clear` / `quit` / `resume` / `history`)；未知命令返 400 `unknown_command`；`/help` `clear` `resume` 输出转 SSE `message_delta` 后直接 `message_stop`；`/quit` 关闭当前 session + SSE
- [ ] 5.2 `SessionController.current` (`GET /api/sessions/current`): 返 200 `{session_id, started_at, turn_count, tokens_in, tokens_out, model}`；无 session 时返 `{session_id: null}` (spec §Current Session)
- [ ] 5.3 SPA fallback 路由测试：`GET /sessions/{uuid}` 应返 `index.html` (200) 而非 404
- [ ] 5.4 `WebTestClient` 测试：`/help` 触发 message_delta；`/unknown` 返 400；`/api/sessions/current` 空 session 返 null

## 6. Backend — profile 切换与 CLI 隔离

- [ ] 6.1 `AgentCli.java` 加 `--web` flag (或单独入口)；`-Dspring.profiles.active=web` 启动时：禁读 stdin、不启动 REPL、仅启动 web server
- [ ] 6.2 `application-web.yml` 默认值：`agent.web.host=127.0.0.1`, `port=8080`, `trusted-hosts=[]` (空 = 仅 loopback)
- [ ] 6.3 启动 banner：web profile 下打印 `dsh web: http://<host>:<port>` (单行, ANSI 颜色可选)；CLI profile 不变 (无此 banner)
- [ ] 6.4 `mvn spring-boot:run` (无 profile) 行为测试：CLI REPL 正常 + 无端口监听

## 7. Frontend — 基础设施

- [ ] 7.1 `lib/event-types.ts`: 定义与后端 DTO 对齐的 TS 类型 (`MessageStartEvent` / `MessageDeltaEvent` / `ToolCallStartEvent` / `ToolCallEndEvent` / `PermissionRequestEvent` / `PermissionResponseEvent` / `MessageStopEvent` / `ErrorEvent`)
- [ ] 7.2 `lib/sse-client.ts`: 原生 `EventSource` 包装 + 自动重连 (`Last-Event-ID` 透传) + AbortController；导出 `useSseStream(stream_id)` hook (TanStack Query 风格)
- [ ] 7.3 `api/chat.ts`: `send(content, session_id?)` / `abort(stream_id)` / `getCurrentSession()` / `getHealth()`；统一 fetch wrapper 处理 4xx/5xx
- [ ] 7.4 `App.tsx`: 路由 + 三栏布局（左：session list 中：chat 右：tool detail drawer，可折叠）

## 8. Frontend — chat panel + Markdown + tool cards

- [ ] 8.1 `ChatPanel.tsx`: 滚动到底部、消息列表、输入框 + send 按钮 + abort 按钮、IME 友好（中文输入法不抢回车）
- [ ] 8.2 `MessageBubble.tsx`: 用户/助手/系统消息三态样式；助手消息用 `react-markdown` 渲染（含 mermaid fenced block + 代码高亮 rehype-highlight）
- [ ] 8.3 `ToolCallCard.tsx`: 三态渲染：执行中 (loading spinner)、完成 ok (折叠面板)、完成 fail (红色 border)；特殊 tool：`ReadFile` 显示文件路径 + 语法高亮内容；`EditFile` 显示 unified diff；`LsTool` 显示树状列表；其他 tool 显示 name + 原始 result 文本
- [ ] 8.4 `PermissionCard.tsx`: 三按钮 (yes / no / always) 快速回复；点击后转 chat input 提交 `yes`/`no`/`always`；`always` 决策额外调本地后端接口存到 cookie/localStorage (v0.2)
- [ ] 8.5 `SessionList.tsx`: 顶部下拉，仅显示当前 session；「+ 新会话」按钮调 `/api/chat/send` with `session_id: null`

## 9. Frontend — slash commands + 输入区

- [ ] 9.1 `SlashCommandHelp.tsx`: 静态渲染 `/help` 输出 (与后端 `SlashCommand.help()` 输出对齐)；输入 `/` 自动弹命令补全 popover
- [ ] 9.2 聊天输入：支持 `/` 前缀触发 slash；空输入时 disable send；长内容 (≥ 4000 char) 显示字数；粘贴图片 (v0.2)
- [ ] 9.3 abort 按钮：仅在 `stream_id` 存在且未 `message_stop` 时显示；点击调 `/api/chat/abort/{id}`

## 10. 集成测试 + E2E

- [ ] 10.1 后端 WebTestClient 全覆盖：`/api/health` / `/api/chat/send` happy path / abort / stream resume / permission_request / slash / trusted-host
- [ ] 10.2 jacoco 门禁: `mvn -pl agent-web verify` 通过 (`LINE >= 80%`, `BRANCH >= 70%`)
- [ ] 10.3 前端 vitest + @testing-library/react: `MessageBubble` markdown 渲染 / `ToolCallCard` 三态切换 / `sse-client` 重连逻辑
- [ ] 10.4 E2E (可选, v0.1 末): Playwright 起 dev server 跑一个 "用户输入 → 模型流式 → tool call → in-chat 权限 yes" 全链路截图

## 11. 文档 + 收尾

- [ ] 11.1 `docs/design/web-ui-design.md`: 本 design.md 镜像精简版，附 SSE 协议 cheat sheet
- [ ] 11.2 `README.md` 加 web profile 启动说明 (中文)
- [ ] 11.3 `application-web.yml` 配置项加注释；`-Dagent.web.trusted-hosts=192.168.1.0/24` 用法进 README
- [ ] 11.4 `mvn -pl agent-web verify` 全绿；commit + push (per AGENTS.md §2.2 `commit 即 push`)
- [ ] 11.5 准备 archive: `openspec validate add-web-ui-v0-1` 通过；`openspec archive add-web-ui-v0-1` 把 delta spec 合到 `openspec/specs/web-ui/spec.md`
# Change: Add Web UI v0.1 (React + Spring WebFlux)

## Why

agent-demo 当前只有 CLI/REPL 形态（picocli + JLine3），用户须在终端交互。补一个 Web UI 后可在浏览器里对话、查看工具调用、跑 slash 命令，跨设备可用，体验对齐 Claude Code / DSH dsh-web。

## What Changes

- 新增 Maven module `agent-web`（Spring WebFlux + SSE），复用 `agent-core` 的 `AgentLoop` / `SessionStore` / `PermissionManager` / `ToolRegistry`
- `agent-web` 启动时跑同一个 Spring 上下文，挂 `@Profile("web")`，与现有 CLI profile 共存
- 新增 HTTP API（v0.1）：
  - `POST /api/chat/send` — 启动一个 agent turn，返回 `stream_id`
  - `GET  /api/chat/stream/{id}` — SSE 流（粗粒度事件，见 specs/web-ui §Event Types）
  - `POST /api/chat/abort/{id}` — 中断 turn
  - `GET  /api/health` — 健康检查
  - `GET  /api/sessions/current` — 当前 session 元数据
- 新增鉴权：单用户本地 + `--trusted-host <ip>` LAN 白名单（DSH dsh-web-app 同款）。默认 `--host=127.0.0.1`，未在白名单的源 IP 返回 403
- 新增 React 18 + Vite 前端项目 `agent-web/frontend/`：
  - chat panel + 流式渲染 + tool card
  - react-markdown（mermaid fenced block + 代码高亮）
  - Tailwind v4 + shadcn/ui
  - TanStack Query 管 SSE 事件流
  - 单 session 列表（仅当前 session，v0.2 接历史）
- `mvn package` 通过 frontend-maven-plugin 集成 `npm ci && npm run build`，React dist 落到 `agent-web/src/main/resources/static/`，最终 `agent-web.jar` 自包含
- 保留 CLI：`mvn spring-boot:run` 默认仍走 `chat` 子命令；`mvn spring-boot:run -Dspring.profiles.active=web` 起 web server
- v0.1 权限交互：in-chat 模式——后端推 `permission_request` 事件，前端在聊天区显示，用户在输入框回 `yes`/`no` 回车确认

## Capabilities

- **New Capabilities**:
  - `web-ui` — Web 聊天面板 + 流式 + tool cards + slash 命令 + 当前 session 列表 + in-chat 权限交互
- **Modified Capabilities**: (none — `cli` spec 行为不变)

## Impact

- 受影响代码：
  - `agent-core/`: 无业务改动，HTTP 层在 `agent-web/` 新建（复用 AgentLoop / SessionStore / PermissionManager bean）
  - `agent-web/`: 新建 module，含 `src/main/java/com/example/agent/web/{WebConfig, ChatController, ChatStreamService, TrustedHostFilter, StaticResourceConfig}.java` + `src/main/resources/{application.yml, static/}` + `frontend/`
  - `pom.xml`: 加 `agent-web` module、frontend-maven-plugin 依赖、Spring WebFlux 依赖（仅在 agent-web scope）
- 受影响 API / 协议：v0.1 仅新增，不改现有 CLI/REPL 协议
- 受影响依赖：Spring WebFlux（已在 spring-boot-starter-webflux）、picocli 不动；前端新增 react / vite / tanstack-query / tailwind / shadcn
- 受影响配置：`application.yml` 加 `agent.web.{host, port, trusted-hosts, max-sessions}`，`~/.agent-demo/config.yaml` 同路径可覆盖
- 不受影响：CLI REPL、provider、session JSONL 格式、PermissionManager API

## Out of Scope（v0.2+）

- 正式 Permission UI 模态框（v0.1 用 in-chat）
- session 历史列表 / 跨 session 列表 UI（v0.1 只显示当前 session）
- settings 页面（model 切换、provider 配置、温度等）
- 多用户 / OAuth / 登录
- 历史 session 检索 / 关键字搜索
- session 导出 / 导入
- 暗色 / 亮色主题切换（v0.1 默认亮色）
- 多端点协同（WebSocket 用于 plan mode 等双向交互）
- 文件上传 / 拖拽上传
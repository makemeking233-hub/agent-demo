# web-ui Specification

## Purpose

定义 agent-demo v0.1 Web UI 的用户可见行为契约——浏览器侧 React 前端与 Java Spring WebFlux 后端之间的 HTTP / SSE 协议面、信任模型、in-chat 权限交互，以及与既有 `cli` capability 的边界。本 spec 涵盖 v0.1 全部对外行为；v0.2 将另行追加 permission UI、session 历史、settings。

## ADDED Requirements

### Requirement: Chat Send

The system SHALL accept a user message via HTTP and start a new agent turn.

#### Scenario: Valid message starts turn

- WHEN client sends `POST /api/chat/send` with JSON body `{"content": "你好", "session_id": "<uuid>"}` (session_id 可选；缺省创建新 session)
- AND the request source IP is in the trusted-hosts list (or the server binds 127.0.0.1)
- THEN the server returns `200 OK` with body `{"stream_id": "<uuid>", "session_id": "<uuid>", "model": "deepseek-chat"}` within 200ms
- AND the server begins emitting SSE events on `GET /api/chat/stream/{stream_id}`

#### Scenario: Empty content rejected

- WHEN client sends `POST /api/chat/send` with `content` empty or whitespace-only
- THEN the server returns `400 Bad Request` with `{"error": "content_empty"}`
- AND no stream is created

#### Scenario: Provider not configured

- WHEN client sends `POST /api/chat/send` and `provider.apiKey` is missing in config (env + yaml)
- THEN the server returns `503 Service Unavailable` with `{"error": "provider_not_configured", "hint": "set DEEPSEEK_API_KEY"}`
- AND no stream is created

### Requirement: Chat Stream (SSE)

The system SHALL stream agent turn output via Server-Sent Events.

#### Scenario: Stream emits events in order

- WHEN client opens `GET /api/chat/stream/{stream_id}` with `Accept: text/event-stream`
- AND a corresponding agent turn is running
- THEN the server emits events with `Content-Type: text/event-stream` and `Cache-Control: no-cache`
- AND each event has the shape `data: <json>\n\n` where `<json>` is one of: `message_start`, `message_delta`, `tool_call_start`, `tool_call_end`, `permission_request`, `message_stop`, `error`
- AND events arrive in causal order (a `tool_call_end` never precedes its `tool_call_start`)

#### Scenario: Stream closes on stop

- WHEN a `message_stop` event has been emitted
- THEN the server closes the SSE connection within 1s
- AND the client may safely retry with the same `session_id` to start a new turn

#### Scenario: Stream closes on abort

- WHEN client sends `POST /api/chat/abort/{stream_id}` while the stream is open
- THEN the server emits a final `error` event with `{"code": "aborted"}` then closes the SSE connection
- AND the agent turn in `AgentLoop` is interrupted (no further events emitted)

#### Scenario: Stream id not found

- WHEN client opens `GET /api/chat/stream/{stream_id}` with an unknown id
- THEN the server returns `404 Not Found` with `{"error": "stream_not_found"}`

#### Scenario: Reconnect-friendly

- WHEN the SSE connection drops mid-turn (network blip)
- AND the client reconnects to the same `stream_id`
- THEN the server resumes events from where the client left off (last seen event id via `Last-Event-ID` header)
- AND emits at most one `message_stop` per turn (idempotent)

### Requirement: SSE Event Types

The system SHALL emit the following coarse-grained SSE events with stable shape.

#### Scenario: message_start payload

- WHEN an agent turn begins
- THEN the server emits `data: {"type":"message_start","stream_id":"<uuid>","session_id":"<uuid>","model":"deepseek-chat","timestamp":<epoch_ms>}`

#### Scenario: message_delta payload (text)

- WHEN model produces text tokens
- THEN the server emits `data: {"type":"message_delta","delta_type":"text","content":"<chunk>"}` per token chunk (≤ 80 chars each)
- AND concatenating all `content` in order equals the full assistant message

#### Scenario: message_delta payload (thinking)

- WHEN the provider returns reasoning content (e.g. `deepseek-reasoner`)
- THEN the server emits `data: {"type":"message_delta","delta_type":"thinking","content":"<chunk>"}` per token chunk
- AND the client renders thinking in a collapsible section distinct from final text

#### Scenario: tool_call_start payload

- WHEN the agent invokes a tool
- THEN the server emits `data: {"type":"tool_call_start","tool_call_id":"<uuid>","name":"<tool_name>","args":<json>}` BEFORE the tool executes

#### Scenario: tool_call_end payload

- WHEN a tool finishes (success or error)
- THEN the server emits `data: {"type":"tool_call_end","tool_call_id":"<uuid>","name":"<tool_name>","ok":<bool>,"result":<text-or-json>,"duration_ms":<int>}` AFTER the tool returns

#### Scenario: permission_request payload

- WHEN the agent needs user permission (a tool's `checkPermissions` returns `ASK`)
- THEN the server emits `data: {"type":"permission_request","permission_id":"<uuid>","tool_call_id":"<uuid>","tool_name":"<tool_name>","reason":"<string>","choices":["yes","no","always"]}` then pauses the turn
- AND the client renders this as a chat message with the choices as quick-reply buttons

#### Scenario: permission_request resolved

- WHEN the user submits a chat message matching exactly `yes` / `no` / `always` after a `permission_request`
- THEN the server resumes the agent turn with that decision
- AND emits `data: {"type":"permission_response","permission_id":"<uuid>","decision":"<value>"}`
- AND any other chat message submitted before resolution is queued but not sent to the model until resolution

#### Scenario: message_stop payload

- WHEN an agent turn completes (model returns finish_reason=stop, or max iterations hit, or compact circuit broken, or abort)
- THEN the server emits `data: {"type":"message_stop","finish_reason":"stop"|"length"|"max_iterations"|"compact_broken"|"aborted"}` then closes the SSE connection

#### Scenario: error payload

- WHEN a non-recoverable error occurs (provider returns 5xx after retry exhausted, internal exception)
- THEN the server emits `data: {"type":"error","code":"<stable_code>","message":"<human>"}` then closes the SSE connection
- AND the client renders the error inline and disables input until the user sends a new message

### Requirement: Chat Abort

The system SHALL allow the user to abort an in-flight turn.

#### Scenario: Abort succeeds

- WHEN client sends `POST /api/chat/abort/{stream_id}` with a valid stream_id
- AND the stream is currently open
- THEN the server returns `200 OK` with `{"aborted": true}`
- AND the active `AgentLoop` iteration is interrupted (no further tool calls)

#### Scenario: Abort after stop is idempotent

- WHEN client sends `POST /api/chat/abort/{stream_id}` after the turn already ended
- THEN the server returns `200 OK` with `{"aborted": false, "reason": "already_stopped"}` (no error)

#### Scenario: Abort unknown stream

- WHEN client sends `POST /api/chat/abort/{stream_id}` with unknown id
- THEN the server returns `404 Not Found` with `{"error": "stream_not_found"}`

### Requirement: Slash Commands

The system SHALL accept slash commands via `POST /api/chat/send` (alongside normal chat input), executed by `SlashCommand` bean, with results emitted as a synthetic `message_delta` event.

#### Scenario: /help lists commands

- WHEN user submits `/help` via chat
- THEN the server executes `SlashCommand.help()`
- AND emits a `message_delta` with `delta_type:"text"` and `content` listing all available commands with one-line descriptions

#### Scenario: /clear resets message history

- WHEN user submits `/clear`
- THEN the server executes `SlashCommand.clear()` — clears in-memory `MessageHistory` for the current session
- AND emits `message_delta` with `content: "已清空当前会话历史"` 
- AND does NOT start an agent turn

#### Scenario: /quit closes session

- WHEN user submits `/quit`
- THEN the server executes `SlashCommand.quit()` — closes the current session (flushes JSONL, closes stream)
- AND emits `message_stop` then closes SSE
- AND client receives `{"closed": true}` to redirect to landing page

#### Scenario: /resume (post-add-resume-command)

- WHEN user submits `/resume`
- AND the `add-resume-command` change has been archived (i.e. `SessionStore.loadLatest` is implemented)
- THEN the server executes `SlashCommand.resume()` — restores most recent JSONL session
- AND emits `message_delta` summarizing what was loaded

#### Scenario: Unknown slash command

- WHEN user submits `/foo` (not a registered command)
- THEN the server returns `400` with `{"error": "unknown_command", "command": "/foo"}`
- AND no agent turn starts

### Requirement: Trusted Host Auth

The system SHALL enforce IP-based access control on all `/api/**` endpoints.

#### Scenario: Trusted host accepted

- WHEN request source IP matches an entry in `agent.web.trusted-hosts` (CIDR or single IP)
- AND `server.port` listener accepts the IP
- THEN the request proceeds normally

#### Scenario: Untrusted host rejected

- WHEN request source IP is NOT in `agent.web.trusted-hosts`
- AND the configured bind is LAN-reachable (e.g. `--host=0.0.0.0` or `--host=<LAN-IP>`)
- THEN the server returns `403 Forbidden` with `{"error": "host_not_trusted"}`
- AND logs the source IP once per minute at WARN level

#### Scenario: Loopback always trusted

- WHEN request source IP is `127.0.0.1` or `::1`
- THEN the request is allowed regardless of `trusted-hosts` config
- AND `403` is never returned for loopback

#### Scenario: Default bind is loopback-only

- WHEN `agent.web.host` is not configured
- THEN the server binds `127.0.0.1` only
- AND only loopback requests succeed
- AND no external network is exposed

#### Scenario: Bind to 0.0.0.0 rejected

- WHEN startup is invoked with `--host=0.0.0.0` or `agent.web.host=0.0.0.0`
- THEN the server refuses to start with `IllegalStateException("binding 0.0.0.0 is not supported; specify a concrete LAN IP")`
- AND logs the refusal at WARN level

### Requirement: Current Session

The system SHALL expose current session metadata for the UI to render the session list.

#### Scenario: Active session returned

- WHEN client sends `GET /api/sessions/current`
- AND a session is currently active (turn in progress OR a session file exists for current session_id)
- THEN the server returns `200 OK` with `{"session_id":"<uuid>","started_at":<epoch_ms>,"turn_count":<int>,"tokens_in":<int>,"tokens_out":<int>,"model":"<model>"}`

#### Scenario: No active session

- WHEN client sends `GET /api/sessions/current` and no session has been started yet
- THEN the server returns `200 OK` with `{"session_id": null}` (HTTP 200, NOT 404)

### Requirement: Health Check

The system SHALL expose a lightweight health endpoint for monitoring and readiness probes.

#### Scenario: Healthy server

- WHEN client sends `GET /api/health`
- THEN the server returns `200 OK` with `{"status":"ok","version":"<x.y.z>","uptime_s":<int>}`

#### Scenario: Provider not configured

- WHEN client sends `GET /api/health` and provider.apiKey is missing
- THEN the server returns `200 OK` with `{"status":"degraded","reason":"provider_not_configured"}` (NOT 503 — health endpoint should not fail)

### Requirement: Static Resource Serving

The system SHALL serve the React SPA bundle as static resources from the same Spring server.

#### Scenario: Index served at root

- WHEN client navigates to `http://<host>:<port>/`
- THEN the server returns `200 OK` with `Content-Type: text/html` and the SPA `index.html`
- AND `index.html` references the bundled JS/CSS via `<script src="/assets/*.js">` etc.

#### Scenario: Client-side routes serve same index

- WHEN client navigates to `http://<host>:<port>/sessions/<uuid>` (SPA route)
- THEN the server returns `200 OK` with `index.html` (NOT 404 — SPA handles routing client-side)

#### Scenario: Static assets cache

- WHEN client requests `/assets/index-<hash>.js`
- THEN the server returns `200 OK` with `Cache-Control: public, max-age=31536000, immutable` (1 year)
- AND `index.html` itself has `Cache-Control: no-cache` so updates are picked up immediately

### Requirement: Build Pipeline Integration

The system SHALL integrate React build into Maven build via frontend-maven-plugin.

#### Scenario: mvn package builds backend and frontend

- WHEN developer runs `mvn clean package`
- THEN agent-web module's frontend-maven-plugin runs `npm ci` then `npm run build`
- AND Vite output (under `agent-web/frontend/dist/`) is copied into `agent-web/src/main/resources/static/`
- AND `mvn package` produces a single `agent-web-<version>.jar` containing both Java classes and React assets

#### Scenario: Skip frontend build in CI (optional)

- WHEN CI sets `-Dskip.npm` (custom property) or environment variable `SKIP_NPM=true`
- THEN frontend-maven-plugin skips npm steps (uses cached `static/` if present)
- AND the rest of Maven build proceeds normally

#### Scenario: Frontend build failure breaks Maven

- WHEN `npm run build` exits non-zero (TypeScript error, Vite error)
- THEN frontend-maven-plugin fails the Maven build
- AND the failed artifact is NOT copied to `static/`

### Requirement: Backward Compatibility with CLI

The system SHALL preserve the existing CLI behavior; adding the web profile MUST NOT change CLI defaults.

#### Scenario: Default spring-boot:run starts CLI

- WHEN developer runs `mvn spring-boot:run` (no profile specified)
- THEN the existing CLI behavior is preserved: prints `dsh web:` URL line ONLY when `-Dspring.profiles.active=web` is set
- AND the default `chat` subcommand runs as before
- AND no HTTP server binds to a port

#### Scenario: Explicit web profile starts both

- WHEN developer runs `mvn spring-boot:run -Dspring.profiles.active=web`
- THEN the CLI's REPL is suppressed (no stdin reading)
- AND the HTTP server starts on the configured port
- AND logs `dsh web: http://<host>:<port>` once at startup
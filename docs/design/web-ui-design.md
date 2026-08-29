# Web UI 设计 (v0.1 镜像精简 + SSE 协议 cheat sheet)

## 1. 架构

```
agent-core/                                  agent-web/                                    web/
├── AgentLoop (Spring bean)                ├── ChatStreamService (Sinks.Many)         React 18
├── SlashCommand (CLI)                       ├── SseSessionLogSink (bridge)            + Vite 6
├── PermissionManager                       ├── ChatController                         + TanStack Query
└── SessionStore (JSONL)                    ├── ChatStreamController (SSE)             + Tailwind v4
       ↑                                      ├── HealthController                     + shadcn/ui
       └────── web 端通过 WebAgentRuntime        ├── StaticResourceConfig (SPA)            + react-markdown
              复用 agent-core bean (AgentLoop,    ├── SlashCommandRouter (in-chat)
              SessionStore, PermissionManager)   └── PermissionBridge (LockSupport)
                                                frontend/
                                                ├── src/
                                                │   ├── lib/event-types.ts (SseEvent mirror)
                                                │   ├── lib/sse-client.ts (fetch + ReadableStream)
                                                │   ├── api/chat.ts (HTTP client)
                                                │   ├── components/ChatPanel.tsx
                                                │   ├── components/MessageBubble.tsx
                                                │   ├── components/ToolCallCard.tsx
                                                │   └── components/PermissionCard.tsx
                                                └── App.tsx
```

## 2. SSE 协议 (spec §Requirement: SSE Event Types)

`data: <json>\n\n` 行, 每行一个事件. `event:` 字段=事件类型, `id:`=序号, `data:`=JSON 负载.

| 事件 | data 形状 | 触发时机 |
|------|----------|---------|
| message_start | {type, stream_id, session_id, model, timestamp} | ChatStreamService.create() 立刻 |
| message_delta | {type, delta_type: text|thinking, content} | AgentLoop.onAssistant |
| tool_call_start | {type, tool_call_id, name, args} | AgentLoop.onAssistant 含 toolCalls |
| tool_call_end | {type, tool_call_id, name, ok, result, duration_ms} | AgentLoop.onToolResult |
| permission_request | {type, permission_id, tool_call_id, tool_name, reason, choices} | PermissionBridge.waitForDecision 阻塞前 |
| message_stop | {type, finish_reason: stop\|length\|max_iterations\|compact_broken\|aborted} | AgentLoop.onTurnEnd |

## 3. HTTP API

| Method | Path | Body | Response |
|--------|------|------|----------|
| GET | /api/health | - | 200 + {status: ok\|degraded, version, uptime_s, host, port, reason?} |
| POST | /api/chat/send | {content, session_id?} | 200 + {streamId, sessionId, model} |
| GET | /api/chat/stream/{id} (Accept: text/event-stream) | - | 200 + text/event-stream |
| POST | /api/chat/abort/{id} | - | 200 + {aborted, reason?} |
| POST | /api/chat/decision/{id} | {permission_id, decision} | 200 + {ok} 或 404 |
| POST | /api/chat/slash/{id} | {content} | 200 + SlashResult 或 400 unknown_command |
| GET | /api/sessions/current | - | 200 + {session_id, ...} (v0.1 简化为 null) |

## 4. 配置 (application-web.yml)

```yaml
server:
  port: 8080

agent:
  web:
    host: 127.0.0.1        # 绑 loopback; LAN 写具体 IP, 0.0.0.0 启动时拒
    port: 8080              # 与 server.port 一致
    trusted-hosts: []       # 空=仅 loopback, LAN 设 ['192.168.1.0/24'] 或 ['192.168.1.42']
```

命令行覆盖: `--agent.web.host=192.168.1.42 --agent.web.trusted-hosts=192.168.1.0/24`

## 5. 开发模式

- 后端: `mvn -pl agent-core spring-boot:run` (默认 CLI profile) 或 `-Dspring.profiles.active=web` 起 web
- 前端: `cd agent-web/frontend && npm run dev` (Vite :5173, /api proxy 到 8080)
- 一体化: `mvn -pl agent-web clean package && java -jar agent-web/target/agent-web.jar`

## 6. 已知限制 (v0.1)

- SessionStore / currentSession 仍是占位
- /resume / /history 是静态文本
- AgentLoop 集成走 mock path, 真实串接待 v0.2
- WebIntegrationTest 因 agent-core 没引 webflux-starter 启不起 server, @Disabled 占位

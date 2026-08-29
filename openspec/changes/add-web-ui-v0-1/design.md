## Context

agent-demo 当前只有 CLI（picocli + JLine3 REPL），用户须在终端交互。v0.1 加一个浏览器侧 Web UI，镜像 DSH dsh-web-app + dsh-web-frontend 的形态，但 host 层用 Java 实现。

**当前架构**：

```text
agent-core/ (单 module)
├── src/main/java/com/example/agent/
│   ├── AgentCli.java             # Spring Boot 入口，默认 chat 子命令
│   ├── core/AgentLoop.java       # 主循环
│   ├── session/SessionStore.java # JSONL append-only
│   ├── permission/               # PermissionManager + Decision + PathMatcher
│   ├── tools/                    # Tool 注册中心
│   ├── llm/                      # Provider 接口 + DeepSeek 实现
│   └── cli/                      # picocli 子命令 (ChatCommand / InitCommand / SlashCommand)
└── pom.xml

openspec/changes/add-resume-command/   # WIP, 0/5 tasks
```

**目标架构（v0.1）**：

```text
agent-core/                              # 现有 module, 业务代码不动
agent-web/                               # 新 module
├── pom.xml                              # 依赖 webflux + frontend-maven-plugin
├── src/main/java/com/example/agent/web/
│   ├── WebProfile.java                  # @Configuration @Profile("web") 入口
│   ├── config/
│   │   ├── WebConfig.java               # host / port / trusted-hosts 配置 bean
│   │   └── TrustedHostFilter.java       # OncePerRequestFilter, IP 白名单
│   ├── api/
│   │   ├── ChatController.java          # POST /api/chat/send + /api/chat/abort + /api/sessions/current
│   │   ├── ChatStreamController.java    # GET /api/chat/stream/{id}, @Produces text/event-stream
│   │   ├── HealthController.java        # GET /api/health
│   │   └── dto/                          # request/response/event records
│   ├── stream/
│   │   ├── ChatStreamService.java       # 持有 stream_id → SseEmitter 映射 + AgentLoop 编排
│   │   ├── PermissionBridge.java        # 把 AgentLoop 的 ask → SSE permission_request
│   │   └── ToolCallEventAdapter.java    # AgentLoop 回调 → SSE 事件
│   └── static/StaticResourceConfig.java # SPA fallback
├── src/main/resources/
│   ├── application-web.yml              # 默认 web profile 配置 (127.0.0.1, 空白名单=loopback)
│   └── static/                          # mvn package 时由 frontend-maven-plugin 填充
└── frontend/                            # React 项目
    ├── package.json                     # vite, react 18, tanstack-query, tailwind v4
    ├── vite.config.ts                   # build 输出到 ../src/main/resources/static/
    ├── src/
    │   ├── main.tsx
    │   ├── App.tsx                      # 路由
    │   ├── api/chat.ts                  # fetch + EventSource 封装
    │   ├── components/
    │   │   ├── ChatPanel.tsx
    │   │   ├── MessageBubble.tsx         # 普通消息 + Markdown
    │   │   ├── ToolCallCard.tsx          # ReadFile/EditFile/LsTool 特殊渲染
    │   │   ├── PermissionCard.tsx        # in-chat 权限卡
    │   │   ├── SessionList.tsx           # 顶部下拉
    │   │   └── SlashCommandHelp.tsx      # /help 内容
    │   ├── lib/
    │   │   ├── sse-client.ts             # EventSource + Last-Event-ID 重连
    │   │   └── event-types.ts            # 与后端 ChatStreamService 共享的 TS 类型
    │   └── styles/
    └── tests/                           # vitest + @testing-library/react
```

## Goals / Non-Goals

**Goals:**

- 不动 `agent-core/` 任何业务代码，纯新增
- v0.1 前端能在浏览器跑完一个完整 chat 回合（流式 + tool + slash + 权限 in-chat）
- 单 `mvn package` 出一个自包含 `agent-web.jar`（含 React dist）
- `mvn spring-boot:run` 默认 CLI 行为**不变**；`-Dspring.profiles.active=web` 起 web
- Trusted-host 安全模型与 DSH dsh-web-app 一致

**Non-Goals:**

- 不引入 WebSocket（v0.1 仅 SSE）
- 不引入正式 Permission UI（v0.1 用 in-chat）
- 不接历史 session（v0.1 只显示当前 session）
- 不出 settings 页面
- 不实现多用户 / 登录
- 不动 `cli` spec 行为（CLI REPL 不变）

## Decisions

### D1: Maven 多 module 而非 web profile 单 module

**理由**：
- agent-web 与 agent-core 编译产物、依赖（webflux、frontend-maven-plugin）差异较大，混在一个 module 的 pom 里会让默认 CLI 构建拖慢
- 多 module 让 agent-core 保持纯 JDK + picocli（CLI 路径），webflux 仅在 web module 出现
- v0.2 / v0.3 加新 web 能力时不用碰 agent-core 的 pom

**考虑过**：web profile 单 module + 共享 pom。否决：webflux 的 reactor-netty 依赖会被 CLI 路径拖进去（虽然运行时 lazy，但编译期必须满足）。

### D2: 复用 agent-core Bean，不重写业务逻辑

**理由**：`AgentLoop` / `SessionStore` / `PermissionManager` / `ToolRegistry` 都是 Spring bean。在 `agent-web` 中通过 `@SpringBootApplication(scanBasePackages = "com.example.agent")` 复用同一 ApplicationContext，CLI 与 Web 共享同一组 bean 实例（节省资源 + 行为一致）。

**考虑过**：把 AgentLoop 重写成 HTTP-friendly 版本。否决：复杂度高、bug 面大。

### D3: Spring WebFlux 而非 Spring MVC

**理由**：
- SSE 是 reactive 长连接，WebFlux 原生支持 `SseEmitter` / `Flux<ServerSentEvent>`
- 后续 v0.2 加 WebSocket 也走同栈（不需要混 servlet + reactive）
- pom 里 `spring-boot-starter-webflux` 已包含

**考虑过**：Spring MVC + DeferredResult。否决：SSE 实现复杂，async dispatch 心智负担重。

### D4: SSE 粗粒度事件而非细粒度

**理由**（见 `specs/web-ui §SSE Event Types`）：一个 turn 只需 7 类事件（message_start / message_delta / tool_call_start / tool_call_end / permission_request / message_stop / error）。每类事件内含多个字段，前端按 `delta_type` 分发渲染（text vs thinking vs tool_result）。粗粒度降低事件路由复杂度，前端用 TanStack Query 的 `useInfiniteQuery` 自然处理。

**考虑过**：完全兼容 OpenAI `chat.completion.chunks` 流。否决：tool_call / permission_request 在 OpenAI 协议里没有对应字段，强兼容会损失功能。

### D5: Permission in-chat 而非 modal UI

**理由**：v0.1 缺正经 UI 时，权限卡塞进聊天区 + 输入框 `yes`/`no` 是最简的 UX 路径。后端 `PermissionBridge` 把 `AgentLoop` 的 ask 暂停点映射到 SSE `permission_request`，用户回车即恢复 turn。v0.2 再做正经 modal。

**考虑过**：v0.1 直接 deny 所有 ask 权限。否决：写操作工具（WriteFile/EditFile/ShellTool）全部被拒，v0.1 实用价值大幅下降。

### D6: trusted-host LAN 白名单而非 Bearer Token

**理由**（已与用户确认）：DSH dsh-web-app 默认 LAN 信任栅栏，行为一致。开发机起 web 后，手机同 LAN 输 IP 就能用，零配置。v0.2 再加 Bearer。

**考虑过**：自动生成 token 显示在启动页。否决：增加 v0.1 复杂度（生成 + 显示 + 输入框 + 后端校验），不如 LAN 白名单务实。

### D7: 前端用 TanStack Query + Tailwind v4 + shadcn/ui

**理由**（已与用户确认）：
- TanStack Query 天然适配 SSE → React state（EventSource 包装成 query，配合自动重连）
- Tailwind v4 比 CSS Modules 少维护 CSS，shadcn/ui 提供 chat 用得到的 Card/Button/Input
- Vite + React 18 是当下 React 生态最稳组合

**考虑过**：Zustand + Mantine。否决：TanStack Query 的 SSE query 模型天然契合，状态管理代码量更少。

### D8: frontend-maven-plugin 集成 npm build

**理由**（已与用户确认）：`mvn package` 一个命令出 jar，避免 CI 双构建脚本（npm + maven）。Release 流程简化为 `mvn deploy`。

**考虑过**：GitHub Actions 分两步（先 npm build 再 mvn package）。否决：本地开发体验差，必须先跑 npm 才能 mvn。

### D9: AgentLoop 不修改，桥接器模式

**理由**：`AgentLoop` 现有的回调（`onTextDelta` / `onToolCall` / `onPermissionAsk` 等）通过 listener 暴露。在 `ChatStreamService` 里注册 listener，把回调转成 SSE 事件。AgentLoop 自身零改动。

**考虑过**：给 AgentLoop 加 SSE-aware 适配。否决：污染核心循环。

### D10: 拒绝绑定 0.0.0.0

**理由**：v0.1 阶段不期望支持任意网卡，仅绑具体 LAN IP（`192.168.x.x`）或 `127.0.0.1`。启动时 `--host=0.0.0.0` 直接抛 `IllegalStateException`，避免误暴露。

**考虑过**：警告但放行。否决：v0.1 用户多半是开发，没必要承担这个表面。

## Risks / Trade-offs

### R1: `agent-core` 拖入 webflux 编译期依赖 → CLI jar 体积小幅上升

[Mitigation] agent-core pom **不**加 webflux；仅 agent-web 加。多 module 隔离使 agent-core jar 体积不变。

### R2: SSE 跨 Nginx 反代超时（默认 60s）

[Mitigation] v0.1 文档化需在反代配 `proxy_read_timeout 3600s`；v0.2 若加 WebSocket 可走 `/ws` 路径规避。

### R3: TanStack Query 对 EventSource 的 wrapper 暂无官方支持，需手写 lib/sse-client.ts

[Mitigation] 用原生 `EventSource` API + 自定义 hook `useSseStream`，单文件 ~80 行。包发布后 v0.2 可抽离。

### R4: trusted-host 配置写错 LAN 网段会自我锁死

[Mitigation] 启动时打印已注册 trusted-host 列表到 stdout；命令行 `--print-trusted-hosts` 命令可单独打印；配置 reload 通过 SIGHUP（v0.2）。

### R5: in-chat 权限交互在快速 typing 时可能 race

[Mitigation] 后端 `PermissionBridge` 维护 `permission_id → waiting_thread` 映射；用户提交 chat 时若 pending permission_id 存在则按决策解析，否则当作普通 chat 输入。specs §Requirement: permission_request resolved 已覆盖。

### R6: 前端 Vite build 失败会让整个 mvn build 失败

[Mitigation] frontend-maven-plugin 默认行为即如此（v0.1 接受）；CI 加 `-Dskip.npm=true` 跳过以便紧急 release；本地 dev 体验不变。

## Migration Plan

1. **Phase 1（不动 core）**: 创建 `agent-web` 空 module + `frontend/` 空骨架；`mvn -pl agent-web package` 应能跑（即使空）
2. **Phase 2（health + static）**: 实现 `/api/health` + 静态资源 serving，frontend 只放一个 placeholder `index.html`。可在浏览器看到空白页，验证打包链路
3. **Phase 3（chat send + stream）**: 实现 `ChatController` + `ChatStreamService` + 最简 React chat 面板（仅 text_delta 渲染）。能跑通一个完整 turn
4. **Phase 4（tool + permission）**: 加 tool_call 卡片 + in-chat 权限交互
5. **Phase 5（slash + session list + Markdown + polish）**: 收尾

每 Phase 完成后：
- `mvn test` 全绿（agent-web 加 WebTestClient 测试）
- `mvn -pl agent-web verify` jacoco 门禁通过（按 SPEC §3 强制 LINE≥80%/BRANCH≥70%）
- 中文 commit + push（per AGENTS.md §2.2）

**Rollback**：agent-web 是新 module，回滚只需删除 `agent-web/` 目录 + `pom.xml` 移除 module 声明。`agent-core` 不动。

## Open Questions

- 无（v0.1 范围内所有决策已敲定；v0.2 才讨论 permission UI、session 历史、settings）
package com.example.agent.web.api;

import com.example.agent.permission.PermissionMode;
import com.example.agent.web.api.dto.AbortResponse;
import com.example.agent.web.api.dto.PermissionModeRequest;
import com.example.agent.web.api.dto.SendRequest;
import com.example.agent.web.api.dto.SendResponse;
import com.example.agent.web.stream.ChatStreamService;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@Profile("web")
public class ChatController {
    private final ChatStreamService streams;
    private final Environment env;
    private final SessionOwnerRegistry ownerRegistry;

    public ChatController(ChatStreamService streams, Environment env, SessionOwnerRegistry ownerRegistry) {
        this.streams = streams;
        this.env = env;
        this.ownerRegistry = ownerRegistry;
    }

    @PostMapping("/send")
    public Mono<ResponseEntity<?>> send(@RequestBody SendRequest req, ServerWebExchange exchange) {
        if (req.content() == null || req.content().isBlank()) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "content_empty")));
        }
        // 与 CLI 一致的 key 优先级: env(DEEPSEEK_API_KEY) > application-local.yml(agent.provider.api-key)
        String key = pickFirstNonBlank(env.getProperty("DEEPSEEK_API_KEY"), env.getProperty("agent.provider.api-key"));
        if (key == null || key.isBlank()) {
            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "provider_not_configured", "hint", "set DEEPSEEK_API_KEY 或 application-local.yml 的 agent.provider.api-key")));
        }
        String sessionId = req.sessionId() != null ? req.sessionId() : UUID.randomUUID().toString();
        PermissionMode mode;
        try {
            // 缺省 read_only; 非法值 → 400（不创建流）。
            // PermissionMode.from 接受新 4 档 dsh (plan/ask/danger-full/dontAsk) + 旧 3 档 (read_only/workspace_write/full_access) + 自动 normalize
            mode = req.permissionMode() != null ? PermissionMode.from(req.permissionMode()) : PermissionMode.DEFAULT;
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_mode")));
        }
        // 工作区：缺省使用默认工作区；非法 → 400（不创建流）。
        if (!streams.workspaceExists(req.workspace())) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "workspace_not_found")));
        }
        String model = resolveModel(req.model());
        // add-models-dropdown-v0：透传 reasoningEffort（null/blank = 不切换，沿用 Provider 默认）
        ChatStreamService.ActiveStream meta =
                streams.create(sessionId, model, mode, req.workspace(), req.reasoningEffort());
        // Q2: 注册 stream owner IP (permission 切换时校验)
        ownerRegistry.register(meta.streamId(), clientIp(exchange));
        streams.start(meta.streamId(), req.content());
        return Mono.just(ResponseEntity.ok(new SendResponse(meta.streamId(), sessionId, model)));
    }

    private static String pickFirstNonBlank(String... candidates) {
        for (String s : candidates) {
            if (s != null && !s.isBlank()) return s;
        }
        return null;
    }

    /**
     * add-reasoning-thinking-streaming: 解析模型名。null/空用默认 {@code deepseek-chat}；
     * 非法（不在 supported-models 列表）回退默认（前端可调 /api/chat/models 看合法列表）。
     */
    private String resolveModel(String requested) {
        String fallback = "deepseek-chat";
        if (requested == null || requested.isBlank()) return fallback;
        return ModelRegistry.isSupported(requested, env) ? requested : fallback;
    }

    @PostMapping("/abort/{streamId}")
    public Mono<ResponseEntity<AbortResponse>> abort(@PathVariable String streamId) {
        if (streams.get(streamId) == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(AbortResponse.ofAlreadyStopped()));
        }
        streams.abort(streamId);
        return Mono.just(ResponseEntity.ok(AbortResponse.ofAborted()));
    }

    /**
     * 用户提交 in-chat 权限决策 (spec §Requirement: permission_request 决策)。
     *
     * @param streamId 流 id
     * @param req 决策载荷 (permission_id + decision)
     */
    @PostMapping("/decision/{streamId}")
    public Mono<ResponseEntity<Map<String, Object>>> decision(
            @PathVariable String streamId, @RequestBody com.example.agent.web.api.dto.DecisionRequest req) {
        boolean submitted = streams.submitDecision(streamId, req.permissionId(), req.decision());
        if (!submitted) {
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "permission_not_found")));
        }
        return Mono.just(ResponseEntity.ok(Map.of("ok", true, "permission_id", req.permissionId(), "decision", req.decision())));
    }

    /**
     * 实时切换权限模式（rewrite-permission-mode-dsh T7.1 扩展 escalate + 新 4 档）。
     *
     * @param streamId 流 id
     * @param req 载荷 (mode: 4 档 dsh 或 3 档旧 wire value; escalate: 是否临时升级)
     */
    @PostMapping("/{streamId}/permission")
    public Mono<ResponseEntity<Map<String, Object>>> permission(
            @PathVariable String streamId, @RequestBody PermissionModeRequest req,
            ServerWebExchange exchange) {
        // Q2: 同 IP 校验 (stream owner)
        if (!ownerRegistry.verify(streamId, clientIp(exchange))) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "ip_mismatch")));
        }
        PermissionMode mode;
        try {
            // PermissionMode.from 接受新旧两套 wire value + 自动 normalize (T7.1)
            mode = PermissionMode.from(req.mode());
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_mode")));
        }
        boolean escalate = Boolean.TRUE.equals(req.escalate());
        if (!streams.setPermission(streamId, mode, escalate)) {
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "stream_not_found")));
        }
        return Mono.just(ResponseEntity.ok(Map.of(
                "ok", true,
                "mode", mode.wireValue(),
                "effective_mode", mode.toSandboxMode().wireValue())));
    }

    /** 提取客户端 IP（X-Forwarded-For 优先，否则用 remote address）。 */
    private static String clientIp(ServerWebExchange exchange) {
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma >= 0 ? xff.substring(0, comma) : xff).trim();
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote != null && remote.getAddress() != null
                ? remote.getAddress().getHostAddress()
                : "unknown";
    }
}

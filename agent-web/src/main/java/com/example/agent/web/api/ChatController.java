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
import reactor.core.publisher.Mono;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@Profile("web")
public class ChatController {
    private final ChatStreamService streams;
    private final Environment env;

    public ChatController(ChatStreamService streams, Environment env) {
        this.streams = streams;
        this.env = env;
    }

    @PostMapping("/send")
    public Mono<ResponseEntity<?>> send(@RequestBody SendRequest req) {
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
            // 缺省 read_only；非法值 → 400（不创建流）。
            mode = req.permissionMode() != null ? PermissionMode.from(req.permissionMode()) : PermissionMode.DEFAULT;
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_mode")));
        }
        ChatStreamService.ActiveStream meta = streams.create(sessionId, "deepseek-chat", mode);
        streams.start(meta.streamId(), req.content());
        return Mono.just(ResponseEntity.ok(new SendResponse(meta.streamId(), sessionId, "deepseek-chat")));
    }

    private static String pickFirstNonBlank(String... candidates) {
        for (String s : candidates) {
            if (s != null && !s.isBlank()) return s;
        }
        return null;
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
     * 实时切换权限模式 (spec §Requirement: 权限模式实时切换 → `{"mode":"..."}`)。
     *
     * @param streamId 流 id
     * @param req 载荷 (mode: read_only / workspace_write / full_access)
     */
    @PostMapping("/{streamId}/permission")
    public Mono<ResponseEntity<Map<String, Object>>> permission(
            @PathVariable String streamId, @RequestBody PermissionModeRequest req) {
        PermissionMode mode;
        try {
            mode = PermissionMode.from(req.mode());
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_mode")));
        }
        if (!streams.setPermission(streamId, mode)) {
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "stream_not_found")));
        }
        return Mono.just(ResponseEntity.ok(Map.of("ok", true, "mode", mode.wireValue())));
    }
}

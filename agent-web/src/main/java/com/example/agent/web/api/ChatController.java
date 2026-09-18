package com.example.agent.web.api;

import com.example.agent.permission.PermissionMode;
import com.example.agent.web.api.catalog.ModelCatalog;
import com.example.agent.web.api.catalog.ProviderCatalogProperties;
import com.example.agent.web.api.dto.AbortResponse;
import com.example.agent.web.api.dto.PermissionModeRequest;
import com.example.agent.web.api.dto.SendRequest;
import com.example.agent.web.api.dto.SendResponse;
import com.example.agent.web.stream.ChatStreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatStreamService streams;
    private final Environment env;
    private final ModelCatalog catalog;
    private final ProviderCatalogProperties catalogProps;

    public ChatController(
            ChatStreamService streams,
            Environment env,
            ModelCatalog catalog,
            ProviderCatalogProperties catalogProps) {
        this.streams = streams;
        this.env = env;
        this.catalog = catalog;
        this.catalogProps = catalogProps;
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
        // 工作区：缺省使用默认工作区；非法 → 400（不创建流）。
        if (!streams.workspaceExists(req.workspace())) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "workspace_not_found")));
        }
        String model = resolveModel(req.model());
        if (model == null) {
            // fix-stale-model-fallback：非法 model 必须当场拒绝，不再静默兜回。
            // 修复前的兜底值是 deepseek-chat（同样是被上游停用的 id），两个非法值首尾相接，
            // 最终原样发给上游且无人知晓。合法取值见 /api/chat/models。
            String requested = req.model();
            log.warn("rejecting unknown model requested={} supported={}", requested, catalog.modelIds());
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "error", "invalid_model",
                            "requested", requested,
                            "supported", catalog.modelIds())));
        }
        // add-models-dropdown-v0：透传 reasoningEffort（null/blank = 不切换，沿用 Provider 默认）
        ChatStreamService.ActiveStream meta =
                streams.create(sessionId, model, mode, req.workspace(), req.reasoningEffort());
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
     * 解析请求中的模型名（fix-stale-model-fallback）。
     *
     * <p>以 {@link ModelCatalog}（配置 {@code agent.chat.providers}）为**唯一真源**：
     *
     * <ul>
     *   <li>{@code null}/空白 → 配置默认值 {@code agent.chat.default-model}（启动期已校验其存在于目录中）
     *   <li>命中目录 → 原样返回
     *   <li>非空但未命中 → 返回 {@code null}，由调用方转 400（fail-closed）
     * </ul>
     *
     * <p>返回 {@code null} 表示「调用方发了一个非法模型」，与「未指定」是两回事：
     * 前者必须拒绝，后者才走默认值。这个区分是本 bug 的关键 —— 修复前两者被混为一谈，
     * 于是非法值被静默「修好」成了一个同样非法的值。
     *
     * @param requested 请求体中的 model（可为 null/空白）
     * @return 可用的模型 id；非法时返回 {@code null}
     */
    private String resolveModel(String requested) {
        if (requested == null || requested.isBlank()) {
            return catalogProps.defaultModel();
        }
        return catalog.modelById(requested).isPresent() ? requested : null;
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

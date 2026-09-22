package com.example.agent.web.api;

import com.example.agent.session.MessageFeedbackStore;
import com.example.agent.session.MessageFeedbackStore.FeedbackItem;
import com.example.agent.web.stream.WebAgentRuntime;
import com.example.agent.session.MessageFeedbackStore.FeedbackSnapshot;
import com.example.agent.session.MessageFeedbackStore.MessageFeedbackVersionConflict;
import com.example.agent.session.MessageFeedbackStore.Rating;
import com.example.agent.web.api.dto.FeedbackDeleteRequest;
import com.example.agent.web.api.dto.FeedbackItemDto;
import com.example.agent.web.api.dto.FeedbackPutRequest;
import com.example.agent.web.api.dto.FeedbackResponse;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息反馈 REST API（add-message-feedback F2）。
 *
 * <p>端点（{@code @Profile("web")}）：
 *
 * <ul>
 *   <li>{@code GET /api/feedback/{sessionId}} —— 拉取该会话全部 feedback（无侧车时返 {@code {items:{}}})
 *   <li>{@code PUT /api/feedback/{sessionId}/{messageId}} —— 创建或更新；CAS 冲突 → 409
 *   <li>{@code DELETE /api/feedback/{sessionId}/{messageId}} —— 取消；CAS 冲突 → 409
 * </ul>
 *
 * <p>错误码：
 *
 * <ul>
 *   <li>400 {@code rating_invalid}：rating 非 {@code up} / {@code down}
 *   <li>400 {@code session_invalid} / {@code message_invalid}：白名单未通过（防路径穿越）
 *   <li>404 {@code session_not_found}：session 不存在（PUT/DELETE 才有此校验；GET 始终 200）
 *   <li>409 {@code {current: item|null}}：CAS 冲突；前端据此调和
 * </ul>
 *
 * <p>sidecar 由 {@link MessageFeedbackStore} 自管文件锁与并发安全，本控制器只做参数校验与
 * 错误码映射。
 */
@RestController
@RequestMapping("/api/feedback")
@Profile("web")
public class FeedbackController {

    private final MessageFeedbackStore store;
    private final WebAgentRuntime runtime;

    public FeedbackController(MessageFeedbackStore store, WebAgentRuntime runtime) {
        this.store = store;
        this.runtime = runtime;
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<FeedbackResponse> get(@PathVariable String sessionId) {
        if (!MessageFeedbackStore.isValidUuid(sessionId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        FeedbackSnapshot snap = store.getAll(sessionId);
        Map<String, FeedbackItemDto> items = new LinkedHashMap<>();
        snap.items().forEach((k, v) -> items.put(k, toDto(v)));
        return ResponseEntity.ok(new FeedbackResponse(sessionId, items));
    }

    @PutMapping("/{sessionId}/{messageId}")
    public ResponseEntity<?> put(
            @PathVariable String sessionId,
            @PathVariable String messageId,
            @RequestBody(required = false) FeedbackPutRequest body) {
        if (!MessageFeedbackStore.isValidUuid(sessionId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "session_invalid"));
        }
        if (!MessageFeedbackStore.isValidUuid(messageId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "message_invalid"));
        }
        if (!runtime.hasSession(sessionId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "session_not_found"));
        }
        if (body == null || body.rating() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "rating_invalid"));
        }
        Rating rating = Rating.fromWire(body.rating());
        if (rating == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "rating_invalid"));
        }
        try {
            FeedbackItem written = store.put(sessionId, messageId, rating, body.ifVersion());
            return ResponseEntity.ok(Map.of(
                    "rating", written.rating().wire(),
                    "version", written.version(),
                    "uuid", messageId));
        } catch (MessageFeedbackVersionConflict conflict) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Collections.singletonMap("current", currentAsMap(conflict.current())));
        }
    }

    @DeleteMapping("/{sessionId}/{messageId}")
    public ResponseEntity<?> delete(
            @PathVariable String sessionId,
            @PathVariable String messageId,
            @RequestBody(required = false) FeedbackDeleteRequest body) {
        if (!MessageFeedbackStore.isValidUuid(sessionId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "session_invalid"));
        }
        if (!MessageFeedbackStore.isValidUuid(messageId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "message_invalid"));
        }
        if (!runtime.hasSession(sessionId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "session_not_found"));
        }
        Long ifVersion = body == null ? null : body.ifVersion();
        try {
            store.delete(sessionId, messageId, ifVersion);
            return ResponseEntity.noContent().build();
        } catch (MessageFeedbackVersionConflict conflict) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Collections.singletonMap("current", currentAsMap(conflict.current())));
        }
    }

    /** 序列化为 JSON：null → {@code null}（Map.of 不接受 null，用 LinkedHashMap 兜底）；否则 {@code {rating,version}}。 */
    private static Map<String, Object> currentAsMap(FeedbackItem item) {
        if (item == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rating", item.rating().wire());
        m.put("version", item.version());
        return m;
    }

    private static FeedbackItemDto toDto(FeedbackItem item) {
        return new FeedbackItemDto(item.rating().wire(), item.version(), item.updatedAt());
    }
}
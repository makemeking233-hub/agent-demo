package com.example.agent.web.api;

import com.example.agent.core.Message;
import com.example.agent.session.SessionStore;
import com.example.agent.web.api.dto.SessionMessageDto;
import com.example.agent.web.api.dto.SessionMessagesResponse;
import com.example.agent.web.api.dto.SessionSummaryDto;
import com.example.agent.web.api.dto.ToolCallDto;
import com.example.agent.web.stream.WebAgentRuntime;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话查询 API（T5.2 + v0.3 会话重进恢复）。
 *
 * <p>{@code GET /api/sessions/current}（spec §Requirement: Current Session）：
 *
 * <ul>
 *   <li>v0.1 简化：没有"活动 session"概念时返 {@code {session_id: null}}（HTTP 200，不返 404）。
 *   <li>v0.2 接 SessionStore 后返回实时 session 元数据（当前仍返 null；前端直接持久化 session_id）。
 * </ul>
 *
 * <p>{@code GET /api/sessions/{sessionId}/messages}（v0.3 新增）：返回某会话的消息历史，供前端
 * 在浏览器刷新 / 重进后回填对话区。未知会话返回 404。
 */
@RestController
@RequestMapping("/api/sessions")
@Profile("web")
public class SessionController {

    private final WebAgentRuntime runtime;

    public SessionController(WebAgentRuntime runtime) {
        this.runtime = runtime;
    }

    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> current() {
        // Map.of 禁止 null 值；用 HashMap 承载 {session_id: null} 语义（spec §Requirement: Current Session）。
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("session_id", null);
        return ResponseEntity.ok(body);
    }

    /**
     * 列出现实会话（add-session-switch change）。
     *
     * @return {@code 200} 含会话摘要列表（id/title/preview/workspace，按 mtime 降序）
     */
    @GetMapping
    public ResponseEntity<List<SessionSummaryDto>> list() {
        Path sessionsDir = runtime.agentDataDir().resolve("sessions");
        List<SessionSummaryDto> result = new ArrayList<>();
        for (String id : SessionStore.listSessions(sessionsDir)) {
            String title = id;
            String preview = "";
            List<Message> msgs = runtime.messagesFor(id);
            if (!msgs.isEmpty()) {
                Message first = msgs.get(0);
                title = first.content().lines().findFirst().orElse(id);
                if (title.trim().isEmpty()) title = id;
                preview = first.content().lines().skip(1).findFirst().orElse("");
                if (title.equals(preview)) preview = "";
            }
            result.add(new SessionSummaryDto(id, title, preview, "agent-demo"));
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 返回某会话的消息历史（v0.3 会话重进恢复）。
     *
     * @param sessionId 会话 id
     * @return {@code 200} 含 {@code {session_id, messages}}；未知会话返 {@code 404}
     */
    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<SessionMessagesResponse> messages(@PathVariable String sessionId) {
        if (!runtime.hasSession(sessionId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        List<SessionMessageDto> messages =
                runtime.messagesFor(sessionId).stream().map(SessionController::toDto).toList();
        return ResponseEntity.ok(new SessionMessagesResponse(sessionId, messages));
    }

    /** 把领域消息映射为 DTO。 */
    private static SessionMessageDto toDto(Message m) {
        if (m instanceof Message.Assistant a) {
            return new SessionMessageDto(
                    m.role(),
                    m.content(),
                    a.toolCalls() == null
                            ? List.of()
                            : a.toolCalls().stream()
                                    .map(tc -> new ToolCallDto(tc.id(), tc.name(), tc.argumentsJson()))
                                    .toList(),
                    null,
                    false);
        }
        if (m instanceof Message.ToolResult t) {
            return new SessionMessageDto(m.role(), m.content(), List.of(), t.toolCallId(), t.isError());
        }
        return new SessionMessageDto(m.role(), m.content(), List.of(), null, false);
    }
}

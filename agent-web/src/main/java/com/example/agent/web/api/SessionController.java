package com.example.agent.web.api;

import com.example.agent.core.Message;
import com.example.agent.session.SessionResumeLoader;
import com.example.agent.session.SessionStore;
import com.example.agent.web.api.dto.SessionMessageDto;
import com.example.agent.web.api.dto.SessionMessagesResponse;
import com.example.agent.web.api.dto.SessionSummaryDto;
import com.example.agent.web.api.dto.ToolCallDto;
import com.example.agent.web.stream.WebAgentRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话查询 API（T5.2 + add-session-switch + add-session-management）。
 *
 * <p>端点：
 *
 * <ul>
 *   <li>{@code GET /api/sessions} 列出现实会话（默认排除归档）；{@code ?archived=true} 列出归档会话
 *   <li>{@code DELETE /api/sessions/{id}} 归档（软删除）某会话
 *   <li>{@code POST /api/sessions/{id}/restore} 恢复某归档会话
 *   <li>{@code GET /api/sessions/{id}/messages} 返回会话消息历史
 *   <li>{@code GET /api/sessions/current} 当前会话元数据（恒为 null，前端持久化 session_id）
 * </ul>
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
     * 列出会话摘要（add-session-switch + add-session-management）。
     *
     * @param archived 为 true 时列出归档会话，否则列出现实会话（默认排除归档）
     * @return 会话摘要列表（按 mtime 降序；含 id/title/preview/workspace/time）
     */
    @GetMapping
    public ResponseEntity<List<SessionSummaryDto>> list(
            @RequestParam(name = "archived", defaultValue = "false") boolean archived) {
        return ResponseEntity.ok(
                archived ? buildArchivedSummaries() : buildActiveSummaries());
    }

    /**
     * 归档（软删除）某会话（add-session-management）。
     *
     * @param sessionId 会话 id
     * @return {@code 200} 归档成功；无此会话 {@code 404}
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> archive(@PathVariable String sessionId) {
        boolean ok = runtime.archiveSession(sessionId);
        if (!ok) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "session_not_found"));
        }
        return ResponseEntity.ok(Map.of("ok", true, "session_id", sessionId));
    }

    /**
     * 恢复某归档会话（add-session-management）。
     *
     * @param sessionId 会话 id
     * @return {@code 200} 恢复成功；无此归档 {@code 404}
     */
    @PostMapping("/{sessionId}/restore")
    public ResponseEntity<Map<String, Object>> restore(@PathVariable String sessionId) {
        boolean ok = runtime.restoreSession(sessionId);
        if (!ok) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "session_not_found"));
        }
        return ResponseEntity.ok(Map.of("ok", true, "session_id", sessionId));
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

    // ---------- 内部 ----------

    private List<SessionSummaryDto> buildActiveSummaries() {
        Path sessionsDir = runtime.sessionsDir();
        List<SessionSummaryDto> out = new ArrayList<>();
        for (String id : SessionStore.listSessions(sessionsDir)) {
            Derived d = derive(runtime.messagesFor(id), id);
            out.add(new SessionSummaryDto(
                    id, d.title(), d.preview(), "agent-demo", mtime(sessionsDir.resolve(id + ".jsonl"))));
        }
        return out;
    }

    private List<SessionSummaryDto> buildArchivedSummaries() {
        Path sessionsDir = runtime.sessionsDir();
        Path archiveDir = sessionsDir.resolve(".archive");
        List<SessionSummaryDto> out = new ArrayList<>();
        for (String id : SessionStore.listArchived(sessionsDir)) {
            Derived d =
                    derive(SessionResumeLoader.loadArchivedById(sessionsDir, id).messages(), id);
            out.add(new SessionSummaryDto(
                    id, d.title(), d.preview(), "agent-demo", mtime(archiveDir.resolve(id + ".jsonl"))));
        }
        return out;
    }

    /** 从首条消息派生标题与预览。 */
    private static Derived derive(List<Message> msgs, String id) {
        String title = id;
        String preview = "";
        if (!msgs.isEmpty()) {
            Message first = msgs.get(0);
            title = first.content().lines().findFirst().orElse(id);
            if (title.trim().isEmpty()) title = id;
            preview = first.content().lines().skip(1).findFirst().orElse("");
            if (title.equals(preview)) preview = "";
        }
        return new Derived(title, preview);
    }

    private static long mtime(Path file) {
        try {
            FileTime t = Files.getLastModifiedTime(file);
            return t == null ? 0L : t.toMillis();
        } catch (Exception e) {
            return 0L;
        }
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

    /** 标题+预览派生结果。 */
    private record Derived(String title, String preview) {}
}

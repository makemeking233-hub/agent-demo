package com.example.agent.web.api;

import com.example.agent.core.Message;
import com.example.agent.session.SessionAgeBucket;
import com.example.agent.session.SessionEntry;
import com.example.agent.session.SessionResumeLoader;
import com.example.agent.session.SessionStore;
import com.example.agent.web.api.dto.RenameRequest;
import com.example.agent.web.api.dto.SessionMessageDto;
import com.example.agent.web.api.dto.SessionMessagesResponse;
import com.example.agent.web.api.dto.SessionStatsDto;
import com.example.agent.web.api.dto.SessionSummaryDto;
import com.example.agent.web.api.dto.ToolCallDto;
import com.example.agent.web.stream.WebAgentRuntime;
import com.example.agent.session.WorkspaceStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
            @RequestParam(name = "archived", defaultValue = "false") boolean archived,
            @RequestParam(name = "workspace", required = false) String workspace) {
        return ResponseEntity.ok(
                archived
                        ? buildArchivedSummaries(workspace)
                        : buildActiveSummaries(workspace));
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
     * 会话重命名（add-workspaces-and-rename）：写入侧车 {@code <id>.meta.json{title}} 覆盖自动标题。
     *
     * @param sessionId 会话 id
     * @param req 载荷（{@code title} 非空）
     * @return {@code 200} 重命名成功；会话不存在 {@code 404}；title 空 {@code 400}
     */
    @PostMapping("/{sessionId}/rename")
    public ResponseEntity<Map<String, Object>> rename(
            @PathVariable String sessionId, @RequestBody RenameRequest req) {
        if (!runtime.hasSession(sessionId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "session_not_found"));
        }
        if (req.title() == null || req.title().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "title_empty"));
        }
        boolean ok = SessionStore.writeTitle(runtime.sessionsDir(), sessionId, req.title());
        if (!ok) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "rename_failed"));
        }
        return ResponseEntity.ok(Map.of("ok", true, "title", req.title()));
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
        List<Message> msgs = runtime.messagesFor(sessionId);
        // add-message-actions P2：把存档里的 per-message 读数（message_meta 条目）按 assistant 序号
        // 贴回消息，使刷新后 clock 仍在（见 loadAssistantMeta 的「数量不一致则整体放弃」保护）
        List<Map<String, Object>> assistantMeta =
                loadAssistantMeta(runtime.sessionsDirFor(null), sessionId);
        long assistantCount = msgs.stream().filter(m -> "assistant".equals(m.role())).count();
        boolean aligned = assistantCount == assistantMeta.size();
        List<SessionMessageDto> messages = new ArrayList<>(msgs.size());
        int assistantIndex = 0;
        for (Message m : msgs) {
            boolean isAssistant = "assistant".equals(m.role());
            Map<String, Object> meta = null;
            if (isAssistant) {
                if (aligned && assistantIndex < assistantMeta.size()) {
                    meta = assistantMeta.get(assistantIndex);
                }
                assistantIndex++;
            }
            messages.add(toDto(m, meta));
        }
        return ResponseEntity.ok(new SessionMessagesResponse(sessionId, messages));
    }

    /**
     * 读取某会话存档里的 per-message 读数（add-message-actions P2）。
     *
     * <p>返回列表**按 assistant 条目在存档中的出现顺序**索引：第 i 个元素就是第 i 条 assistant 消息
     * 的读数（无读数时为 {@code null}）。这样前端只需按 assistant 序号对齐，无需自己解析存档。
     *
     * <p>读数是 {@code SessionRecorder} 在回合结束时追加的 {@code meta(key="message_meta")} 条目，
     * 内含 {@code uuid} —— 用它反查该 assistant 条目在存档中的序号，避免"数消息"式推断。
     * 存档里的 assistant 条目若少于消息列表（例如孤儿 tool_result 触发了合成骨架），序号会错位，
     * 故调用方需按 list 尺寸与消息数量是否一致决定是否采用（不一致就整体放弃，宁可不显示读数）。
     *
     * @param sessionsDir sessions 目录
     * @param sessionId   会话 id
     * @return assistant 序号 → 读数（可含 null 元素）；无存档/无读数时为空 list
     */
    private static List<Map<String, Object>> loadAssistantMeta(Path sessionsDir, String sessionId) {
        List<SessionEntry> entries = SessionStore.loadById(sessionsDir, sessionId);
        if (entries.isEmpty()) return List.of();
        Map<String, Integer> ordinalByUuid = new HashMap<>();
        Map<String, Map<String, Object>> metaByUuid = new HashMap<>();
        int assistantCount = 0;
        for (SessionEntry e : entries) {
            if ("assistant".equals(e.type())) {
                if (e.uuid() != null) ordinalByUuid.put(e.uuid(), assistantCount);
                assistantCount++;
                continue;
            }
            if (!"meta".equals(e.type()) || e.extras() == null) continue;
            if (!"message_meta".equals(e.extras().get("key"))) continue;
            Object value = e.extras().get("value");
            if (!(value instanceof Map<?, ?> raw)) continue;
            Map<String, Object> meta = asStringMap(raw);
            Object uuid = meta.get("uuid");
            if (uuid != null) metaByUuid.put(String.valueOf(uuid), meta);
        }
        if (assistantCount == 0) return List.of();
        List<Map<String, Object>> out = new ArrayList<>(Collections.nCopies(assistantCount, null));
        metaByUuid.forEach(
                (uuid, meta) -> {
                    Integer at = ordinalByUuid.get(uuid);
                    if (at != null) out.set(at, meta);
                });
        return out;
    }

    /** Jackson 反序列化出的 extras 是 {@code Map<String,Object>}，这里只做受检转换。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringMap(Map<?, ?> raw) {
        return (Map<String, Object>) raw;
    }

    /**
     * 会话累计统计（add-session-stats-bar）：供首屏 / resume 回填底部状态栏。
     *
     * @param sessionId 会话 id
     * @param workspace 归属工作区（可选，缺省默认工作区）
     * @return {@code 200} 含累计与派生指标；会话不存在 {@code 404}
     */
    @GetMapping("/{sessionId}/stats")
    public ResponseEntity<?> stats(
            @PathVariable String sessionId,
            @RequestParam(name = "workspace", required = false) String workspace) {
        if (!runtime.hasSession(workspace, sessionId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "session_not_found"));
        }
        return ResponseEntity.ok(SessionStatsDto.from(runtime.statsFor(workspace, sessionId)));
    }

    // ---------- 内部 ----------

    private List<SessionSummaryDto> buildActiveSummaries(String workspace) {
        Path sessionsDir = runtime.sessionsDirFor(workspace);
        List<SessionSummaryDto> out = new ArrayList<>();
        for (String id : SessionStore.listSessions(sessionsDir)) {
            Derived d = derive(runtime.messagesFor(workspace, id), id, sessionsDir);
            out.add(new SessionSummaryDto(
                    id, d.title(), d.preview(), workspaceName(workspace), mtime(sessionsDir.resolve(id + ".jsonl"))));
        }
        return out;
    }

    private List<SessionSummaryDto> buildArchivedSummaries(String workspace) {
        Path sessionsDir = runtime.sessionsDirFor(workspace);
        Path archiveDir = sessionsDir.resolve(".archive");
        long now = System.currentTimeMillis();
        List<SessionSummaryDto> out = new ArrayList<>();
        for (String id : SessionStore.listArchived(sessionsDir)) {
            Derived d =
                    derive(
                            SessionResumeLoader.loadArchivedById(sessionsDir, id).messages(),
                            id,
                            archiveDir);
            long lastModified = mtime(archiveDir.resolve(id + ".jsonl"));
            out.add(new SessionSummaryDto(
                    id,
                    d.title(),
                    d.preview(),
                    workspaceName(workspace),
                    lastModified,
                    // auto-archive-stale-sessions：分档在后端算，保证"7 天阈值"与"7–14 天档"
                    // 同源一致；前端只按该字段分组渲染
                    SessionAgeBucket.of(lastModified, now).key()));
        }
        return out;
    }

    private static String workspaceName(String workspace) {
        return workspace == null || workspace.isBlank()
                ? WorkspaceStore.DEFAULT_WORKSPACE
                : workspace;
    }

    /** 标题：优先侧车自定义标题，否则从首条消息派生；预览始终从首条消息派生。 */
    private static Derived derive(List<Message> msgs, String id, Path sessionsDir) {
        String custom = SessionStore.readTitle(sessionsDir, id);
        String title = custom != null && !custom.isBlank() ? custom : id;
        String preview = "";
        if (!msgs.isEmpty()) {
            Message first = msgs.get(0);
            if (custom == null || custom.isBlank()) {
                title = first.content().lines().findFirst().orElse(id);
                if (title.trim().isEmpty()) title = id;
            }
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

    /**
     * 把领域消息映射为 DTO。
     *
     * @param m    领域消息
     * @param meta 该条消息的 per-message 读数（add-message-actions P2；非 assistant 或无数时 null）
     * @return 消息 DTO
     */
    private static SessionMessageDto toDto(Message m, Map<String, Object> meta) {
        String uuid = meta == null ? null : asText(meta.get("uuid"));
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
                    false,
                    uuid,
                    meta);
        }
        if (m instanceof Message.ToolResult t) {
            return new SessionMessageDto(m.role(), m.content(), List.of(), t.toolCallId(), t.isError(), null, null);
        }
        return new SessionMessageDto(m.role(), m.content(), List.of(), null, false, null, null);
    }

    /** {@code Object → String}（null 安全；非字符串走 {@code String.valueOf}）。 */
    private static String asText(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /** 标题+预览派生结果。 */
    private record Derived(String title, String preview) {}
}

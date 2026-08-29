package com.example.agent.web.api;

import com.example.agent.log.SessionEventReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 日志查看 API（spec §Requirement: 日志查看 API）。
 *
 * <p>受 {@code TrustedHostFilter} 保护（/api/** 自动覆盖）。
 *
 * <ul>
 *   <li>GET /api/logs/sessions — 列出日志会话目录
 *   <li>GET /api/logs/sessions/{id}/events?offset=&amp;limit= — 分页读 session.jsonl 事件
 *   <li>GET /api/logs/sessions/{id}/files/{name} — 读 chat/tools/thinking/session 文本
 * </ul>
 *
 * <p>安全：{@code id}/{@code name} 白名单校验 + 路径解析限制在 logs 根内（防路径穿越）。
 */
@RestController
@RequestMapping("/api/logs")
@Profile("web")
public class LogController {

    /** 合法会话 id 字符集（SessionId 生成格式 {YYYY-MM-DD}T{HH-mm-ss}-{uuid8}） */
    private static final Pattern SAFE_ID = Pattern.compile("^[0-9A-Za-z][0-9A-Za-z._-]*$");

    /** 可读文件白名单 */
    private static final Set<String> ALLOWED_FILES =
            Set.of("session.jsonl", "chat.log", "thinking.log", "tools.log");

    /** 日志根目录（{@code <dir>/sessions/} 下为会话目录） */
    private final Path logsRoot;

    /** 会话目录根（{@code logsRoot/sessions}） */
    private final Path sessionsRoot;

    /**
     * 生产构造：从配置读取日志根目录（缺省 {@code ~/.agent-demo/logs}）。
     *
     * @param loggingDir 配置的日志根（{@code agent.logging.dir}）
     */
    public LogController(@Value("${agent.logging.dir:}") String loggingDir) {
        this(Paths.get(
                loggingDir == null || loggingDir.isBlank()
                        ? System.getProperty("user.home") + "/.agent-demo/logs"
                        : loggingDir));
    }

    /** 测试构造：注入日志根目录。 */
    LogController(Path logsRoot) {
        this.logsRoot = logsRoot;
        this.sessionsRoot = logsRoot.resolve("sessions");
    }

    /** 列出日志会话目录。 */
    @GetMapping("/sessions")
    public List<Map<String, Object>> listSessions() {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!Files.isDirectory(sessionsRoot)) return out;
        try (var dirs = Files.list(sessionsRoot)) {
            dirs.filter(Files::isDirectory)
                    .sorted()
                    .forEach(
                            dir -> {
                                Map<String, Object> item = new LinkedHashMap<>();
                                item.put("id", dir.getFileName().toString());
                                item.put("hasEvents", Files.exists(dir.resolve("session.jsonl")));
                                item.put("hasChat", Files.exists(dir.resolve("chat.log")));
                                item.put("hasTools", Files.exists(dir.resolve("tools.log")));
                                out.add(item);
                            });
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "list sessions failed");
        }
        return out;
    }

    /** 分页读取会话事件。 */
    @GetMapping("/sessions/{id}/events")
    public Map<String, Object> events(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "100") int limit) {
        if (offset < 0 || limit < 1 || limit > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad offset/limit");
        }
        Path sessionFile = resolveSessionFile(id, "session.jsonl");
        if (!Files.exists(sessionFile)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found");
        }
        List<Map<String, Object>> all = SessionEventReader.readEvents(sessionFile);
        int from = Math.min(offset, all.size());
        int to = Math.min(offset + limit, all.size());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("events", all.subList(from, to));
        body.put("total", all.size());
        return body;
    }

    /** 读取可读日志文件（文本）。 */
    @GetMapping("/sessions/{id}/files/{name}")
    public ResponseEntity<String> file(@PathVariable String id, @PathVariable String name) {
        if (!ALLOWED_FILES.contains(name)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "file not allowed");
        }
        Path p = resolveSessionFile(id, name);
        if (!Files.exists(p)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "file not found");
        }
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(Files.readString(p));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "read failed");
        }
    }

    /** 校验 id 合法性并解析会话文件路径（防路径穿越）。 */
    private Path resolveSessionFile(String id, String fileName) {
        if (id == null || !SAFE_ID.matcher(id).matches() || id.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid session id");
        }
        Path dir = sessionsRoot.resolve(id).normalize();
        if (!dir.startsWith(sessionsRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid session id");
        }
        return dir.resolve(fileName);
    }
}

package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** LogController 单测：列会话 / 分页事件 / 文件白名单 / 路径穿越防护。 */
class LogControllerTest {
    @TempDir Path tmp;

    private LogController newController() throws Exception {
        Path sessions = Files.createDirectories(tmp.resolve("sessions"));
        Path sess = Files.createDirectories(sessions.resolve("2026-08-29T10-23-45-abc12345"));
        Files.writeString(sess.resolve("session.jsonl"), "{\"seq\":0,\"type\":\"session\"}\n{\"seq\":1,\"type\":\"user/message\",\"content\":\"hi\"}\n");
        Files.writeString(sess.resolve("chat.log"), "对话内容\n");
        return new LogController(tmp);
    }

    @Test
    void listsSessions() throws Exception {
        LogController c = newController();
        List<Map<String, Object>> sessions = c.listSessions();
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0)).containsEntry("id", "2026-08-29T10-23-45-abc12345");
        assertThat(sessions.get(0)).containsEntry("hasEvents", true);
    }

    @Test
    void paginatesEvents() throws Exception {
        LogController c = newController();
        Map<String, Object> body = c.events("2026-08-29T10-23-45-abc12345", 0, 1);
        assertThat((int) body.get("total")).isEqualTo(2);
        assertThat(((List<?>) body.get("events"))).hasSize(1);
    }

    @Test
    void readsAllowedFile() throws Exception {
        LogController c = newController();
        var resp = c.file("2026-08-29T10-23-45-abc12345", "chat.log");
        assertThat(resp.getBody()).contains("对话内容");
    }

    @Test
    void rejectsUnknownFile() throws Exception {
        LogController c = newController();
        assertThrows(ResponseStatusException.class, () -> c.file("2026-08-29T10-23-45-abc12345", "secret.txt"));
    }

    @Test
    void rejectsTraversalId() throws Exception {
        LogController c = newController();
        assertThrows(ResponseStatusException.class, () -> c.events("..%2F..%2Fetc", 0, 10));
        assertThrows(ResponseStatusException.class, () -> c.events("..", 0, 10));
        assertThrows(ResponseStatusException.class, () -> c.events("a/b", 0, 10));
    }

    @Test
    void rejectsBadPagination() throws Exception {
        LogController c = newController();
        assertThrows(ResponseStatusException.class, () -> c.events("2026-08-29T10-23-45-abc12345", -1, 10));
        assertThrows(ResponseStatusException.class, () -> c.events("2026-08-29T10-23-45-abc12345", 0, 0));
        assertThrows(ResponseStatusException.class, () -> c.events("2026-08-29T10-23-45-abc12345", 0, 5000));
    }

    @Test
    void eventsNotFoundForMissingSession() throws Exception {
        LogController c = newController();
        assertThrows(ResponseStatusException.class, () -> c.events("no-such-session", 0, 10));
    }

    @Test
    void fileNotFoundWhenMissing() throws Exception {
        LogController c = newController();
        assertThrows(ResponseStatusException.class, () -> c.file("2026-08-29T10-23-45-abc12345", "thinking.log"));
    }

    @Test
    void listsEmptyWhenNoSessions() throws Exception {
        LogController c = new LogController(tmp); // 无 sessions 子目录
        assertThat(c.listSessions()).isEmpty();
    }
}

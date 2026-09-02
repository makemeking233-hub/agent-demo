package com.example.agent.web.api;

import com.example.agent.session.SessionStore;
import com.example.agent.session.WorkspaceStore;
import com.example.agent.web.api.dto.CreateWorkspaceRequest;
import com.example.agent.web.api.dto.WorkspaceDto;
import com.example.agent.web.stream.WebAgentRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作区 API（add-workspaces-and-rename）。
 *
 * <p>端点：
 *
 * <ul>
 *   <li>{@code GET /api/workspaces} 列出全部工作区（含默认 agent-demo）
 *   <li>{@code POST /api/workspaces} 创建工作区（真实运行目录；400/409 校验失败）
 * </ul>
 */
@RestController
@RequestMapping("/api/workspaces")
@Profile("web")
public class WorkspaceController {

    private final WebAgentRuntime runtime;

    public WorkspaceController(WebAgentRuntime runtime) {
        this.runtime = runtime;
    }

    @GetMapping
    public ResponseEntity<List<WorkspaceDto>> list() {
        Path agentDataDir = runtime.agentDataDir();
        List<WorkspaceDto> out = new ArrayList<>();
        for (WorkspaceStore.Workspace ws : WorkspaceStore.list(agentDataDir)) {
            Path sessionsDir = ws.sessionsDir();
            List<String> ids = SessionStore.listSessions(sessionsDir);
            long lastActive = lastModified(sessionsDir);
            out.add(new WorkspaceDto(ws.name(), ws.dir().toString(), ids.size(), lastActive));
        }
        return ResponseEntity.ok(out);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateWorkspaceRequest req) {
        WorkspaceStore.CreateResult r =
                WorkspaceStore.create(runtime.agentDataDir(), req.name(), req.dir());
        if (!r.ok()) {
            return ResponseEntity.status(statusFor(r.error()))
                    .body(Map.of("error", r.error()));
        }
        WorkspaceStore.Workspace ws = r.workspace();
        return ResponseEntity.ok(
                Map.of("ok", true, "name", ws.name(), "dir", ws.dir().toString()));
    }

    private static HttpStatus statusFor(String error) {
        return switch (error == null ? "" : error) {
            case "workspace_exists" -> HttpStatus.CONFLICT;
            case "name_invalid", "dir_not_found", "dir_not_absolute" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /** 工作区目录下最近会话的 mtime（无会话/sessions 目录缺失时 0）。 */
    private static long lastModified(Path sessionsDir) {
        if (sessionsDir == null || !Files.isDirectory(sessionsDir)) return 0L;
        try (var stream = Files.list(sessionsDir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                    .mapToLong(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis();
                        } catch (Exception e) {
                            return 0L;
                        }
                    })
                    .max()
                    .orElse(0L);
        } catch (Exception e) {
            return 0L;
        }
    }
}

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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作区 API v2（align-dsh-workspace）。
 *
 * <p>端点：
 *
 * <ul>
 *   <li>{@code GET /api/workspaces} 列出全部工作区（含默认 agent-demo + 持久化 order + status）
 *   <li>{@code POST /api/workspaces} 创建工作区（body: {@code {path}}，name + title 自动派生）
 *   <li>{@code DELETE /api/workspaces/{name}} 删除 record（不动 dir / session log）
 *   <li>{@code PATCH /api/workspaces/{name}} 重命名 title（body: {@code {title}}）
 *   <li>{@code PUT /api/workspaces/order} 更新持久化 order（body: {@code {order:[names...]}}）
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
        List<WorkspaceDto> out = new ArrayList<>();
        for (WorkspaceStore.Workspace ws : WorkspaceStore.list(runtime.agentDataDir())) {
            Path sessionsDir = ws.sessionsDir();
            List<String> ids = SessionStore.listSessions(sessionsDir);
            long lastActive = lastModified(sessionsDir);
            out.add(new WorkspaceDto(
                    ws.name(),
                    ws.id(),
                    ws.title(),
                    ws.path().toString(),
                    ids.size(),
                    lastActive,
                    ws.updatedAt(),
                    ws.status().name().toLowerCase()));
        }
        return ResponseEntity.ok(out);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateWorkspaceRequest req) {
        if (req == null || req.path() == null || req.path().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "path_empty"));
        }
        Path dir;
        try {
            dir = Path.of(req.path());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "path_invalid"));
        }
        WorkspaceStore.CreateResult r = WorkspaceStore.create(runtime.agentDataDir(), dir);
        if (!r.ok()) {
            return ResponseEntity.status(statusFor(r.error()))
                    .body(Map.of("error", r.error()));
        }
        WorkspaceStore.Workspace ws = r.workspace();
        return ResponseEntity.ok(
                Map.of("ok", true, "name", ws.name(), "title", ws.title(),
                        "dir", ws.path().toString(), "id", ws.id()));
    }

    /** 删除 workspace record（不动 dir / session log）。name 不存在返回 404。 */
    @DeleteMapping("/{name}")
    public ResponseEntity<?> delete(@PathVariable String name) {
        boolean ok = WorkspaceStore.delete(runtime.agentDataDir(), name);
        if (!ok) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "workspace_not_found"));
        }
        return ResponseEntity.noContent().build();
    }

    /** 重命名 display title（不动 dir/path/name/id）。失败（不存在 / blank title）返回 400/404。 */
    @PatchMapping("/{name}")
    public ResponseEntity<?> rename(@PathVariable String name, @RequestBody Map<String, String> body) {
        if (body == null || body.get("title") == null || body.get("title").isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "title_invalid"));
        }
        boolean ok = WorkspaceStore.rename(runtime.agentDataDir(), name, body.get("title"));
        if (!ok) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "workspace_not_found"));
        }
        return ResponseEntity.ok(Map.of("ok", true, "name", name, "title", body.get("title")));
    }

    /** 更新持久化 durable order（拖拽重排）。 */
    @PutMapping("/order")
    public ResponseEntity<?> updateOrder(@RequestBody Map<String, List<String>> body) {
        List<String> order = body == null ? null : body.get("order");
        if (order == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "order_invalid"));
        }
        // DSH 语义：order 仅包含 user workspaces（不含默认 agent-demo）
        var userNames = new java.util.HashSet<>();
        for (WorkspaceStore.Workspace ws : WorkspaceStore.list(runtime.agentDataDir())) {
            if (!WorkspaceStore.DEFAULT_WORKSPACE.equals(ws.name())) userNames.add(ws.name());
        }
        if (order.size() != userNames.size() || !userNames.containsAll(order)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "order_mismatch"));
        }
        // 直接覆盖（DSH 语义：order = 前端最新视图）
        WorkspaceStore.replaceOrder(runtime.agentDataDir(), order);
        return ResponseEntity.ok(Map.of("ok", true, "order", order));
    }

    private static HttpStatus statusFor(String error) {
        return switch (error == null ? "" : error) {
            case "workspace_exists" -> HttpStatus.CONFLICT;
            case "name_invalid", "dir_not_found", "dir_not_absolute", "path_invalid" -> HttpStatus.BAD_REQUEST;
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
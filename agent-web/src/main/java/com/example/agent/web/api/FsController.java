package com.example.agent.web.api;

import com.example.agent.web.api.dto.FsDrivesResponse;
import com.example.agent.web.api.dto.FsEntry;
import com.example.agent.web.api.dto.FsHomeResponse;
import com.example.agent.web.api.dto.FsListResponse;
import com.example.agent.web.api.dto.FsMkdirRequest;
import com.example.agent.web.security.HomePathException;
import com.example.agent.web.security.HomePathGuard;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件系统浏览 API（add-workspace-picker-modal）。
 *
 * <p>端点：
 *
 * <ul>
 *   <li>{@code GET /api/fs/home} 当前用户家目录
 *   <li>{@code GET /api/fs/list?path=...&includeHidden=false} 列目录
 *   <li>{@code POST /api/fs/mkdir} body {@code {"path":"..."}} 新建空目录
 *   <li>{@code GET /api/fs/drives} 盘符列表（仅 Windows 返回有内容；Linux/macOS 返回空数组）
 * </ul>
 *
 * <p>所有端点继承 {@code TrustedHostFilter} 的 IP 白名单；路径解析统一经 {@link HomePathGuard}，
 * 拒绝越界访问。所有 {@link HomePathException} 在本 controller 内被映射为对应 HTTP 状态码。
 */
@RestController
@RequestMapping("/api/fs")
@Profile("web")
public class FsController {

    private final HomePathGuard guard;

    public FsController() {
        this(HomePathGuard.systemDefault());
    }

    /** 可注入构造（测试用）。 */
    public FsController(HomePathGuard guard) {
        this.guard = guard;
    }

    @GetMapping("/home")
    public ResponseEntity<FsHomeResponse> home() {
        return ResponseEntity.ok(
                new FsHomeResponse(guard.homeRealPath().toString(), detectPlatform()));
    }

    @GetMapping("/list")
    public ResponseEntity<?> list(
            @RequestParam("path") String path,
            @RequestParam(value = "includeHidden", defaultValue = "false") boolean includeHidden) {
        try {
            HomePathGuard.ResolvedPath r = guard.resolveWithinHome(path, true);
            Path real = r.realPath();
            if (!Files.isDirectory(real)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "not_a_directory"));
            }
            List<FsEntry> entries = new ArrayList<>();
            try (Stream<Path> stream = Files.list(real)) {
                stream.filter(p -> includeHidden || !isHidden(p))
                        .sorted(
                                Comparator.comparing((Path p) -> !Files.isDirectory(p))
                                        .thenComparing(p -> p.getFileName().toString()))
                        .forEach(p -> entries.add(toEntry(p)));
            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "list_failed", "message", e.getMessage()));
            }
            Path parent = real.getParent();
            String parentStr = parent == null ? null : parent.toString();
            return ResponseEntity.ok(new FsListResponse(real.toString(), parentStr, entries));
        } catch (HomePathException e) {
            return errorFor(e);
        }
    }

    @PostMapping("/mkdir")
    public ResponseEntity<?> mkdir(@RequestBody FsMkdirRequest req) {
        if (req == null || req.path() == null || req.path().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "name_invalid"));
        }
        try {
            HomePathGuard.ResolvedPath r = guard.resolveWithinHome(req.path(), false);
            String leaf = r.requested().getFileName().toString();
            // 复用 WorkspaceStore 的 name 合法性规则（[A-Za-z0-9._-]，≤64）。
            if (!leaf.matches("[A-Za-z0-9._-]+") || leaf.length() > 64) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "name_invalid"));
            }
            try {
                Files.createDirectories(r.requested());
            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "mkdir_failed", "message", e.getMessage()));
            }
            return ResponseEntity.ok(Map.of("path", r.requested().toString()));
        } catch (HomePathException e) {
            return errorFor(e);
        }
    }

    @GetMapping("/drives")
    public ResponseEntity<FsDrivesResponse> drives() {
        if (!isWindows()) {
            return ResponseEntity.ok(new FsDrivesResponse(List.of()));
        }
        List<FsDrivesResponse.FsDrive> out = new ArrayList<>();
        for (FileStore store : java.nio.file.FileSystems.getDefault().getFileStores()) {
            String name = nameOf(store);
            String path = pathOf(store);
            if (name != null && path != null) {
                out.add(new FsDrivesResponse.FsDrive(name, path));
            }
        }
        out.sort(Comparator.comparing(FsDrivesResponse.FsDrive::name));
        return ResponseEntity.ok(new FsDrivesResponse(out));
    }

    private static FsEntry toEntry(Path p) {
        long size = 0L;
        long mtime = 0L;
        try {
            if (Files.isRegularFile(p)) size = Files.size(p);
            mtime = Files.getLastModifiedTime(p).toMillis();
        } catch (IOException ignored) {
            // 权限拒绝时返回 0，不阻断列表
        }
        return new FsEntry(
                p.getFileName().toString(),
                p.toString(),
                Files.isDirectory(p),
                size,
                mtime);
    }

    private static boolean isHidden(Path p) {
        String name = p.getFileName().toString();
        if (name.startsWith(".")) return true;
        try {
            return Files.isHidden(p);
        } catch (IOException e) {
            return false;
        }
    }

    private static String detectPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("mac") || os.contains("darwin")) return "mac";
        return "linux";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String nameOf(FileStore store) {
        try {
            return store.name();
        } catch (Exception e) {
            return null;
        }
    }

    private static String pathOf(FileStore store) {
        try {
            // toString() 通常包含挂载点（如 "C:\"）；不同 JDK 实现有差异，v0.x 接受原始字符串
            return store.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static ResponseEntity<?> errorFor(HomePathException e) {
        HttpStatus status =
                switch (e.code()) {
                    case "path_not_absolute", "name_invalid" -> HttpStatus.BAD_REQUEST;
                    case "path_outside_home" -> HttpStatus.FORBIDDEN;
                    case "path_not_found" -> HttpStatus.NOT_FOUND;
                    case "dir_exists" -> HttpStatus.CONFLICT;
                    default -> HttpStatus.INTERNAL_SERVER_ERROR;
                };
        return ResponseEntity.status(status).body(Map.of("error", e.code()));
    }
}

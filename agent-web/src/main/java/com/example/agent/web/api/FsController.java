package com.example.agent.web.api;

import com.example.agent.web.api.dto.FsDrivesResponse;
import com.example.agent.web.api.dto.FsEntry;
import com.example.agent.web.api.dto.FsHomeResponse;
import com.example.agent.web.api.dto.FsListResponse;
import com.example.agent.web.api.dto.FsMkdirRequest;
import com.example.agent.web.api.dto.FsQuickAccessResponse;
import com.example.agent.web.security.HomePathException;
import com.example.agent.web.security.HomePathGuard;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
 *   <li>{@code GET /api/fs/raw?path=...} 读取图片字节流（add-rich-markdown-rendering）
 * </ul>
 *
 * <p>所有端点继承 {@code TrustedHostFilter} 的 IP 白名单；路径解析统一经 {@link HomePathGuard}，
 * 拒绝越界访问。所有 {@link HomePathException} 在本 controller 内被映射为对应 HTTP 状态码。
 */
@RestController
@RequestMapping("/api/fs")
@Profile("web")
public class FsController {

    /** 单文件大小上限：16 MiB，超过则拒绝，且不把文件读入内存。 */
    private static final long MAX_RAW_BYTES = 16L * 1024 * 1024;

    /**
     * 图片类型白名单（add-rich-markdown-rendering）：扩展名（小写、不含点）→ MIME。
     *
     * <p>白名单是**安全边界**而非便利措施：{@code /api/fs/raw} 在**应用同源**下返回字节，若放开任意
     * 扩展名，家目录内一个攻击者可控的 {@code .html} 就会被以 {@code text/html} 打开，构成同源存储型
     * XSS——比放开 raw HTML 那条链更直接，且不需要模型配合。
     */
    private static final Map<String, String> RAW_MIME_BY_EXTENSION =
            Map.ofEntries(
                    Map.entry("png", "image/png"),
                    Map.entry("jpg", "image/jpeg"),
                    Map.entry("jpeg", "image/jpeg"),
                    Map.entry("gif", "image/gif"),
                    Map.entry("webp", "image/webp"),
                    Map.entry("avif", "image/avif"),
                    Map.entry("bmp", "image/bmp"),
                    Map.entry("ico", "image/x-icon"),
                    Map.entry("svg", "image/svg+xml"));

    /** SVG 作为顶层文档被直接访问时其中的 {@code <script>} 会在同源执行；sandbox 使其失效。 */
    private static final String SVG_SANDBOX_CSP = "sandbox";

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

    /**
     * 快速访问目录列表（polish-workspace-picker-dsh-style）：
     *
     * <ul>
     *   <li>始终包含 Home（= homeDir）；
     *   <li>探测 Home + Desktop / Documents / Downloads，仅返回已存在且落在 homeDir 子树内的目录；
     *   <li>探测失败的目录（不存在 / 越界 / IO 异常）静默跳过。
     * </ul>
     */
    @GetMapping("/quick-access")
    public ResponseEntity<FsQuickAccessResponse> quickAccess() {
        List<FsQuickAccessResponse.FsQuickAccessItem> items = new ArrayList<>();
        // Home 始终返回
        items.add(
                new FsQuickAccessResponse.FsQuickAccessItem(
                        "Home", guard.homeRealPath().toString()));
        // 探测常见快速访问目录；不存在或越界时跳过
        for (String sub : new String[] {"Desktop", "Documents", "Downloads"}) {
            Path candidate = Paths.get(guard.homeRealPath().toString(), sub);
            try {
                if (!Files.isDirectory(candidate)) continue;
                Path real = candidate.toRealPath();
                if (!real.startsWith(guard.homeRealPath())) continue;
                items.add(new FsQuickAccessResponse.FsQuickAccessItem(sub, real.toString()));
            } catch (IOException e) {
                // 静默跳过无法解析的目录
            }
        }
        return ResponseEntity.ok(new FsQuickAccessResponse(items));
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

    /**
     * 读取本地图片字节流（add-rich-markdown-rendering）：供对话区渲染消息里的本地图片。
     *
     * <p>安全约束（缺一不可，顺序即校验顺序）：
     *
     * <ol>
     *   <li>trusted-host 由 {@code TrustedHostFilter} 在过滤器层先行把关；
     *   <li>路径经 {@link HomePathGuard} 解析，{@code toRealPath()} 后必须落在 homeDir 子树内；
     *   <li>目标必须是普通文件（目录返回 400）；
     *   <li>扩展名必须命中图片白名单（否则 415，绝不按 {@code text/html} 吐出去）；
     *   <li>大小超过 {@link #MAX_RAW_BYTES} 直接拒绝，不读盘。
     * </ol>
     */
    @GetMapping("/raw")
    public ResponseEntity<?> raw(@RequestParam("path") String path) {
        HomePathGuard.ResolvedPath resolved;
        try {
            resolved = guard.resolveWithinHome(path, true);
        } catch (HomePathException e) {
            return errorFor(e);
        }
        Path real = resolved.realPath();
        if (!Files.isRegularFile(real)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "not_a_file"));
        }
        String mime = mimeForRaw(real);
        if (mime == null) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(Map.of("error", "unsupported_media_type"));
        }
        byte[] bytes;
        try {
            long size = Files.size(real);
            if (size > MAX_RAW_BYTES) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                        .body(Map.of("error", "file_too_large"));
            }
            bytes = Files.readAllBytes(real);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "read_failed", "message", e.getMessage()));
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mime));
        headers.setContentLength(bytes.length);
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Disposition", contentDispositionFor(real));
        if ("image/svg+xml".equals(mime)) {
            headers.set("Content-Security-Policy", SVG_SANDBOX_CSP);
        }
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
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

    /** 按扩展名查白名单；无扩展名或未命中返回 {@code null}（调用方据此返回 415）。 */
    private static String mimeForRaw(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return RAW_MIME_BY_EXTENSION.get(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /**
     * 构造 {@code Content-Disposition}：同时给 ASCII 回退名与 RFC 5987 的 UTF-8 名，避免中文文件名
     * 在 header 里被按 ISO-8859-1 写出后变成乱码。
     */
    private static String contentDispositionFor(Path file) {
        String name = file.getFileName().toString();
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return "inline; filename=\"" + asciiFallback(name) + "\"; filename*=UTF-8''" + encoded;
    }

    /** 把非 ASCII 与 header 不安全字符替换为下划线，仅用于 ASCII 回退名。 */
    private static String asciiFallback(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean unsafe = c < 0x20 || c > 0x7e || c == '"' || c == '\\' || c == ';';
            sb.append(unsafe ? '_' : c);
        }
        return sb.toString();
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

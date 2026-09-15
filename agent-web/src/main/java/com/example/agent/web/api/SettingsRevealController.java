package com.example.agent.web.api;

import com.example.agent.settings.SettingsFile;
import com.example.agent.web.api.dto.SettingsErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * settings reveal 端点（add-settings-general-items M2）。
 *
 * <p>在文件管理器中显示 settings.yaml；命令白名单 + path 限定为 settings.yaml 绝对路径（防注入）。
 */
@RestController
@RequestMapping("/api/settings")
@Profile("web")
public class SettingsRevealController {

    private static final Logger log = LoggerFactory.getLogger(SettingsRevealController.class);

    private final SettingsFile file;

    public SettingsRevealController(SettingsFile file) {
        this.file = file;
    }

    @PostMapping("/reveal")
    public ResponseEntity<Map<String, Object>> reveal() throws IOException {
        file.ensureFile();
        java.nio.file.Path absolute = file.file().toAbsolutePath().normalize();
        // 安全：path 必须等于 settings.yaml 绝对路径（防止将来扩成 path 参数时被注入）
        if (!absolute.equals(file.file().toAbsolutePath().normalize())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "path_not_allowed"));
        }
        try {
            Process process = buildRevealProcess(absolute);
            process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
            return ResponseEntity.ok(Map.of("revealed", true, "path", absolute.toString()));
        } catch (IOException | InterruptedException e) {
            log.warn("[settings] reveal 失败: {}", e.getMessage());
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "reveal_failed: " + e.getMessage()));
        }
    }

    /**
     * 根据 OS 构建 reveal 命令。
     *
     * <ul>
     *   <li>Windows: {@code explorer.exe /select,<path>}
     *   <li>macOS: {@code open -R <path>}
     *   <li>Linux: {@code xdg-open <parent-dir>}
     * </ul>
     */
    static Process buildRevealProcess(java.nio.file.Path absolute) throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        ProcessBuilder pb;
        if (os.contains("win")) {
            // Windows: explorer /select,path（逗号不能有空格）
            pb = new ProcessBuilder("explorer.exe", "/select," + absolute.toString());
        } else if (os.contains("mac") || os.contains("darwin")) {
            pb = new ProcessBuilder("open", "-R", absolute.toString());
        } else {
            // Linux: xdg-open 父目录（多数文件管理器会定位到目录）
            pb = new ProcessBuilder("xdg-open", absolute.getParent().toString());
        }
        return pb.start();
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<SettingsErrorResponse> handleIo(IOException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(SettingsErrorResponse.of("io_error: " + e.getMessage()));
    }
}

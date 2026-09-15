package com.example.agent.web.api;

import com.example.agent.settings.SettingsFile;
import com.example.agent.settings.SettingsService;
import com.example.agent.settings.SettingsView;
import com.example.agent.settings.exception.SettingsConflictException;
import com.example.agent.settings.exception.SettingsNotFoundException;
import com.example.agent.settings.exception.SettingsValidationException;
import com.example.agent.web.api.dto.SettingsErrorResponse;
import com.example.agent.web.api.dto.SettingsPatchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * settings REST 端点（add-settings-foundation M1）。
 *
 * <ul>
 *   <li>{@code GET /api/settings} 完整读
 *   <li>{@code PATCH /api/settings/{path}} 单字段更新（dot notation，path 从 HttpServletRequest 解析）
 *   <li>{@code GET /api/settings/file-path} 返回 settings.yaml 绝对路径（M2 reveal 用）
 * </ul>
 */
@RestController
@RequestMapping("/api/settings")
@Profile("web")
public class SettingsController {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);
    private static final String PREFIX = "/api/settings/";

    private final SettingsService service;
    private final SettingsFile file;

    public SettingsController(SettingsService service, SettingsFile file) {
        this.service = service;
        this.file = file;
    }

    @GetMapping
    public ResponseEntity<SettingsView> get() throws IOException {
        return ResponseEntity.ok(service.read());
    }

    @PatchMapping("/**")
    public ResponseEntity<SettingsView> patch(ServerHttpRequest req, @RequestBody SettingsPatchRequest body) throws IOException {
        String path = extractPath(req);
        SettingsView view = service.patch(path, body.value(), body.revision());
        return ResponseEntity.ok(view);
    }

    @GetMapping("/file-path")
    public ResponseEntity<Map<String, String>> filePath() throws IOException {
        file.ensureFile();
        return ResponseEntity.ok(Map.of("path", file.file().toAbsolutePath().toString()));
    }

    private static String extractPath(ServerHttpRequest req) {
        String path = req.getPath().value();
        if (path.startsWith(PREFIX)) {
            return path.substring(PREFIX.length());
        }
        return path;
    }

    // ---------- 异常映射 ----------

    @ExceptionHandler(SettingsNotFoundException.class)
    public ResponseEntity<SettingsErrorResponse> handleNotFound(SettingsNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(SettingsErrorResponse.of(e.getMessage()));
    }

    @ExceptionHandler(SettingsValidationException.class)
    public ResponseEntity<SettingsErrorResponse> handleValidation(SettingsValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(SettingsErrorResponse.of(e.getMessage()));
    }

    @ExceptionHandler(SettingsConflictException.class)
    public ResponseEntity<SettingsErrorResponse> handleConflict(SettingsConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(SettingsErrorResponse.of(e.getMessage()));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<SettingsErrorResponse> handleIo(IOException e) {
        log.error("[settings] IO 异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(SettingsErrorResponse.of("io_error: " + e.getMessage()));
    }
}

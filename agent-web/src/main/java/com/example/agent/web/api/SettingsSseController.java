package com.example.agent.web.api;

import com.example.agent.settings.SettingsChangeBroadcaster;
import com.example.agent.settings.SettingsFile;
import com.example.agent.settings.SettingsFileWatcher;
import com.example.agent.settings.SettingsService;
import com.example.agent.settings.SettingsView;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.IOException;

/**
 * settings SSE 端点（add-settings-foundation M1）。
 *
 * <p>独立成类便于测试；监听文件变更 + 服务端写入变更，统一通过 SSE 广播。
 * 使用 reactor Sinks + Flux，与 ChatStreamController 模式一致（spring-webflux）。
 */
@RestController
@RequestMapping("/api/settings")
@Profile("web")
public class SettingsSseController {

    private static final Logger log = LoggerFactory.getLogger(SettingsSseController.class);

    private final SettingsService service;
    private final SettingsChangeBroadcaster broadcaster;
    private final SettingsFile file;
    private final Sinks.Many<SettingsView> sink = Sinks.many().multicast().onBackpressureBuffer();
    private SettingsFileWatcher watcher;

    public SettingsSseController(SettingsService service,
                                 SettingsChangeBroadcaster broadcaster,
                                 SettingsFile file) {
        this.service = service;
        this.broadcaster = broadcaster;
        this.file = file;
    }

    @PostConstruct
    public void start() {
        try {
            file.ensureFile();
            watcher = new SettingsFileWatcher(file, this::onExternalChange);
            watcher.start();
            broadcaster.register(this::onBroadcast);
            log.info("[settings] SSE 控制器启动");
        } catch (IOException e) {
            log.error("[settings] SSE 控制器启动失败", e);
        }
    }

    @PreDestroy
    public void stop() {
        if (watcher != null) watcher.close();
        sink.tryEmitComplete();
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<Object>>> stream() {
        // 立即推送一次当前 snapshot 作为初始事件
        try {
            SettingsView initial = service.read();
            sink.tryEmitNext(initial);
        } catch (IOException e) {
            log.warn("[settings] SSE 初始化读取失败: {}", e.getMessage());
        }
        Flux<ServerSentEvent<Object>> body = sink.asFlux()
                .map(view -> (Object) view)
                .map(obj -> ServerSentEvent.builder(obj)
                        .event("settings.changed")
                        .build());
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(body);
    }

    private void onBroadcast(SettingsView view) {
        sink.tryEmitNext(view);
    }

    private void onExternalChange(java.nio.file.Path path) {
        // 文件被外部修改 → 让 service 重新读取并广播
        try {
            SettingsView view = service.read();
            broadcaster.broadcast(view);
        } catch (IOException e) {
            log.warn("[settings] 外部变更读取失败: {}", e.getMessage());
        }
    }
}

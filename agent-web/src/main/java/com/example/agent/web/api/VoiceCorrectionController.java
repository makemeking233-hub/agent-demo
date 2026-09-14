package com.example.agent.web.api;

import com.example.agent.web.api.dto.VoiceCorrectionRequest;
import com.example.agent.web.api.dto.VoiceCorrectionResponse;
import com.example.agent.web.api.voice.VoiceCorrectionService;
import com.example.agent.web.api.voice.VoiceRateLimitException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * 语音纠错端点（improve-voice-accuracy T7）。
 *
 * <p>{@code POST /api/chat/voice-correction}：接受 Vosk 原始识别文本 + sessionId + 最近对话上下文，
 * 返回 DeepSeek 语义纠错后的文本。前端用 fire-and-forget 模式调用，失败时降级用 rawText 提交。
 *
 * <p>响应码：
 * <ul>
 *   <li>200 — 纠错成功（含降级，{@code corrected=null}）
 *   <li>400 — rawText / sessionId 缺失
 *   <li>429 — sessionId 限流（每会话每秒 5 次）
 *   <li>500 — 服务内部错误（罕见；service 已内部降级捕获）
 * </ul>
 */
@RestController
@RequestMapping("/api/chat")
@Profile("web")
public class VoiceCorrectionController {

    private final VoiceCorrectionService service;

    public VoiceCorrectionController(VoiceCorrectionService service) {
        this.service = service;
    }

    @PostMapping("/voice-correction")
    public Mono<ResponseEntity<?>> correct(@RequestBody VoiceCorrectionRequest req) {
        if (req == null || req.rawText() == null || req.rawText().isBlank()) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "raw_text_empty")));
        }
        if (req.sessionId() == null || req.sessionId().isBlank()) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "session_id_empty")));
        }

        // service 内部会阻塞（1500ms 超时调 DeepSeek）；包到 boundedElastic 防止阻塞 event loop
        Mono<ResponseEntity<?>> ok = Mono.fromCallable(() -> service.correct(req))
                .subscribeOn(Schedulers.boundedElastic())
                .map(resp -> ResponseEntity.ok(resp));
        return ok
                .onErrorResume(VoiceRateLimitException.class, e -> Mono.just(
                        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                                .body(Map.of("error", "rate_limited",
                                        "message", e.getMessage()))))
                .onErrorResume(IllegalArgumentException.class, e -> Mono.just(
                        ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(Map.of("error", "invalid_argument",
                                        "message", e.getMessage()))));
    }
}
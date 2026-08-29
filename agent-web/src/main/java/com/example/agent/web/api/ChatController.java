package com.example.agent.web.api;

import com.example.agent.web.api.dto.AbortResponse;
import com.example.agent.web.api.dto.SendRequest;
import com.example.agent.web.api.dto.SendResponse;
import com.example.agent.web.stream.ChatStreamService;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@Profile("web")
public class ChatController {
    private final ChatStreamService streams;
    private final Environment env;

    public ChatController(ChatStreamService streams, Environment env) {
        this.streams = streams;
        this.env = env;
    }

    @PostMapping("/send")
    public Mono<ResponseEntity<?>> send(@RequestBody SendRequest req) {
        if (req.content() == null || req.content().isBlank()) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "content_empty")));
        }
        String key = env.getProperty("DEEPSEEK_API_KEY");
        if (key == null || key.isBlank()) {
            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "provider_not_configured", "hint", "set DEEPSEEK_API_KEY")));
        }
        String sessionId = req.sessionId() != null ? req.sessionId() : UUID.randomUUID().toString();
        ChatStreamService.ActiveStream meta = streams.create(sessionId, "deepseek-chat");
        streams.stop(meta.streamId(), "stop");
        return Mono.just(ResponseEntity.ok(new SendResponse(meta.streamId(), sessionId, "deepseek-chat")));
    }

    @PostMapping("/abort/{streamId}")
    public Mono<ResponseEntity<AbortResponse>> abort(@PathVariable String streamId) {
        if (streams.get(streamId) == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(AbortResponse.ofAlreadyStopped()));
        }
        streams.abort(streamId);
        return Mono.just(ResponseEntity.ok(AbortResponse.ofAborted()));
    }
}

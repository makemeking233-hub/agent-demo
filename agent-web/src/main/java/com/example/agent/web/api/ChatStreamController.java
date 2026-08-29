package com.example.agent.web.api;

import com.example.agent.web.stream.ChatStreamService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
@Profile("web")
public class ChatStreamController {
    private final ChatStreamService streams;

    public ChatStreamController(ChatStreamService streams) {
        this.streams = streams;
    }

    @GetMapping(value = "/stream/{streamId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<Object>>> stream(@PathVariable String streamId) {
        ChatStreamService.ActiveStream meta = streams.get(streamId);
        if (meta == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(streams.stream(streamId));
    }
}

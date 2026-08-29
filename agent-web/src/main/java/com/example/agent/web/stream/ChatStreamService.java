package com.example.agent.web.stream;

import com.example.agent.web.api.dto.SseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Service
@Profile("web")
public class ChatStreamService {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamService.class);

    private final Map<String, ActiveStream> actives = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final java.util.concurrent.atomic.AtomicLong lastEventId = new java.util.concurrent.atomic.AtomicLong(0);

    public record ActiveStream(String streamId, String sessionId, String model, long startedAt,
                               Sinks.Many<ServerSentEvent<Object>> sink) {}

    public ActiveStream create(String sessionId, String model) {
        String streamId = UUID.randomUUID().toString();
        Sinks.Many<ServerSentEvent<Object>> sink = Sinks.many().unicast().onBackpressureBuffer();
        ActiveStream meta = new ActiveStream(streamId, sessionId, model, System.currentTimeMillis(), sink);
        actives.put(streamId, meta);
        emit(meta, new SseEvent.MessageStart(streamId, sessionId, model, System.currentTimeMillis()));
        return meta;
    }

    public Flux<ServerSentEvent<Object>> stream(String streamId) {
        ActiveStream meta = actives.get(streamId);
        if (meta == null) {
            return Flux.error(new IllegalArgumentException("stream_not_found: " + streamId));
        }
        return meta.sink().asFlux().timeout(Duration.ofMinutes(30));
    }

    public void emit(ActiveStream meta, SseEvent event) {
        if (meta == null || meta.sink() == null) {
            log.debug("emit to unknown/closed stream: {}", event.type());
            return;
        }
        try {
            String json = mapper.writeValueAsString(event);
            long id = lastEventId.incrementAndGet();
            ServerSentEvent<Object> sse = ServerSentEvent.builder((Object) json)
                    .id(String.valueOf(id))
                    .event(event.type())
                    .build();
            Sinks.EmitResult result = meta.sink().tryEmitNext(sse);
            if (result.isFailure()) {
                log.debug("emit dropped for stream {}: {}", meta.streamId(), result);
            }
        } catch (Exception e) {
            log.warn("emit serialization failed: {}", e.toString());
        }
    }

    public void emit(String streamId, SseEvent event) {
        emit(actives.get(streamId), event);
    }

    public void stop(String streamId, String finishReason) {
        ActiveStream meta = actives.remove(streamId);
        if (meta == null) return;
        emit(meta, new SseEvent.MessageStop(finishReason));
        meta.sink().tryEmitComplete();
    }

    public void abort(String streamId) {
        ActiveStream meta = actives.get(streamId);
        if (meta == null) return;
        emit(meta, new SseEvent.Error("aborted", "turn aborted by user"));
        stop(streamId, "aborted");
    }

    public ActiveStream get(String streamId) {
        return actives.get(streamId);
    }

    @PreDestroy
    public void shutdown() {
        actives.forEach((id, meta) -> meta.sink().tryEmitComplete());
        actives.clear();
    }
}

package com.example.agent.web.stream;

import com.example.agent.core.AgentLoop;
import com.example.agent.core.Message;
import com.example.agent.core.TurnResult;
import com.example.agent.log.SessionLogSink;
import com.example.agent.permission.PermissionConfirmer;
import com.example.agent.web.api.dto.SseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    /** 每流后台执行槽（跑 AgentLoop.processTurn，不阻塞 HTTP handler 线程）。 */
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, ActiveStream> actives = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final java.util.concurrent.atomic.AtomicLong lastEventId =
            new java.util.concurrent.atomic.AtomicLong(0);
    private final WebAgentRuntime runtime;
    private final PermissionBridge permissionBridge;

    public ChatStreamService(WebAgentRuntime runtime, PermissionBridge permissionBridge) {
        this.runtime = runtime;
        this.permissionBridge = permissionBridge;
    }

    /**
     * 活动流：一个 SSE sink + 关联的 AgentLoop（单会话）+ 该会话的权限桥。
     *
     * @param streamId SSE 流 id
     * @param sessionId 会话 id
     * @param model 模型名
     * @param startedAt 开始时间戳（ms）
     * @param sink SSE 发布 sink
     * @param loop 该会话的 AgentLoop（可空，调用 start 时装配）
     * @param sinkAdapter SSE 事件观察者（把 AgentLoop 回调转 SSE 事件）
     * @param history 当前消息历史（可空，start 时装配）
     */
    public record ActiveStream(
            String streamId,
            String sessionId,
            String model,
            long startedAt,
            Sinks.Many<ServerSentEvent<Object>> sink,
            AgentLoop loop,
            SseSessionLogSink sinkAdapter) {}

    public ActiveStream create(String sessionId, String model) {
        String streamId = UUID.randomUUID().toString();
        Sinks.Many<ServerSentEvent<Object>> sink = Sinks.many().unicast().onBackpressureBuffer();
        SseSessionLogSink adapter = new SseSessionLogSink(this, streamId);
        // 权限桥包装成 PermissionConfirmer：confirm(prompt) 阻塞等待前端决策。
        PermissionConfirmer confirmer =
                prompt ->
                        "yes"
                                .equals(
                                        permissionBridge.waitForDecision(
                                                permissionBridge.newPermissionId(), null, prompt, prompt, null));
        AgentLoop loop = runtime.createLoop(streamId, adapter, confirmer);
        ActiveStream meta =
                new ActiveStream(
                        streamId, sessionId, model, System.currentTimeMillis(), sink, loop, adapter);
        actives.put(streamId, meta);
        emit(meta, new SseEvent.MessageStart(streamId, sessionId, model, System.currentTimeMillis()));
        return meta;
    }

    /**
     * 启动一个 turn：在后台执行 {@link AgentLoop#processTurn(Message.User)}。SSE 事件由
     * {@link SseSessionLogSink} 下发到本流。turn 结束（自然或异常）后关闭流。
     *
     * @param streamId 已创建流 id
     * @param content 用户消息
     * @return 是否找到该流并已启动
     */
    public boolean start(String streamId, String content) {
        ActiveStream meta = actives.get(streamId);
        if (meta == null) return false;
        AgentLoop loop = meta.loop();
        executor.execute(
                () -> {
                    try {
                        loop.processTurn(new Message.User(content))
                                .block(Duration.ofMinutes(30));
                    } catch (Exception e) {
                        log.warn("turn failed for stream {}: {}", streamId, e.toString());
                        emit(meta, new SseEvent.Error("turn_failed", String.valueOf(e.getMessage())));
                        stop(streamId, "error");
                    }
                });
        return true;
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
            ServerSentEvent<Object> sse =
                    ServerSentEvent.builder((Object) json).id(String.valueOf(id)).event(event.type()).build();
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
        executor.shutdownNow();
    }
}

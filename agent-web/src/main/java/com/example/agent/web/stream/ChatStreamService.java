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
import java.util.List;
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
            SseSessionLogSink sinkAdapter,
            java.util.concurrent.atomic.AtomicBoolean aborted) {}

    public ActiveStream create(String sessionId, String model) {
        String streamId = UUID.randomUUID().toString();
        // replay().all(): 延迟订阅者(客户端 turn 完成后再连)能收到全部事件 + complete,
        // 支撑 spec §resume/Last-Event-ID 与测试中 send→stream 的先后时序。
        Sinks.Many<ServerSentEvent<Object>> sink = Sinks.many().replay().all();
        SseSessionLogSink adapter = new SseSessionLogSink(this, streamId);
        java.util.concurrent.atomic.AtomicBoolean aborted = new java.util.concurrent.atomic.AtomicBoolean(false);
        // 权限桥包装成 PermissionConfirmer：confirm(prompt) emit permission_request 后阻塞等待前端决策。
        PermissionConfirmer confirmer =
                prompt -> {
                    String permissionId = permissionBridge.newPermissionId();
                    // AgentLoop 传的是 "toolName → 描述" 的 prompt; 取 toolName 首段, reason 为全文。
                    String toolName = prompt.split("→", 2)[0].trim();
                    emit(streamId, new SseEvent.PermissionRequest(permissionId, null, toolName, prompt, DECISION_CHOICES));
                    String decision =
                            permissionBridge.waitForDecision(permissionId, null, toolName, prompt, DECISION_CHOICES);
                    emit(streamId, new SseEvent.PermissionResponse(permissionId, decision));
                    return "yes".equals(decision);
                };
        // abort 信号: abort() 置 true, AgentLoop 工具执行会感知并中断。
        AgentLoop loop = runtime.createLoop(streamId, adapter, confirmer, aborted::get);
        ActiveStream meta =
                new ActiveStream(
                        streamId, sessionId, model, System.currentTimeMillis(), sink, loop, adapter, aborted);
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
        ActiveStream meta = actives.get(streamId);
        if (meta == null) return;
        emit(meta, new SseEvent.MessageStop(finishReason));
        meta.sink().tryEmitComplete();
        // 不立即从 map 移除: replay sink 保留全部事件, 延迟订阅 / 重连 (spec §resume)
        // 仍能通过 get(streamId) 拿到已完成的流。由 TTL 清理 + shutdown 回收。
        evictIfExpired(meta, STREAM_RETENTION_MS);
    }

    /** 保留时长上限(ms): 完成后仍允许客户端在此窗口内订阅 / 用 Last-Event-ID 重连。 */
    private static final long STREAM_RETENTION_MS = 10 * 60 * 1000L;

    /** 权限决策选项 (spec §Requirement: permission_request 载荷)。 */
    private static final List<String> DECISION_CHOICES = List.of("yes", "no", "always");

    /**
     * 用户提交权限决策 (spec §Requirement: permission_request 决策)。把 yes/no/always 交给
     * {@link PermissionBridge#submitDecision} 唤醒等待线程。返回 {@code true} 表示找到待决策流并已提交。
     *
     * @param streamId 流 id
     * @param permissionId 待决策的 permission_id
     * @param decision 决策值 (yes/no/always)
     * @return 是否成功提交 (找到 permission_id 且决策合法)
     */
    public boolean submitDecision(String streamId, String permissionId, String decision) {
        if (actives.get(streamId) == null) return false;
        return permissionBridge.submitDecision(permissionId, decision);
    }

    /** 惰性清理: 超过保留时长的已结束流从 map 移除, 防内存泄漏。 */
    private void evictIfExpired(ActiveStream meta, long retentionMs) {
        long ttl = meta.startedAt() + retentionMs;
        // 简单惰性: 仅当流确实完成且超过 TTL 才移除; 由下一次 create/get 触发
        if (System.currentTimeMillis() > ttl) {
            actives.remove(meta.streamId());
        }
    }

    public void abort(String streamId) {
        ActiveStream meta = actives.get(streamId);
        if (meta == null) return;
        // 置中断信号, 让 AgentLoop 工具执行感知并尽快停止
        if (meta.aborted() != null) {
            meta.aborted().set(true);
        }
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

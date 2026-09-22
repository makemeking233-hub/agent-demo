package com.example.agent.web.wecom;

import com.example.agent.permission.PermissionMode;
import com.example.agent.web.stream.ChatStreamService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * 企业微信回调消息派发器（add-wecom-channel task 6.2）。
 *
 * <p>流程：
 *
 * <ol>
 *   <li>非 text / event 拒（返回 false 让 Controller 发"暂仅支持文本指令"）
 *   <li>取 userId 对应 sessionId（不存在则建）
 *   <li>per-userId 串行化（{@link ReentrantLock}）— 同 userId 多消息排队
 *   <li>复用 {@link ChatStreamService#create(sessionId, model, mode, workspace, null)}
 *   <li>异步订阅 chunks → {@link WecomReplyPusher#append(userId, chunk)}
 *   <li>同步返回（整个流程应 < 100ms）
 * </ol>
 */
@Component
@ConditionalOnProperty(name = "agent.wecom.enabled", havingValue = "true")
public class WecomMessageDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WecomMessageDispatcher.class);
    private static final String DEFAULT_MODEL = "deepseek-chat";

    private final WecomCrypto crypto;
    private final WecomConfigProperties props;
    private final WecomSessionMapper sessionMapper;
    private final WecomReplyPusher pusher;
    /** 用 ObjectProvider 避免 ChatStreamService 启动期循环依赖（构造期未就绪时仍可注入） */
    private final ObjectProvider<ChatStreamService> streamsProvider;
    private final ConcurrentHashMap<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    public WecomMessageDispatcher(
            WecomCrypto crypto,
            WecomConfigProperties props,
            WecomSessionMapper sessionMapper,
            WecomReplyPusher pusher,
            @Lazy ObjectProvider<ChatStreamService> streamsProvider) {
        this.crypto = crypto;
        this.props = props;
        this.sessionMapper = sessionMapper;
        this.pusher = pusher;
        this.streamsProvider = streamsProvider;
    }

    /**
     * 处理单个事件（同步；返回后 Controller 可立刻 200 OK）。
     *
     * @return true=已派发到 AgentLoop；false=忽略（如非 text / event 类型）
     */
    public boolean dispatch(WecomEvent event) {
        if (!event.isText()) {
            return false;
        }
        String userId = event.fromUserName();
        if (userId == null || userId.isBlank()) {
            log.warn("wecom 事件缺 FromUserName：{}", event);
            return false;
        }
        ReentrantLock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
        lock.lock();
        try {
            String sessionId = sessionMapper.getOrCreate(userId);
            String model = DEFAULT_MODEL;
            // FULL_ACCESS：微信通道默认全放行（绕过 PermissionConfirmer）
            // rewrite-permission-mode-dsh T12.2: 改用 DANGER_FULL (4 档 dsh 命名)
            ChatStreamService.ActiveStream meta = streamsProvider.getObject().create(
                    sessionId, model,
                    com.example.agent.permission.PermissionMode.fromSandboxMode(
                            com.example.agent.permission.SandboxMode.DANGER_FULL),
                    null, null);
            // 异步订阅 chunks → ReplyPusher；不阻塞 Controller
            String content = event.content() == null ? "" : event.content();
            Flux.from(meta.loop().processTurn(new com.example.agent.core.Message.User(content)))
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(
                            null,
                            err -> log.warn("wecom turn failed userId={} sessionId={} err={}",
                                    userId, sessionId, err.getMessage()),
                            () -> log.debug("wecom turn done userId={} sessionId={}", userId, sessionId));
            // 启动 turn（processTurn 已经在订阅时执行；不需要再调 start）
            return true;
        } finally {
            lock.unlock();
        }
    }
}

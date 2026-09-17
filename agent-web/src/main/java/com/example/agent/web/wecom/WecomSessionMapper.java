package com.example.agent.web.wecom;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 企业微信 userId → SessionStore sessionId 映射（add-wecom-channel task 4.1）。
 *
 * <p>每个微信 userId 一对一映射到形如 {@code wecom:<userId>} 的 sessionId；
 * 复用现有 {@code SessionStore} 的 JSONL 持久化（<sessionId>.jsonl）。
 *
 * <p>持久化到 {@code <agentDataDir>/wecom/sessions.json}；启动时 reload。
 *
 * <p>线程安全：用 {@link ConcurrentHashMap} 缓存 + 每 userId
 * {@code computeIfAbsent} 保证同 userId 并发也只产生一个 sessionId。
 */
public class WecomSessionMapper {

    private static final Logger log = LoggerFactory.getLogger(WecomSessionMapper.class);

    private static final String SESSION_FILE = "sessions.json";

    private final Path sessionFile;
    private final Map<String, String> userToSession = new ConcurrentHashMap<>();
    private final ObjectMapper json = new ObjectMapper();

    public WecomSessionMapper(Path wecomDir) {
        try {
            Files.createDirectories(wecomDir);
            this.sessionFile = wecomDir.resolve(SESSION_FILE);
            if (Files.exists(sessionFile)) {
                Map<String, String> loaded = json.readValue(
                        sessionFile.toFile(),
                        new TypeReference<LinkedHashMap<String, String>>() {});
                if (loaded != null) {
                    userToSession.putAll(loaded);
                }
                log.info("WecomSessionMapper 加载 {} 条 userId 映射", userToSession.size());
            }
        } catch (IOException e) {
            throw new RuntimeException("wecom sessions.json 加载失败", e);
        }
    }

    /**
     * 查 userId 对应 sessionId（不创建）。
     */
    public Optional<String> findSessionId(String userId) {
        return Optional.ofNullable(userToSession.get(userId));
    }

    /**
     * 拿或建 userId 对应 sessionId（首次时新建并持久化）。
     *
     * @return sessionId（形如 {@code wecom:<userId>}）
     */
    public String getOrCreate(String userId) {
        // computeIfAbsent 在 ConcurrentHashMap 上是原子的
        return userToSession.computeIfAbsent(userId, this::createAndPersist);
    }

    private String createAndPersist(String userId) {
        // 注：ConcurrentHashMap.computeIfAbsent 已自动 put 返回值；这里不能再 put（会抛 Recursive update）
        // 落盘要包含新值；这里把"先 put 再 persist"分两步，但 computeIfAbsent 持有的锁允许 put。
        // 用一个临时 map 包含 sessionId 给 Jackson 写。
        String sessionId = "wecom:" + userId;
        // 新值即将被 computeIfAbsent 自动写入；这里手动写一次包含新值的快照
        Map<String, String> snapshot = new LinkedHashMap<>(userToSession);
        snapshot.put(userId, sessionId);
        persistSnapshot(snapshot);
        log.info("WecomSessionMapper 新建映射：userId={} → sessionId={}", userId, sessionId);
        return sessionId;
    }

    /** 落盘到 sessions.json（写失败仅日志 WARN，不抛错 — 内存已生效）。 */
    private void persistSnapshot(Map<String, String> snapshot) {
        try {
            json.writerWithDefaultPrettyPrinter()
                    .writeValue(sessionFile.toFile(), snapshot);
        } catch (IOException e) {
            log.warn("wecom sessions.json 写入失败（不影响本次请求）：{}", e.getMessage());
        }
    }
}

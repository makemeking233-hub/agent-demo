package com.example.agent.log;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 会话 ID 生成（详见 logging-design.md §2.2）。
 *
 * <p>格式：{@code YYYY-MM-DDTHH-mm-ss-uuid8}。`{}` 用时间戳前缀保证按创建时间排序，`uuid8` 用全局唯一 ID 的
 * 前 8 位做物理区分。`sessions/` 存档与 `logs/sessions/` 目录共用，保证同一会话两处命名一致。
 */
public final class SessionId {
    private SessionId() {}

    /** 格式器（用小写字母枚举，分隔符用 `-`，可与文件名直接兼容） */
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss");

    /**
     * 生成新的会话 ID。
     *
     * @return 形如 {@code 2026-08-26T10-23-45-1a2b3c4d} 的唯一会话 ID
     */
    public static String newSessionId() {
        return LocalDateTime.now().format(FMT) + "-" + shortUuid();
    }

    /**
     * 取 UUID 前 8 位（去 {@code -}），用作短标识。
     *
     * @return 8 位十六进制短 ID
     */
    static String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}

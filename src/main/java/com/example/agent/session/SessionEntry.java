package com.example.agent.session;

import java.util.Map;
import java.util.UUID;

/**
 * JSONL 单条 entry（详见 design.md §10）。
 *
 * <p>type ∈ user / assistant / tool_result / system / meta
 */
public record SessionEntry(
    String type,
    String uuid,
    String parentUuid,
    String content,
    Map<String, Object> extras,
    long timestamp
) {
    public static SessionEntry user(String content, UUID parent) {
        return new SessionEntry("user", UUID.randomUUID().toString(),
            parent == null ? null : parent.toString(),
            content, null, System.currentTimeMillis());
    }

    public static SessionEntry assistant(String content, java.util.List<?> toolCalls, UUID parent) {
        return new SessionEntry("assistant", UUID.randomUUID().toString(),
            parent == null ? null : parent.toString(),
            content, java.util.Map.of("toolCalls", toolCalls), System.currentTimeMillis());
    }

    public static SessionEntry toolResult(String toolCallId, String content, boolean isError, UUID parent) {
        return new SessionEntry("tool_result", UUID.randomUUID().toString(),
            parent == null ? null : parent.toString(),
            content, java.util.Map.of("toolCallId", toolCallId, "isError", isError),
            System.currentTimeMillis());
    }

    public static SessionEntry system(String content, UUID parent) {
        return new SessionEntry("system", UUID.randomUUID().toString(),
            parent == null ? null : parent.toString(),
            content, null, System.currentTimeMillis());
    }

    public static SessionEntry meta(String key, Object value) {
        return new SessionEntry("meta", UUID.randomUUID().toString(), null,
            null, java.util.Map.of("key", key, "value", value), System.currentTimeMillis());
    }
}
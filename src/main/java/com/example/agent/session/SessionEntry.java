package com.example.agent.session;

import java.util.Map;
import java.util.UUID;

/**
 * JSONL 单条 entry（详见 design.md §10）。
 *
 * <p>type ∈ user / assistant / tool_result / system / meta
 *
 * @param type 条目类型
 * @param uuid 全局唯一 ID
 * @param parentUuid 父条目 UUID（用于构建对话树）
 * @param content 主内容（不同 type 含义不同：user 输入 / assistant 回复 / tool_result 工具输出等）
 * @param extras 扩展字段（按 type 不同）
 * @param timestamp 毫秒时间戳
 */
public record SessionEntry(
    String type,
    String uuid,
    String parentUuid,
    String content,
    Map<String, Object> extras,
    long timestamp
) {
    /**
     * 构造 user 输入条目。
     * @param content 用户消息内容
     * @param parent 父条目 UUID（可空）
     * @return 新 user 条目
     */
    public static SessionEntry user(String content, UUID parent) {
        return new SessionEntry("user", UUID.randomUUID().toString(),
            parent == null ? null : parent.toString(),
            content, null, System.currentTimeMillis());
    }

    /**
     * 构造 assistant 回复条目（含可选 tool_calls）。
     * @param content 模型回复内容
     * @param toolCalls 工具调用列表（可空）
     * @param parent 父条目 UUID（可空）
     * @return 新 assistant 条目
     */
    public static SessionEntry assistant(String content, java.util.List<?> toolCalls, UUID parent) {
        return new SessionEntry("assistant", UUID.randomUUID().toString(),
            parent == null ? null : parent.toString(),
            content, java.util.Map.of("toolCalls", toolCalls), System.currentTimeMillis());
    }

    /**
     * 构造 tool_result 条目（回流给模型）。
     * @param toolCallId 关联的工具调用 ID
     * @param content 工具输出内容
     * @param isError 是否为错误结果
     * @param parent 父条目 UUID（可空）
     * @return 新 tool_result 条目
     */
    public static SessionEntry toolResult(String toolCallId, String content, boolean isError, UUID parent) {
        return new SessionEntry("tool_result", UUID.randomUUID().toString(),
            parent == null ? null : parent.toString(),
            content, java.util.Map.of("toolCallId", toolCallId, "isError", isError),
            System.currentTimeMillis());
    }

    /**
     * 构造 system 条目。
     * @param content system prompt 内容
     * @param parent 父条目 UUID（可空）
     * @return 新 system 条目
     */
    public static SessionEntry system(String content, UUID parent) {
        return new SessionEntry("system", UUID.randomUUID().toString(),
            parent == null ? null : parent.toString(),
            content, null, System.currentTimeMillis());
    }

    /**
     * 构造 meta 条目（用于 token 累计、模型信息等元数据）。
     * @param key 元数据键
     * @param value 元数据值
     * @return 新 meta 条目
     */
    public static SessionEntry meta(String key, Object value) {
        return new SessionEntry("meta", UUID.randomUUID().toString(), null,
            null, java.util.Map.of("key", key, "value", value), System.currentTimeMillis());
    }
}
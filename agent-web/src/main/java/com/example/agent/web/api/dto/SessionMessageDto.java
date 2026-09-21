package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * 会话单条消息（v0.3 会话重进恢复用）。
 *
 * @param role 角色（user / assistant / tool / system）
 * @param content 消息内容
 * @param toolCalls assistant 携带的工具调用骨架（非 assistant 恒为空）
 * @param toolCallId tool_result 关联的调用 id（非 tool 恒为 null）
 * @param isError tool_result 是否为错误结果
 * @param uuid 该条消息在会话存档里的条目 uuid（add-message-actions P2；仅 assistant 有，其余为 null）
 * @param meta 该条消息的读数（add-message-actions P2：{@code duration_ms} / {@code ttft_ms} /
 *     {@code tok_per_sec} / {@code timestamp}），供前端渲染 DSH 风格 clock；无读数时为 null
 */
public record SessionMessageDto(
        @JsonProperty("role") String role,
        @JsonProperty("content") String content,
        @JsonProperty("toolCalls") List<ToolCallDto> toolCalls,
        @JsonProperty("toolCallId") String toolCallId,
        @JsonProperty("isError") boolean isError,
        @JsonProperty("uuid") String uuid,
        @JsonProperty("meta") Map<String, Object> meta) {}

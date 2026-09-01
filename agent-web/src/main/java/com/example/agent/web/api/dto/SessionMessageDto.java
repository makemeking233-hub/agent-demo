package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 会话单条消息（v0.3 会话重进恢复用）。 */
public record SessionMessageDto(
        @JsonProperty("role") String role,
        @JsonProperty("content") String content,
        @JsonProperty("toolCalls") List<ToolCallDto> toolCalls,
        @JsonProperty("toolCallId") String toolCallId,
        @JsonProperty("isError") boolean isError) {}

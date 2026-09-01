package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 会话消息里的工具调用描述（v0.3 会话重进恢复用）。 */
public record ToolCallDto(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("argumentsJson") String argumentsJson) {}

package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POST /api/chat/send body (spec §Requirement: Chat Send → `{"content","session_id"}`)。
 *
 * <p>{@code permission_mode} 为该会话初始权限基准（add-permission-mode-dropdown；可选，缺省
 * {@code read_only}）；{@code workspace} 为会话归属工作区（add-workspaces-and-rename；可选，缺省
 * 默认工作区 {@code agent-demo}）。
 */
public record SendRequest(
        @JsonProperty("content") String content,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("permission_mode") String permissionMode,
        @JsonProperty("workspace") String workspace,
        /**
         * 可选模型名。null/缺省/空白 → 用 {@code agent.chat.default-model}；非空但不在
         * {@code agent.chat.providers} 目录中 → 400 {@code invalid_model}（不静默兜底）。
         * 合法取值见 {@code GET /api/chat/models}。
         */
        @JsonProperty("model") String model,
        /** add-models-dropdown-v0: 可选思考强度（{@code low} / {@code medium} / {@code high}）；null/缺省由 Provider 内部 fallback（如 OAI 默认 medium、Anthropic 默认 4096 token） */
        @JsonProperty("reasoning_effort") String reasoningEffort) {}

package com.example.agent.web.api.dto;

import com.example.agent.stats.SessionStats;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public sealed interface SseEvent {
    @JsonProperty("type")
    String type();

    record MessageStart(@JsonProperty("type") String type, @JsonProperty("stream_id") String streamId,
                        @JsonProperty("session_id") String sessionId, @JsonProperty("model") String model,
                        @JsonProperty("timestamp") long timestamp) implements SseEvent {
        public MessageStart(String streamId, String sessionId, String model, long timestamp) {
            this("message_start", streamId, sessionId, model, timestamp);
        }
    }

    record MessageDelta(@JsonProperty("type") String type, @JsonProperty("delta_type") String deltaType,
                        @JsonProperty("content") String content) implements SseEvent {
        public MessageDelta(String deltaType, String content) {
            this("message_delta", deltaType, content);
        }
    }

    record ToolCallStart(@JsonProperty("type") String type, @JsonProperty("tool_call_id") String toolCallId,
                         @JsonProperty("name") String name, @JsonProperty("args") Object args) implements SseEvent {
        public ToolCallStart(String toolCallId, String name, Object args) {
            this("tool_call_start", toolCallId, name, args);
        }
    }

    record ToolCallEnd(@JsonProperty("type") String type, @JsonProperty("tool_call_id") String toolCallId,
                       @JsonProperty("name") String name, @JsonProperty("ok") boolean ok,
                       @JsonProperty("result") Object result, @JsonProperty("duration_ms") long durationMs) implements SseEvent {
        public ToolCallEnd(String toolCallId, String name, boolean ok, Object result, long durationMs) {
            this("tool_call_end", toolCallId, name, ok, result, durationMs);
        }
    }

    record PermissionRequest(@JsonProperty("type") String type, @JsonProperty("permission_id") String permissionId,
                            @JsonProperty("tool_call_id") String toolCallId, @JsonProperty("tool_name") String toolName,
                            @JsonProperty("reason") String reason, @JsonProperty("choices") List<String> choices) implements SseEvent {
        public PermissionRequest(String permissionId, String toolCallId, String toolName, String reason, List<String> choices) {
            this("permission_request", permissionId, toolCallId, toolName, reason, choices);
        }
    }

    record PermissionResponse(@JsonProperty("type") String type, @JsonProperty("permission_id") String permissionId,
                              @JsonProperty("decision") String decision) implements SseEvent {
        public PermissionResponse(String permissionId, String decision) {
            this("permission_response", permissionId, decision);
        }
    }

    record MessageStop(@JsonProperty("type") String type, @JsonProperty("finish_reason") String finishReason) implements SseEvent {
        public MessageStop(String finishReason) {
            this("message_stop", finishReason);
        }
    }

    /**
     * 回合统计（add-session-stats-bar）：每次回合结束时于 {@code message_stop} 之前推送，携带会话累计值。
     *
     * <p>派生指标（{@code avg_ttft_ms} / {@code tok_per_sec} / {@code cache_hit_rate}）不可用时为 {@code null}
     * （前端显示 N/A）。
     */
    record TurnStats(
            @JsonProperty("type") String type,
            @JsonProperty("turns") long turns,
            @JsonProperty("steps") long steps,
            @JsonProperty("tokens_in") long tokensIn,
            @JsonProperty("tokens_out") long tokensOut,
            @JsonProperty("llm_ms") long llmMs,
            @JsonProperty("tool_ms") long toolMs,
            @JsonProperty("avg_ttft_ms") Double avgTtftMs,
            @JsonProperty("tok_per_sec") Double tokPerSec,
            @JsonProperty("cache_hit_rate") Double cacheHitRate)
            implements SseEvent {

        public TurnStats(SessionStats s) {
            this(
                    "turn_stats",
                    s.turns(),
                    s.steps(),
                    s.tokensIn(),
                    s.tokensOut(),
                    s.llmMillis(),
                    s.toolMillis(),
                    s.avgTtftMs(),
                    s.tokPerSec(),
                    s.cacheHitRate());
        }
    }

    record Error(@JsonProperty("type") String type, @JsonProperty("code") String code, @JsonProperty("message") String message) implements SseEvent {
        public Error(String code, String message) {
            this("error", code, message);
        }
    }
}

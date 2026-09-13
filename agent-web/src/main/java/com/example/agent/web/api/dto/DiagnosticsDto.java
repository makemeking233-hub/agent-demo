package com.example.agent.web.api.dto;

import com.example.agent.session.SessionDiagnostics;
import java.util.List;

/**
 * 诊断报告 DTO（improve-failure-observability）。
 *
 * <p>把「会话记录完整性」这类排查信息一次性给出去，替代此前手写脚本扫 JSONL 的做法。
 *
 * @param sessions_dir 被扫描的会话目录
 * @param logs_dir 运行时日志根目录（便于直接去看日志）
 * @param scanned_sessions 实际扫描的会话数
 * @param incomplete_sessions 记录不完整的会话；全部完整时为空
 */
public record DiagnosticsDto(
        @com.fasterxml.jackson.annotation.JsonProperty("sessions_dir") String sessionsDir,
        @com.fasterxml.jackson.annotation.JsonProperty("logs_dir") String logsDir,
        @com.fasterxml.jackson.annotation.JsonProperty("scanned_sessions") int scannedSessions,
        @com.fasterxml.jackson.annotation.JsonProperty("incomplete_sessions")
                List<IncompleteSessionDto> incompleteSessions) {

    /**
     * 单个不完整会话。
     *
     * @param session_id 会话 id
     * @param dangling_tool_call_ids 有 tool_calls 但在下一条非 tool 消息前缺少结果的 id
     * @param size_bytes 存档文件字节数
     */
    public record IncompleteSessionDto(
            @com.fasterxml.jackson.annotation.JsonProperty("session_id") String sessionId,
            @com.fasterxml.jackson.annotation.JsonProperty("dangling_tool_call_ids")
                    List<String> danglingToolCallIds,
            @com.fasterxml.jackson.annotation.JsonProperty("size_bytes") long sizeBytes) {}

    /**
     * 由核心层报告转换。
     *
     * @param r 核心层报告（{@code null} 时返回空报告）
     * @return DTO
     */
    public static DiagnosticsDto from(SessionDiagnostics.Report r) {
        if (r == null) {
            return new DiagnosticsDto(null, null, 0, List.of());
        }
        List<IncompleteSessionDto> incomplete =
                r.incomplete() == null
                        ? List.of()
                        : r.incomplete().stream()
                                .map(
                                        s ->
                                                new IncompleteSessionDto(
                                                        s.sessionId(),
                                                        s.danglingToolCallIds(),
                                                        s.sizeBytes()))
                                .toList();
        return new DiagnosticsDto(
                r.sessionsDir(), r.logsDir(), r.scannedSessions(), incomplete);
    }
}

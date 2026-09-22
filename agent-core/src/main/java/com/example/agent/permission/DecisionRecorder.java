package com.example.agent.permission;

import com.example.agent.log.SessionLogSink;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 权限裁决事件记录器（rewrite-permission-mode-dsh T5.1 引入；从 PermissionManager 抽出）。
 *
 * <p>职责单一：把非 ALLOW 的裁决（ASK / DENY）广播到 {@link SessionLogSink}；ALLOW 不产生事件。
 *
 * <p>事件 payload：
 *
 * <pre>{@code
 * {
 *   "tool": "WriteFile",
 *   "path": "/some/path",
 *   "decision": "ask" | "deny",
 *   "reason": "policy_ask" | "tool_deny"
 * }
 * }</pre>
 *
 * <p>Sink 默认为 {@link SessionLogSink#NOOP}；可通过 {@link #setSink} 注入真实 sink。
 */
public final class DecisionRecorder {

    private SessionLogSink sink = SessionLogSink.NOOP;

    /** 注入 sink（{@code null} 重置为 no-op） */
    public void setSink(SessionLogSink sink) {
        this.sink = sink != null ? sink : SessionLogSink.NOOP;
    }

    /**
     * 记录裁决事件（ALLOW 不产生事件；ASK / DENY 广播）。
     *
     * @param toolName 工具名
     * @param path     目标路径（无路径语义时可为 null）
     * @param decision 裁决结果
     */
    public void record(String toolName, String path, PermissionDecision decision) {
        if (decision == null || decision.behavior() == PermissionDecision.Behavior.ALLOW) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool", toolName);
        payload.put("path", path != null ? path : "");
        payload.put(
                "decision",
                decision.behavior() == PermissionDecision.Behavior.ASK ? "ask" : "deny");
        payload.put(
                "reason",
                decision.behavior() == PermissionDecision.Behavior.DENY ? "tool_deny" : "policy_ask");
        sink.onPermissionDecision(payload);
    }
}
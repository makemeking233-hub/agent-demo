package com.example.agent.tools;

import com.example.agent.AbortSignal;
import com.example.agent.permission.PermissionDecision;
import com.example.agent.permission.PermissionManager;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.Map;

/**
 * Tool 协议接口（借鉴 Claude Code 04b §2）。
 *
 * <p>Fail-Closed 默认：所有新工具默认 {@code isConcurrencySafe=false / isReadOnly=false / isDestructive=false}，
 * {@code checkPermissions} 默认返回 ask（详见 design.md §6.2）。
 */
public interface Tool<I, O> {
    String name();
    String description();
    Map<String, Object> inputSchema();

    // 安全属性（默认 fail-closed）
    default boolean isConcurrencySafe(I input) { return false; }
    default boolean isReadOnly(I input) { return false; }
    default boolean isDestructive(I input) { return false; }

    /** 工具级裁决（详见 design.md §6.5 Q9：deny 是终态，不可覆盖） */
    default PermissionDecision checkPermissions(I input, ToolContext ctx) {
        return PermissionDecision.ask();
    }

    /** 渲染（给用户看） */
    String renderUse(I input);
    String renderResult(O output);

    /** 执行（异步） */
    Mono<ToolResult<O>> execute(I input, ToolContext ctx);

    /** 工具上下文总线（M2 占位；后续在 AgentLoop.processToolCall 时组装） */
    record ToolContext(Path workingDirectory, PermissionManager permissions, AbortSignal abortSignal) {}
}
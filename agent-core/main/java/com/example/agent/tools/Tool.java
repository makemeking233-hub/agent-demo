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
 * <p>Fail-Closed 默认：所有新工具默认 {@code isConcurrencySafe=false / isReadOnly=false /
 * isDestructive=false}， {@code checkPermissions} 默认返回 ask（详见 design.md §6.2）。
 *
 * @param <I> 输入类型（v0.1 通常为 record）
 * @param <O> 输出类型（String / structured record）
 */
public interface Tool<I, O> {
    /** 工具名（与 {@link ToolRegistry} 索引对应） */
    String name();

    /** 工具描述（LLM 用来判断何时调用） */
    String description();

    /** JSON Schema（LLM 看的输入格式） */
    Map<String, Object> inputSchema();

    /** 是否可并发（v0.1 默认 false；true 表示无副作用可并行） */
    default boolean isConcurrencySafe(I input) {
        return false;
    }

    /** 是否只读（true 跳过写权限确认） */
    default boolean isReadOnly(I input) {
        return false;
    }

    /** 是否破坏性（true 需要用户确认） */
    default boolean isDestructive(I input) {
        return false;
    }

    /**
     * 工具语义分类（用于 {@code PermissionManager} 策略决策，默认 {@link ToolCategory#OTHER}）。
     *
     * @return 工具分类
     */
    default ToolCategory category() {
        return ToolCategory.OTHER;
    }

    /**
     * 工具级权限裁决（详见 design.md §6.5 Q9：deny 是终态，不可覆盖）。
     *
     * @param input 工具输入
     * @param ctx 工具执行上下文
     * @return 裁决结果（allow / ask / deny）
     */
    default PermissionDecision checkPermissions(I input, ToolContext ctx) {
        return PermissionDecision.ask();
    }

    /**
     * 工具被调用时的渲染（给用户看）。
     *
     * @param input 工具输入
     * @return 展示给用户的字符串
     */
    String renderUse(I input);

    /**
     * 工具执行结果的渲染。
     *
     * @param output 工具输出
     * @return 展示给用户的字符串
     */
    String renderResult(O output);

    /**
     * 把模型返回的工具参数 JSON 字符串解析为类型化输入。
     *
     * <p>v0.1 由各 Tool 自行实现（详见 ToolCall 注释）；v0.2 已明确为通用 JSON → Input 转换，
     * 基类（如 {@code AbstractFileTool}）提供 Jackson 默认实现。
     *
     * @param argumentsJson 模型生成的参数 JSON（如 {@code {"path": "/tmp/a.txt"}}）
     * @return 类型化输入对象
     * @throws IllegalArgumentException JSON 解析失败或工具未实现时抛出
     */
    default I parseArguments(String argumentsJson) {
        throw new UnsupportedOperationException(name() + " 未实现 parseArguments");
    }

    /**
     * 异步执行。
     *
     * @param input 工具输入
     * @param ctx 工具执行上下文（含 workingDirectory / permissions / abortSignal）
     * @return 异步结果
     */
    Mono<ToolResult<O>> execute(I input, ToolContext ctx);

    /**
     * 工具上下文总线（在 {@link com.example.agent.core.AgentLoop#executeTools} 时组装）。
     *
     * @param workingDirectory 工作目录（所有相对路径的基准）
     * @param permissions 权限管理器
     * @param abortSignal 中断信号（M9 InterruptController 接入 Ctrl+C）
     * @param agentDataDir agent 数据目录（{@code ~/.agent-demo}，memory/logs/sessions 所在；文件工具额外放行，可空）
     */
    record ToolContext(
            Path workingDirectory,
            PermissionManager permissions,
            AbortSignal abortSignal,
            Path agentDataDir) {
        /** 3 参便捷构造：{@code agentDataDir} 为 {@code null}（不额外放行任何目录）。 */
        public ToolContext(Path workingDirectory, PermissionManager permissions, AbortSignal abortSignal) {
            this(workingDirectory, permissions, abortSignal, null);
        }
    }
}

package com.example.agent.tools;

import com.example.agent.tools.file.ToolInput;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

import java.nio.file.Path;

/**
 * 文件路径类工具的模板方法基类（v0.1：Read/Write/Edit/Ls）。
 *
 * <p>统一处理：
 *
 * <ul>
 *   <li>{@link org.slf4j.Logger} 实例化（基于子类 {@code getClass()}）
 *   <li>路径 normalize + 越界检查（防止 {@code ../} 跳出 workingDirectory）
 *   <li>返回 {@link ToolResult#error(String)} 的短路执行（用于越界等预执行错误）
 * </ul>
 *
 * <p>子类只需实现 {@link #doExecute(ToolInput, java.nio.file.Path, Tool.ToolContext)}。
 *
 * @param <I> 输入类型（必须实现 {@link ToolInput}）
 */
public abstract class AbstractFileTool<I extends ToolInput> implements Tool<I, String> {
    /** 子类日志（按具体类名生成 logger） */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** JSON 反序列化器（record Input 由 Jackson 2.15+ 原生支持） */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 执行入口：模板方法（normalize → bounds 检查 → doExecute） */
    @Override
    public final Mono<ToolResult<String>> execute(I input, Tool.ToolContext ctx) {
        PathResult r = resolve(input, ctx);
        if (r.error() != null) return Mono.just(r.error());
        return doExecute(input, r.path(), ctx);
    }

    /**
     * 把模型参数 JSON 反序列化为类型化输入（子类声明 {@link #inputClass()}）。
     *
     * @param argumentsJson 模型生成的参数 JSON
     * @return 反序列化后的输入对象
     * @throws IllegalArgumentException JSON 格式错误时抛出（AgentLoop 转成错误 ToolResult）
     */
    @Override
    public I parseArguments(String argumentsJson) {
        try {
            return JSON.readValue(argumentsJson, inputClass());
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "参数 JSON 解析失败 (" + argumentsJson + "): " + e.getMessage(), e);
        }
    }

    /**
     * 输入类型（供 {@link #parseArguments} 反序列化）。
     *
     * @return 输入 record 的 Class
     */
    protected abstract Class<I> inputClass();

    /**
     * 子类实现的实际执行逻辑（在路径已校验通过后调用）。
     *
     * @param input 工具输入（已校验）
     * @param target 已 normalize 的绝对路径（在 workingDirectory 内）
     * @param ctx 工具执行上下文
     * @return 异步执行结果
     */
    protected abstract Mono<ToolResult<String>> doExecute(
            I input, Path target, Tool.ToolContext ctx);

    /**
     * 解析 + 越界检查（一次返回路径或错误结果）。
     *
     * @param input 工具输入
     * @param ctx 工具上下文
     * @return {@link PathResult}（错误时 error 非空）
     */
    private PathResult resolve(I input, Tool.ToolContext ctx) {
        Path base = ctx.workingDirectory();
        Path p = base.resolve(input.path() == null ? "" : input.path()).normalize();
        if (!p.startsWith(base)) {
            return PathResult.error(ToolResult.<String>error("路径越界: " + input.path()));
        }
        return PathResult.ok(p);
    }

    /** 路径解析结果（路径 + 可选错误） */
    private record PathResult(Path path, ToolResult<String> error) {
        static PathResult ok(Path p) {
            return new PathResult(p, null);
        }

        static PathResult error(ToolResult<String> e) {
            return new PathResult(null, e);
        }
    }
}

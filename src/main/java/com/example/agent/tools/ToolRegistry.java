package com.example.agent.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表（按 name 索引）。
 *
 * <p>v0.1 简化：list() 返回所有工具；get(name) 按名字查。
 * M3 Task 3.7 添加 registerMemoryTools 静态方法。
 */
public class ToolRegistry {
    private final Map<String, Tool<?, ?>> tools = new LinkedHashMap<>();

    public void register(Tool<?, ?> tool) {
        tools.put(tool.name(), tool);
    }

    @SuppressWarnings("unchecked")
    public <I, O> Tool<I, O> get(String name) {
        return (Tool<I, O>) tools.get(name);
    }

    public List<Tool<?, ?>> list() {
        return List.copyOf(tools.values());
    }

    /** 反射查（AgentLoop 调用 execute 时不需要泛型转换） */
    public Tool<?, ?> getRaw(String name) {
        return tools.get(name);
    }

    /** M5 Memory 自动注入：Agent 默认拥有读写 memory 的工具 */
    public static void registerMemoryTools(ToolRegistry registry) {
        registry.register(new ReadFileTool());
        registry.register(new WriteFileTool());
        registry.register(new EditFileTool());
    }

    // v0.1 stub：ReadFileTool/WriteFileTool/EditFileTool 由 M3 Task 3.2/3.3 提供
    private static final class ReadFileTool implements Tool<Object, Object> {
        @Override public String name() { return "ReadFile"; }
        @Override public String description() { return "stub"; }
        @Override public java.util.Map<String, Object> inputSchema() { return java.util.Map.of(); }
        @Override public String renderUse(Object input) { return ""; }
        @Override public String renderResult(Object output) { return ""; }
        @Override public reactor.core.publisher.Mono<ToolResult<Object>> execute(Object input, ToolContext ctx) {
            return reactor.core.publisher.Mono.error(new UnsupportedOperationException("M3 stub"));
        }
    }
    private static final class WriteFileTool implements Tool<Object, Object> {
        @Override public String name() { return "WriteFile"; }
        @Override public String description() { return "stub"; }
        @Override public java.util.Map<String, Object> inputSchema() { return java.util.Map.of(); }
        @Override public String renderUse(Object input) { return ""; }
        @Override public String renderResult(Object output) { return ""; }
        @Override public reactor.core.publisher.Mono<ToolResult<Object>> execute(Object input, ToolContext ctx) {
            return reactor.core.publisher.Mono.error(new UnsupportedOperationException("M3 stub"));
        }
    }
    private static final class EditFileTool implements Tool<Object, Object> {
        @Override public String name() { return "EditFile"; }
        @Override public String description() { return "stub"; }
        @Override public java.util.Map<String, Object> inputSchema() { return java.util.Map.of(); }
        @Override public String renderUse(Object input) { return ""; }
        @Override public String renderResult(Object output) { return ""; }
        @Override public reactor.core.publisher.Mono<ToolResult<Object>> execute(Object input, ToolContext ctx) {
            return reactor.core.publisher.Mono.error(new UnsupportedOperationException("M3 stub"));
        }
    }
}
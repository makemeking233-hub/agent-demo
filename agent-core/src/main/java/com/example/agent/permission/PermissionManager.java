package com.example.agent.permission;

import com.example.agent.log.SessionLogSink;
import com.example.agent.tools.Tool;
import com.example.agent.tools.ToolCategory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 权限裁决管理器（详见 design.md §6.5）。
 *
 * <p>裁决顺序：
 *
 * <ol>
 *   <li>敏感路径匹配 → 强制 ask（即使 defaultRead=allow）
 *   <li>按 {@link ToolCategory} 查默认策略（read/write/shell/other）
 * </ol>
 *
 * <p>Q9 决议：Tool.checkPermissions 返回 deny 是终态，由本类调用 tool 层方法处理； 本类当前 v0.1 仅做 1+2 两步。
 *
 * <p>路径匹配逻辑抽到 {@link PermissionPathMatcher}，本类专注策略。
 *
 * <p>工具名 → {@link ToolCategory} 通过 {@code categoryRegistry} 注册（默认包含 5 个已知工具，新增工具时调 {@link
 * #registerCategory(String, ToolCategory)} 或 override {@code Tool.category()}）。
 */
public class PermissionManager {
    /**
     * 权限策略（read/write/shell 默认 + 敏感路径 glob）
     */
    private final PermissionPolicy policy;

    /**
     * 路径 glob 匹配器（敏感路径检测）
     */
    private final PermissionPathMatcher pathMatcher;

    /**
     * 工具名 → 语义分类注册表（消 switch(String)）
     */
    private final Map<String, ToolCategory> categoryRegistry = new HashMap<>();

    /**
     * 会话日志观察者（permission/decision 事件广播；默认 no-op）
     */
    private SessionLogSink sink = SessionLogSink.NOOP;

    /**
     * 注入会话日志观察者（AgentLoop 装配时调用；{@code null} 重置为 no-op）。
     *
     * @param sink 会话日志观察者
     */
    public void setSink(SessionLogSink sink) {
        this.sink = sink != null ? sink : SessionLogSink.NOOP;
    }

    /**
     * 默认策略构造（{@link PermissionPolicy#defaults()} + 内置 5 个工具分类）
     */
    public PermissionManager() {
        this(PermissionPolicy.defaults());
        registerDefaults();
    }

    /**
     * 自定义策略构造。
     *
     * @param policy 权限策略（不可空）
     */
    public PermissionManager(PermissionPolicy policy) {
        this.policy = policy;
        this.pathMatcher = new PermissionPathMatcher(policy.sensitivePathPatterns());
        registerDefaults();
    }

    /**
     * 注册 v0.1 已知的 5 个工具分类
     */
    private void registerDefaults() {
        categoryRegistry.put("ReadFile", ToolCategory.READ);
        categoryRegistry.put("Ls", ToolCategory.READ);
        categoryRegistry.put("WriteFile", ToolCategory.WRITE);
        categoryRegistry.put("EditFile", ToolCategory.WRITE);
        categoryRegistry.put("Shell", ToolCategory.SHELL);
    }

    /**
     * 注册自定义工具分类（新增 Tool 时调用）。
     *
     * @param toolName 工具名
     * @param category 语义分类
     */
    public void registerCategory(String toolName, ToolCategory category) {
        categoryRegistry.put(toolName, category);
    }

    /**
     * 主裁决方法。
     *
     * @param toolName 工具名
     * @param input    工具输入（用于抽取路径）
     * @param ctx      工具上下文（可空，v0.1 暂未使用）
     * @return 裁决结果
     */
    public PermissionDecision decide(String toolName, Object input, Tool.ToolContext ctx) {
        String path = extractPath(input);
        PermissionDecision d;
        if (path != null && pathMatcher.matches(path)) {
            d = PermissionDecision.ask();
        } else {
            d = decideByCategory(categoryRegistry.getOrDefault(toolName, ToolCategory.OTHER));
        }
        broadcast(toolName, path, d);
        return d;
    }

    /**
     * 广播权限裁决事件（仅 ask/deny；allow 不产生事件，避免噪声）。
     *
     * @param toolName 工具名
     * @param path     抽取出的路径（无路径语义时为 {@code null}）
     * @param d        裁决结果
     */
    private void broadcast(String toolName, String path, PermissionDecision d) {
        if (d.behavior() == PermissionDecision.Behavior.ALLOW) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool", toolName);
        payload.put("path", path != null ? path : "");
        payload.put(
                "decision",
                d.behavior() == PermissionDecision.Behavior.ASK ? "ask" : "deny");
        payload.put(
                "reason",
                d.behavior() == PermissionDecision.Behavior.DENY ? "tool_deny" : "policy_ask");
        sink.onPermissionDecision(payload);
    }

    /**
     * 兼容 stub 调用（M2 AgentLoop 用）。
     *
     * @param toolName 工具名
     * @param input    工具输入
     * @return 裁决结果
     */
    public PermissionDecision decide(String toolName, Object input) {
        return decide(toolName, input, null);
    }

    /**
     * 按 {@link ToolCategory} 应用默认策略（策略注册表核心）。
     *
     * @param category 工具语义分类
     * @return 裁决结果
     */
    private PermissionDecision decideByCategory(ToolCategory category) {
        return switch (category) {
            case READ -> policy.defaultRead()
                    ? PermissionDecision.allow()
                    : PermissionDecision.ask();
            case WRITE -> policy.defaultWrite()
                    ? PermissionDecision.allow()
                    : PermissionDecision.ask();
            case SHELL -> policy.defaultShell()
                    ? PermissionDecision.allow()
                    : PermissionDecision.ask();
            case OTHER -> PermissionDecision.ask();
        };
    }

    /**
     * 从工具输入抽取文件路径（sealed {@link com.example.agent.tools.ToolInput} 多态分发）。
     *
     * @param input 工具输入
     * @return 路径字符串；类型不匹配或 ToolContext 时返回 {@code null}
     */
    private String extractPath(Object input) {
        if (input instanceof Tool.ToolContext) return null;
        if (input instanceof com.example.agent.tools.file.ToolInput ti) return ti.path();
        return null;
    }
}

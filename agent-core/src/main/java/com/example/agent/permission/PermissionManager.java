package com.example.agent.permission;

import com.example.agent.log.SessionLogSink;
import com.example.agent.tools.Tool;
import com.example.agent.tools.ToolCategory;

import java.nio.file.Path;
import java.nio.file.Paths;
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
     * 当前权限模式（add-permission-mode-dropdown；缺省 {@link PermissionMode#READ_ONLY}，运行期可重设）。
     */
    private PermissionMode mode = PermissionMode.DEFAULT;

    /**
     * 会话工作目录（{@code workspace_write} 边界判定用；由 AgentLoop 装配时注入）。
     */
    private Path workingDir;

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
     * 主裁决方法（模式感知 + 工作区边界，add-permission-mode-dropdown）。
     *
     * <p>裁决顺序：
     *
     * <ol>
     *   <li>{@code mode == FULL_ACCESS} → allow（含敏感路径；仅工具级 DENY 兜底）
     *   <li>命中敏感路径 pattern → ask（即使 read/workspace 模式默认放行）
     *   <li>否则按 {@code mode × category} 裁决；{@code WORKSPACE_WRITE} 下 WRITE 再按工作区边界
     * </ol>
     *
     * @param toolName 工具名
     * @param input    工具输入（用于抽取路径）
     * @param ctx      工具上下文（含工作目录，可空）
     * @return 裁决结果
     */
    public PermissionDecision decide(String toolName, Object input, Tool.ToolContext ctx) {
        String path = extractPath(input);
        ToolCategory category = categoryRegistry.getOrDefault(toolName, ToolCategory.OTHER);
        PermissionDecision d;
        if (mode == PermissionMode.FULL_ACCESS) {
            // 全权限：放行一切（含敏感路径）。工具级 DENY（checkPermissions 终态）在 AgentLoop 另行兜底。
            d = PermissionDecision.allow();
        } else if (path != null && pathMatcher.matches(path)) {
            // 敏感路径：非 full_access 下强制 ask（Fail-Closed 兜底）。
            d = PermissionDecision.ask();
        } else {
            boolean withinWorkspace = isWithinWorkspace(path, ctx);
            d = mode.defaultDecision(category, withinWorkspace);
        }
        broadcast(toolName, path, d);
        return d;
    }

    /**
     * 目标路径是否位于会话工作目录内（仅 WRITE 类别有意义；非 WRITE 类别由模式默认裁决决定）。
     *
     * @param path 抽取出的路径（无路径语义时为 {@code null}）
     * @param ctx  工具上下文（取其工作目录；为空时回退到注入的 {@link #workingDir}）
     * @return 是否在工作区边界内
     */
    private boolean isWithinWorkspace(String path, Tool.ToolContext ctx) {
        if (path == null) return false;
        Path base = workingDirFor(ctx);
        if (base == null) return false;
        Path p = Paths.get(path).normalize().toAbsolutePath();
        Path b = base.normalize().toAbsolutePath();
        return p.startsWith(b);
    }

    /** 优先级：上下文工作目录 > 注入字段。 */
    private Path workingDirFor(Tool.ToolContext ctx) {
        if (ctx != null && ctx.workingDirectory() != null) return ctx.workingDirectory();
        return workingDir;
    }

    /**
     * 设置当前权限模式（会话内实时切换）。
     *
     * @param mode 新模式（不可空）
     */
    public void setMode(PermissionMode mode) {
        this.mode = mode != null ? mode : PermissionMode.DEFAULT;
    }

    /**
     * 设置会话工作目录（{@code workspace_write} 边界判定用）。
     *
     * @param workingDir 工作目录（可空 = 无边界的严格 fallback）
     */
    public void setWorkingDirectory(Path workingDir) {
        this.workingDir = workingDir;
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

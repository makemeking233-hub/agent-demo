package com.example.agent.permission;

import com.example.agent.log.SessionLogSink;
import com.example.agent.tools.Tool;
import com.example.agent.tools.ToolCategory;
import com.example.agent.tools.file.ToolInput;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 权限裁决管理器（rewrite-permission-mode-dsh T5.2 改造为 wrapper）。
 *
 * <p>结构（拆分后）：
 *
 * <ul>
 *   <li>{@link SandboxPolicyService} — 模式 + workspaceRoot + per-call policy 解析
 *   <li>{@link SensitivePathMatcher} — 敏感路径匹配（{@code **\/.ssh/**} 等）
 *   <li>{@link DecisionRecorder} — 会话日志 sink 广播
 *   <li>本类作为 wrapper：把 mode × category + sensitive path 升级 + workspace 边界组合起来
 * </ul>
 *
 * <p>裁决顺序（保留 v0.1 行为 + 接入 sandbox policy）：
 *
 * <ol>
 *   <li>{@code mode == FULL_ACCESS} → allow（含敏感路径；仅工具级 DENY 兜底）
 *   <li>命中敏感路径 pattern → ask（即使 read/workspace 模式默认放行）
 *   <li>否则委派 {@link SandboxPolicyService#defaultDecision(SandboxMode, ToolCategory, Path, Path)}
 * </ol>
 *
 * <p>Q9 决议：Tool.checkPermissions 返回 deny 是终态，由 AgentLoop 兜底；本类仅做 1+2+3。
 */
public class PermissionManager {
    /** 当前权限模式（保留 v0.1 PermissionMode 3 档作为公开 API；内部用 SandboxMode） */
    private PermissionMode mode = PermissionMode.DEFAULT;

    /** 会话工作目录（{@code workspace_write} 边界判定用） */
    private Path workingDir;

    /** sandbox policy 服务（per-call policy 解析；T5.2 注入） */
    private final SandboxPolicyService sandboxPolicy;

    /** 敏感路径匹配器（T5.2 注入） */
    private final SensitivePathMatcher sensitivePathMatcher;

    /** 裁决事件记录器（T5.2 注入） */
    private final DecisionRecorder recorder;

    /** 工具名 → 语义分类注册表 */
    private final Map<String, ToolCategory> categoryRegistry = new HashMap<>();

    /**
     * 默认策略构造（默认 components）。
     */
    public PermissionManager() {
        this(PermissionPolicy.defaults(),
                SandboxPolicyService.createForTest(SandboxMode.PLAN, Path.of(System.getProperty("user.dir"))),
                SensitivePathMatcher.defaults(),
                new DecisionRecorder());
        registerDefaults();
    }

    /**
     * 自定义策略构造 + 默认 components。
     */
    public PermissionManager(PermissionPolicy policy) {
        this(policy,
                SandboxPolicyService.createForTest(SandboxMode.PLAN, Path.of(System.getProperty("user.dir"))),
                SensitivePathMatcher.defaults(),
                new DecisionRecorder());
        registerDefaults();
    }

    /**
     * 全注入构造（T5.2 引入）。
     */
    public PermissionManager(PermissionPolicy policy, SandboxPolicyService sandboxPolicy,
                             SensitivePathMatcher sensitivePathMatcher, DecisionRecorder recorder) {
        if (policy == null) throw new IllegalArgumentException("policy 不可空");
        if (sandboxPolicy == null) throw new IllegalArgumentException("sandboxPolicy 不可空");
        if (sensitivePathMatcher == null) throw new IllegalArgumentException("sensitivePathMatcher 不可空");
        if (recorder == null) throw new IllegalArgumentException("recorder 不可空");
        this.sandboxPolicy = sandboxPolicy;
        this.sensitivePathMatcher = sensitivePathMatcher;
        this.recorder = recorder;
        registerDefaults();
    }

    /** 注册 v0.1 已知的 5 个工具分类 */
    private void registerDefaults() {
        categoryRegistry.put("ReadFile", ToolCategory.READ);
        categoryRegistry.put("Ls", ToolCategory.READ);
        categoryRegistry.put("WriteFile", ToolCategory.WRITE);
        categoryRegistry.put("EditFile", ToolCategory.WRITE);
        categoryRegistry.put("Shell", ToolCategory.SHELL);
    }

    /** 注册自定义工具分类 */
    public void registerCategory(String toolName, ToolCategory category) {
        categoryRegistry.put(toolName, category);
    }

    /**
     * 主裁决方法（mode × category + sensitive path 升级 + workspace 边界，T5.2 wrapper）。
     */
    public PermissionDecision decide(String toolName, Object input, Tool.ToolContext ctx) {
        String path = extractPath(input);
        ToolCategory category = categoryRegistry.getOrDefault(toolName, ToolCategory.OTHER);
        PermissionDecision d;

        if (mode == PermissionMode.FULL_ACCESS) {
            d = PermissionDecision.allow();
        } else if (path != null && sensitivePathMatcher.matches(path)) {
            d = PermissionDecision.ask();
        } else {
            Path workspaceRoot = workspaceRootFor(ctx);
            Path target = pathToCheck(path, ctx);
            d = sandboxPolicy.defaultDecision(
                    mode.toSandboxMode(), category, target, workspaceRoot);
        }
        recorder.record(toolName, path, d);
        return d;
    }

    /** Stub 兼容 */
    public PermissionDecision decide(String toolName, Object input) {
        return decide(toolName, input, null);
    }

    /** 工作目录优先级：ctx.workingDirectory > 注入字段 */
    private Path workspaceRootFor(Tool.ToolContext ctx) {
        if (ctx != null && ctx.workingDirectory() != null) return ctx.workingDirectory();
        return workingDir;
    }

    /** 路径转 Path（用于 workspace 内/外判定） */
    private Path pathToCheck(String raw, Tool.ToolContext ctx) {
        if (raw == null) return null;
        Path base = workspaceRootFor(ctx);
        try {
            return base != null ? base.resolve(raw).normalize() : Paths.get(raw).normalize();
        } catch (Exception e) {
            return Paths.get(raw).normalize();
        }
    }

    /** 设置当前权限模式（运行期切换 + 同步到 SandboxPolicyService） */
    public void setMode(PermissionMode mode) {
        this.mode = mode != null ? mode : PermissionMode.DEFAULT;
        if (sandboxPolicy != null) {
            sandboxPolicy.setMode(mode != null ? mode.toSandboxMode() : SandboxMode.DEFAULT);
        }
    }

    /** 设置会话工作目录（{@code workspace_write} 边界判定用） */
    public void setWorkingDirectory(Path workingDir) {
        this.workingDir = workingDir;
    }

    /** 注入会话日志观察者 */
    public void setSink(SessionLogSink sink) {
        recorder.setSink(sink);
    }

    /** 当前权限模式（v0.1 兼容 API） */
    public PermissionMode mode() {
        return mode;
    }

    /**
     * 从工具输入抽取文件路径（sealed {@link com.example.agent.tools.file.ToolInput} 多态分发）。
     */
    private String extractPath(Object input) {
        if (input instanceof Tool.ToolContext) return null;
        if (input instanceof ToolInput ti) return ti.path();
        return null;
    }
}
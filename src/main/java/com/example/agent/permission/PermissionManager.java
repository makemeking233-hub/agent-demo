package com.example.agent.permission;

import com.example.agent.tools.Tool;

import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * 权限裁决管理器（详见 design.md §6.5）。
 *
 * <p>裁决顺序：
 * <ol>
 *   <li>敏感路径匹配 → 强制 ask（即使 defaultRead=allow）</li>
 *   <li>按 tool 类型查默认策略（read/write/shell）</li>
 * </ol>
 *
 * <p>Q9 决议：Tool.checkPermissions 返回 deny 是终态，由 PermissionManager 调用 tool 层方法处理；
 * 本类当前 v0.1 仅做 1+2 两步。
 */
public class PermissionManager {
    private final PermissionPolicy policy;

    public PermissionManager() {
        this(PermissionPolicy.defaults());
    }

    public PermissionManager(PermissionPolicy policy) {
        this.policy = policy;
    }

    /** 主裁决方法 */
    public PermissionDecision decide(String toolName, Object input, Tool.ToolContext ctx) {
        String path = extractPath(input);
        if (path != null && matchesSensitivePath(path)) {
            return PermissionDecision.ask();
        }
        return switch (toolName) {
            case "ReadFile", "Ls" -> policy.defaultRead() ? PermissionDecision.allow() : PermissionDecision.ask();
            case "WriteFile", "EditFile" -> policy.defaultWrite() ? PermissionDecision.allow() : PermissionDecision.ask();
            case "Shell" -> policy.defaultShell() ? PermissionDecision.allow() : PermissionDecision.ask();
            default -> PermissionDecision.ask();
        };
    }

    /** 兼容 stub 调用（M2 AgentLoop 用） */
    public PermissionDecision decide(String toolName, Object input) {
        return decide(toolName, input, null);
    }

    private String extractPath(Object input) {
        if (input instanceof Tool.ToolContext) return null;
        try {
            if (input instanceof com.example.agent.tools.ReadFileTool.Input i) return i.path();
            if (input instanceof com.example.agent.tools.WriteFileTool.Input i) return i.path();
            if (input instanceof com.example.agent.tools.EditFileTool.Input i) return i.path();
            if (input instanceof com.example.agent.tools.LsTool.Input i) return i.path();
        } catch (Exception ignored) {}
        return null;
    }

    /** Ant 风格 glob 转正则：双星跨段、单星不跨段、连续双星斜杠可为空。 */
    private boolean matchesSensitivePath(String path) {
        String normalized = Paths.get(path).toString().replace('\\', '/');
        for (String glob : policy.sensitivePathPatterns()) {
            String regex = "^" + glob
                .replace("\\", "\\\\")
                .replace(".", "\\.")
                .replace("**/", "::DOUBLESLASH::")
                .replace("**", ".*")
                .replace("*", "[^/]*")
                .replace("::DOUBLESLASH::", "(?:.*/)?") + "$";
            if (Pattern.matches(regex, normalized)) {
                return true;
            }
        }
        return false;
    }
}
package com.example.agent.tools;

import com.example.agent.permission.FsDenialKind;
import com.example.agent.permission.SandboxMode;

import java.nio.file.Path;

/**
 * 路径解析结果（rewrite-permission-mode-dsh T3.3 从 AbstractFileTool 内部 record 提升为顶层）。
 *
 * <p>三态：
 *
 * <ul>
 *   <li>{@link #ok(Path)} — 成功，路径在 sandbox policy 允许范围
 *   <li>{@link #error(ToolResult)} — 失败（如 JSON 解析异常），error 非空
 *   <li>{@link #denied(FsDenialKind, SandboxMode, SandboxMode, String)} — sandbox policy 拒绝，
 *       携带结构化 denial info（kind / currentMode / suggestedMode / message），供前端渲染 marker
 * </ul>
 */
public record PathResult(Path path, ToolResult<String> error, Denial denial) {

    /**
     * 结构化拒绝信息（rewrite-permission-mode-dsh T3.3）。
     *
     * @param kind          拒绝原因分类
     * @param currentMode   当前 sandbox mode
     * @param suggestedMode 推荐升级 mode（null = 不建议升级）
     * @param marker        展示给模型的拒绝文本（dsh 风格 marker）
     */
    public record Denial(
            FsDenialKind kind,
            SandboxMode currentMode,
            SandboxMode suggestedMode,
            String marker) {
    }

    /** 成功（path 非空，error / denial 为 null）。 */
    public static PathResult ok(Path p) {
        return new PathResult(p, null, null);
    }

    /** 通用错误（如 JSON 解析失败、ctx 异常）。 */
    public static PathResult error(ToolResult<String> e) {
        return new PathResult(null, e, null);
    }

    /** sandbox policy 拒绝（携带结构化 denial info）。 */
    public static PathResult denied(
            FsDenialKind kind, SandboxMode currentMode, SandboxMode suggestedMode, String marker) {
        ToolResult<String> err = ToolResult.<String>error("[sandbox: " + kind.name().toLowerCase().replace('_', '-')
                + " under " + currentMode.wireValue() + " mode] " + marker);
        return new PathResult(null, err, new Denial(kind, currentMode, suggestedMode, marker));
    }
}

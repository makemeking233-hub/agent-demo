package com.example.agent.tools.file;

import com.example.agent.permission.PermissionDecision;
import com.example.agent.tools.AbstractFileTool;
import com.example.agent.tools.PathGuard;
import com.example.agent.tools.Tool.ToolContext;
import com.example.agent.tools.ToolCategory;
import com.example.agent.tools.ToolResult;

import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 列出目录下文件与子目录。
 *
 * <p>权限：默认 allow；路径含 {@code ..} 一律 deny。
 *
 * <p>路径 normalize + 越界检查继承自 {@link AbstractFileTool}，本类仅实现 {@link #doExecute}。 输入 {@code path} 为
 * {@code null}/空 时，{@link AbstractFileTool} 的 resolve 已回退到 {@code workingDirectory}。
 */
public class LsTool extends AbstractFileTool<LsTool.Input> {
    /**
     * Ls 工具输入。
     *
     * @param path 目录相对路径（{@code null} 或空表示当前目录）
     */
    public record Input(String path) implements ToolInput {
    }

    @Override
    public String name() {
        return "Ls";
    }

    @Override
    public String description() {
        return "列出目录下文件与子目录";
    }

    @Override
    protected Class<Input> inputClass() {
        return Input.class;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type",
                "object",
                "properties",
                Map.of("path", Map.of("type", "string")),
                "required",
                List.of("path"));
    }

    @Override
    public boolean isReadOnly(Input i) {
        return true;
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.READ;
    }

    @Override
    public PermissionDecision checkPermissions(Input i, ToolContext ctx) {
        PermissionDecision guard = PathGuard.denyIfTraversal(i.path());
        return guard != null ? guard : PermissionDecision.allow();
    }

    @Override
    public String renderUse(Input i) {
        return "Ls(" + i.path() + ")";
    }

    @Override
    public String renderResult(String s) {
        return s;
    }

    @Override
    protected Mono<ToolResult<String>> doExecute(Input input, Path p, ToolContext ctx) {
        return Mono.fromCallable(
                () -> {
                    try (var stream = Files.list(p)) {
                        String listing =
                                stream.sorted()
                                        .map(
                                                path ->
                                                        (Files.isDirectory(path) ? "[D] " : "[F] ")
                                                                + path.getFileName())
                                        .collect(Collectors.joining("\n"));
                        return ToolResult.ok(listing.isEmpty() ? "(空目录)" : listing, "<auto>");
                    } catch (Exception e) {
                        log.warn("列出目录失败: {}", p, e);
                        return ToolResult.<String>error("列出失败: " + e.getMessage());
                    }
                });
    }
}

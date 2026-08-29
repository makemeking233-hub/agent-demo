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

/**
 * 写入文件（覆盖）。
 *
 * <p>权限：默认 ask；路径含 {@code ..} 一律 deny。 父目录不存在时自动创建。
 *
 * <p>路径 normalize + 越界检查继承自 {@link AbstractFileTool}，本类仅实现 {@link #doExecute}。
 */
public class WriteFileTool extends AbstractFileTool<WriteFileTool.Input> {
    /**
     * WriteFile 工具输入。
     *
     * @param path 文件相对路径
     * @param content 写入内容
     */
    public record Input(String path, String content) implements ToolInput {}

    @Override
    public String name() {
        return "WriteFile";
    }

    @Override
    public String description() {
        return "写入文件（覆盖，父目录自动创建）";
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
                Map.of(
                        "path", Map.of("type", "string"),
                        "content", Map.of("type", "string")),
                "required",
                List.of("path", "content"));
    }

    @Override
    public boolean isDestructive(Input i) {
        return true;
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.WRITE;
    }

    @Override
    public PermissionDecision checkPermissions(Input i, ToolContext ctx) {
        PermissionDecision guard = PathGuard.denyIfTraversal(i.path());
        return guard != null ? guard : PermissionDecision.ask();
    }

    @Override
    public String renderUse(Input i) {
        return "WriteFile(" + i.path() + ")";
    }

    @Override
    public String renderResult(String s) {
        return s;
    }

    @Override
    protected Mono<ToolResult<String>> doExecute(Input input, Path p, ToolContext ctx) {
        return Mono.fromCallable(
                () -> {
                    try {
                        if (p.getParent() != null) Files.createDirectories(p.getParent());
                        Files.writeString(p, input.content());
                        return ToolResult.ok("已写入 " + p);
                    } catch (Exception e) {
                        log.warn("写入文件失败: {}", p, e);
                        return ToolResult.<String>error("写入失败: " + e.getMessage());
                    }
                });
    }
}

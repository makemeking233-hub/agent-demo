package com.example.agent.tools;

import com.example.agent.permission.PermissionDecision;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 写入文件（覆盖）。
 *
 * <p>权限：默认 ask；路径含 {@code ..} 一律 deny。
 * 父目录不存在时自动创建。
 */
public class WriteFileTool implements Tool<WriteFileTool.Input, String> {
    public record Input(String path, String content) {}

    @Override public String name() { return "WriteFile"; }
    @Override public String description() { return "写入文件（覆盖，父目录自动创建）"; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "properties", Map.of(
                "path", Map.of("type", "string"),
                "content", Map.of("type", "string")),
            "required", List.of("path", "content"));
    }

    @Override public boolean isDestructive(Input i) { return true; }

    @Override
    public PermissionDecision checkPermissions(Input i, ToolContext ctx) {
        if (i.path() == null || i.path().contains("..")) return PermissionDecision.deny();
        return PermissionDecision.ask();
    }

    @Override public String renderUse(Input i) { return "WriteFile(" + i.path() + ")"; }
    @Override public String renderResult(String s) { return s; }

    @Override
    public Mono<ToolResult<String>> execute(Input input, ToolContext ctx) {
        return Mono.fromCallable(() -> {
            Path base = ctx.workingDirectory();
            Path p = base.resolve(input.path()).normalize();
            if (!p.startsWith(base)) {
                return ToolResult.<String>error("路径越界: " + input.path());
            }
            try {
                if (p.getParent() != null) Files.createDirectories(p.getParent());
                Files.writeString(p, input.content());
                return ToolResult.ok("已写入 " + p, "<auto>");
            } catch (Exception e) {
                return ToolResult.<String>error("写入失败: " + e.getMessage());
            }
        });
    }
}
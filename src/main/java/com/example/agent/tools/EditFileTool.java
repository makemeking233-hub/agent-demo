package com.example.agent.tools;

import com.example.agent.permission.PermissionDecision;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 字符串替换编辑（oldText 必须精确匹配且唯一出现）。
 *
 * <p>权限：默认 ask。多次匹配时报错（避免歧义替换，详见 test-design.md R11 决议）。
 * v0.1 无原子写——失败时可能留下部分写入的文件。
 */
public class EditFileTool implements Tool<EditFileTool.Input, String> {
    public record Input(String path, String oldText, String newText) {}

    @Override public String name() { return "EditFile"; }
    @Override public String description() { return "字符串替换编辑（oldText 必须精确且唯一）"; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "properties", Map.of(
                "path", Map.of("type", "string"),
                "oldText", Map.of("type", "string"),
                "newText", Map.of("type", "string")),
            "required", List.of("path", "oldText", "newText"));
    }

    @Override public boolean isDestructive(Input i) { return true; }

    @Override
    public PermissionDecision checkPermissions(Input i, ToolContext ctx) {
        return PermissionDecision.ask();
    }

    @Override public String renderUse(Input i) {
        return "EditFile(" + i.path() + ", oldLen=" + (i.oldText() == null ? 0 : i.oldText().length()) + ")";
    }
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
                String content = Files.readString(p, StandardCharsets.UTF_8);
                int firstIdx = content.indexOf(input.oldText());
                if (firstIdx < 0) {
                    return ToolResult.<String>error("未找到 oldText");
                }
                int lastIdx = content.lastIndexOf(input.oldText());
                if (firstIdx != lastIdx) {
                    return ToolResult.<String>error("found " + (content.split(java.util.regex.Pattern.quote(input.oldText()), -1).length - 1)
                        + " matches, expected 1");
                }
                String updated = content.substring(0, firstIdx) + input.newText()
                    + content.substring(firstIdx + input.oldText().length());
                Files.writeString(p, updated);
                return ToolResult.ok("已编辑 " + p, "<auto>");
            } catch (Exception e) {
                return ToolResult.<String>error("编辑失败: " + e.getMessage());
            }
        });
    }
}
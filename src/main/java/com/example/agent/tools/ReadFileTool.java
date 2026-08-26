package com.example.agent.tools;

import com.example.agent.permission.PermissionDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 读取文件内容（UTF-8 优先，失败回退 GBK；详见 design.md §17.2 三重防御第 3 层）。
 *
 * <p>权限：默认 allow；路径含 {@code ..} 一律 deny（防止路径越界）。
 */
public class ReadFileTool implements Tool<ReadFileTool.Input, String> {
    private static final Logger log = LoggerFactory.getLogger(ReadFileTool.class);

    public record Input(String path) {}

    @Override public String name() { return "ReadFile"; }
    @Override public String description() { return "读取文件内容（UTF-8/GBK 自动识别）"; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "properties", Map.of("path", Map.of("type", "string")),
            "required", List.of("path"));
    }

    @Override public boolean isReadOnly(Input i) { return true; }

    @Override
    public PermissionDecision checkPermissions(Input i, ToolContext ctx) {
        if (i.path() == null || i.path().contains("..")) return PermissionDecision.deny();
        return PermissionDecision.allow();
    }

    @Override public String renderUse(Input i) { return "ReadFile(" + i.path() + ")"; }
    @Override public String renderResult(String s) {
        return s.length() > 100 ? s.substring(0, 100) + "..." : s;
    }

    @Override
    public Mono<ToolResult<String>> execute(Input input, ToolContext ctx) {
        return Mono.fromCallable(() -> {
            Path base = ctx.workingDirectory();
            Path p = base.resolve(input.path()).normalize();
            if (!p.startsWith(base)) {
                return ToolResult.<String>error("路径越界: " + input.path());
            }
            try {
                byte[] bytes = Files.readAllBytes(p);
                return ToolResult.ok(new String(bytes, StandardCharsets.UTF_8), "<auto>");
            } catch (MalformedInputException e) {
                try {
                    return ToolResult.ok(
                        new String(Files.readAllBytes(p), java.nio.charset.Charset.forName("GBK")),
                        "<auto>");
                } catch (IOException ex) {
                    log.warn("UTF-8/GBK 都失败: {}", p, ex);
                    return ToolResult.<String>error("UTF-8/GBK 都失败: " + ex.getMessage());
                }
            } catch (IOException e) {
                log.warn("读取文件失败: {}", p, e);
                return ToolResult.<String>error("读取失败: " + e.getMessage());
            }
        });
    }
}
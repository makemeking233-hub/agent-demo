package com.example.agent.tools.file;

import com.example.agent.permission.PermissionDecision;
import com.example.agent.tools.AbstractFileTool;
import com.example.agent.tools.Tool.ToolContext;
import com.example.agent.tools.ToolCategory;
import com.example.agent.tools.ToolResult;

import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/**
 * 字符串替换编辑（oldText 必须精确匹配且唯一出现）。
 *
 * <p>权限：默认 ask。多次匹配时报错（避免歧义替换，详见 test-design.md R11 决议）。
 *
 * <p>v0.2 起改为原子写：先写到 {@code target.tmp}，再 {@code Files.move} 覆盖原文件。 失败时原文件保持不变（要么旧版本，要么新版本，不会半截写入）。
 *
 * <p>路径 normalize + 越界检查继承自 {@link AbstractFileTool}，本类仅实现 {@link #doExecute}。
 */
public class EditFileTool extends AbstractFileTool<EditFileTool.Input> {
    /**
     * EditFile 工具输入。
     *
     * @param path 目标文件相对路径
     * @param oldText 待替换字符串（必须精确且唯一）
     * @param newText 新字符串
     */
    public record Input(String path, String oldText, String newText) implements ToolInput {}

    @Override
    public String name() {
        return "EditFile";
    }

    @Override
    public String description() {
        return "字符串替换编辑（oldText 必须精确且唯一；原子写）";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                        "path", Map.of("type", "string"),
                        "oldText", Map.of("type", "string"),
                        "newText", Map.of("type", "string")),
                "required",
                List.of("path", "oldText", "newText"));
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
        return PermissionDecision.ask();
    }

    @Override
    public String renderUse(Input i) {
        return "EditFile("
                + i.path()
                + ", oldLen="
                + (i.oldText() == null ? 0 : i.oldText().length())
                + ")";
    }

    @Override
    public String renderResult(String s) {
        return s;
    }

    @Override
    protected Mono<ToolResult<String>> doExecute(Input input, Path target, ToolContext ctx) {
        return Mono.fromCallable(
                () -> {
                    Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
                    try {
                        String content = Files.readString(target, StandardCharsets.UTF_8);
                        int firstIdx = content.indexOf(input.oldText());
                        if (firstIdx < 0) {
                            return ToolResult.<String>error("未找到 oldText");
                        }
                        int lastIdx = content.lastIndexOf(input.oldText());
                        if (firstIdx != lastIdx) {
                            int matches =
                                    content.split(
                                                            java.util.regex.Pattern.quote(
                                                                    input.oldText()),
                                                            -1)
                                                    .length
                                            - 1;
                            return ToolResult.<String>error(
                                    "found " + matches + " matches, expected 1");
                        }
                        String updated =
                                content.substring(0, firstIdx)
                                        + input.newText()
                                        + content.substring(firstIdx + input.oldText().length());

                        // 原子写：先写 .tmp，再 move 覆盖
                        Files.writeString(tmp, updated, StandardCharsets.UTF_8);
                        Files.move(
                                tmp,
                                target,
                                StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE);
                        return ToolResult.ok("已编辑 " + target, "<auto>");
                    } catch (Exception e) {
                        log.warn("编辑文件失败: {}", target, e);
                        // 清理残留 .tmp（清理失败不影响主流程）
                        try {
                            Files.deleteIfExists(tmp);
                        } catch (Exception ignored) {
                            /* 清理失败容忍 */
                        }
                        return ToolResult.<String>error("编辑失败: " + e.getMessage());
                    }
                });
    }
}

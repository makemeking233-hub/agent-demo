package com.example.agent.tools;

import com.example.agent.permission.PermissionDecision;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 列出目录下文件与子目录。
 *
 * <p>权限：默认 allow；路径含 {@code ..} 一律 deny。
 */
public class LsTool implements Tool<LsTool.Input, String> {
  private static final Logger log = LoggerFactory.getLogger(LsTool.class);

  /**
   * Ls 工具输入。
   *
   * @param path 目录相对路径（{@code null} 或空表示当前目录）
   */
  public record Input(String path) {}

  @Override
  public String name() {
    return "Ls";
  }

  @Override
  public String description() {
    return "列出目录下文件与子目录";
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
  public PermissionDecision checkPermissions(Input i, ToolContext ctx) {
    if (i.path() != null && i.path().contains("..")) return PermissionDecision.deny();
    return PermissionDecision.allow();
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
  public Mono<ToolResult<String>> execute(Input input, ToolContext ctx) {
    return Mono.fromCallable(
        () -> {
          Path base = ctx.workingDirectory();
          Path p =
              (input.path() == null || input.path().isEmpty())
                  ? base
                  : base.resolve(input.path()).normalize();
          if (!p.startsWith(base)) {
            return ToolResult.<String>error("路径越界");
          }
          try (var stream = Files.list(p)) {
            String listing =
                stream
                    .sorted()
                    .map(path -> (Files.isDirectory(path) ? "[D] " : "[F] ") + path.getFileName())
                    .collect(Collectors.joining("\n"));
            return ToolResult.ok(listing.isEmpty() ? "(空目录)" : listing, "<auto>");
          } catch (Exception e) {
            log.warn("列出目录失败: {}", p, e);
            return ToolResult.<String>error("列出失败: " + e.getMessage());
          }
        });
  }
}

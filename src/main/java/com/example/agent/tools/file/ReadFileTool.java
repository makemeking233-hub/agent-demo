package com.example.agent.tools.file;

import com.example.agent.permission.PermissionDecision;
import com.example.agent.tools.AbstractFileTool;
import com.example.agent.tools.PathGuard;
import com.example.agent.tools.Tool.ToolContext;
import com.example.agent.tools.ToolCategory;

import com.example.agent.tools.ToolResult;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * 读取文件内容（UTF-8 优先，失败回退 GBK；详见 design.md §17.2 三重防御第 3 层）。
 *
 * <p>权限：默认 allow；路径含 {@code ..} 一律 deny（防止路径越界）。
 *
 * <p>路径 normalize + 越界检查继承自 {@link AbstractFileTool}，本类仅实现 {@link #doExecute}。
 */
public class ReadFileTool extends AbstractFileTool<ReadFileTool.Input> {
  /**
   * ReadFile 工具输入。
   *
   * @param path 文件相对路径
   */
  public record Input(String path) implements ToolInput {}

  @Override
  public String name() {
    return "ReadFile";
  }

  @Override
  public String description() {
    return "读取文件内容（UTF-8/GBK 自动识别）";
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
    return "ReadFile(" + i.path() + ")";
  }

  @Override
  public String renderResult(String s) {
    return s.length() > 100 ? s.substring(0, 100) + "..." : s;
  }

  @Override
  protected Mono<ToolResult<String>> doExecute(Input input, Path p, ToolContext ctx) {
    return Mono.fromCallable(
        () -> {
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

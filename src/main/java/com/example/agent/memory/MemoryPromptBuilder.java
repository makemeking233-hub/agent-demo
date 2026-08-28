package com.example.agent.memory;

import java.io.IOException;
import java.nio.file.Files;

/**
 * 把 Memory 拼到 system prompt（详见 design.md §5.4）。
 *
 * <p>v0.1 简化：只拼 MEMORY.md 索引内容到 system prompt；不注入文件级内容。
 *
 * <p>模板从 {@code /prompts/memory-system.txt} 加载，含三个占位符：{@code {memoryDir}} / {@code
 * {extraGuidelines}} / {@code {indexContent}}。
 */
public class MemoryPromptBuilder {
  /** 模板资源路径（classpath） */
  private static final String TEMPLATE_PATH = "/prompts/memory-system.txt";

  /** memory 目录管理器（用于读 MEMORY.md 索引） */
  private final MemoryDir dir;

  /**
   * 构造 memory prompt 构建器。
   *
   * @param dir memory 目录管理器
   */
  public MemoryPromptBuilder(MemoryDir dir) {
    this.dir = dir;
  }

  /**
   * 构造 memory 部分的 system prompt。
   *
   * @param extraGuidelines 额外附加的 memory 指引（可空）
   * @return 完整 system prompt 片段
   */
  public String build(String extraGuidelines) {
    String indexContent = readIndex();
    String indexSection =
        indexContent.isEmpty() ? "Your MEMORY.md is currently empty." : indexContent;
    String extra = extraGuidelines != null && !extraGuidelines.isBlank() ? extraGuidelines : "";
    String template = loadTemplate();
    return template
        .replace("{memoryDir}", dir.dir().toString())
        .replace("{extraGuidelines}", extra)
        .replace("{indexContent}", indexSection);
  }

  /**
   * 从 classpath 加载 memory prompt 模板（缺失时回退到内置最小模板）。
   *
   * @return 模板字符串
   */
  private String loadTemplate() {
    return com.example.agent.util.PromptLoader.loadOrFallback(
        TEMPLATE_PATH, "# Persistent Agent Memory\n{memoryDir}\n{indexContent}");
  }

  /**
   * 读取 MEMORY.md 索引内容（不存在或读取失败返回空串）。
   *
   * @return 索引文本
   */
  private String readIndex() {
    try {
      if (Files.notExists(dir.indexFile())) return "";
      return dir.truncateIndex(Files.readString(dir.indexFile()));
    } catch (IOException e) {
      return "";
    }
  }
}

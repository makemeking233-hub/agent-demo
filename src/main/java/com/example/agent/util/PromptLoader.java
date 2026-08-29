package com.example.agent.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Prompt 模板加载工具（统一处理 classpath 资源 + fallback 字符串）。
 *
 * <p>v0.1 之前：ContextCompressor 和 MemoryPromptBuilder 各自重复了 "open resource / readAllBytes / UTF-8 /
 * 失败回退" 模式。
 *
 * <p>使用：
 *
 * <pre>{@code
 * String tpl = PromptLoader.loadOrFallback("/prompts/foo.txt", "# Fallback");
 * }</pre>
 */
public final class PromptLoader {
  /**
   * 从 classpath 加载文本资源；缺失或 IO 异常时返回 fallback。
   *
   * @param classpathPath 资源路径（含前导 {@code /}）
   * @param fallback 缺失/失败时的兜底字符串（不可空）
   * @param <T> 调用方类型（用于链式）
   * @return 加载到的文本；fallback 在异常路径下返回
   */
  public static String loadOrFallback(String classpathPath, String fallback) {
    ClassLoader cl = PromptLoader.class.getClassLoader();
    try (InputStream in = cl.getResourceAsStream(classpathPath)) {
      if (in == null) return fallback;
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      return fallback;
    }
  }

  private PromptLoader() {}
}

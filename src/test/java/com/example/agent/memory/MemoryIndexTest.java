package com.example.agent.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MemoryIndexTest {
  @TempDir Path tmp;

  @Test
  void parsesEntries() throws Exception {
    Path file = tmp.resolve("MEMORY.md");
    Files.writeString(
        file,
        "# Memory Index\n\n"
            + "- [Java 17 安装](java17.md) — JDK 安装步骤\n"
            + "- [Python 装饰器](py.md) — wraps 用法\n");
    var index = new MemoryIndex(file);
    var entries = index.parse();
    assertEquals(2, entries.size());
    assertEquals("Java 17 安装", entries.get(0).title());
    assertEquals("java17.md", entries.get(0).filename());
  }

  @Test
  void writesEntries() throws Exception {
    Path file = tmp.resolve("MEMORY.md");
    var index = new MemoryIndex(file);
    index.write(List.of(new MemoryEntry("Test", "测试", "test.md")));
    assertTrue(Files.exists(file));
    String content = Files.readString(file);
    assertTrue(content.contains("[Test](test.md)"));
  }
}

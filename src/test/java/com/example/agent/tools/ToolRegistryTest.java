package com.example.agent.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.example.agent.tools.file.LsTool;
import com.example.agent.tools.file.ReadFileTool;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {
  @Test
  void registersAndRetrieves() {
    var reg = new ToolRegistry();
    var t = new ReadFileTool();
    reg.register(t);
    assertSame(t, reg.get("ReadFile"));
    assertEquals(1, reg.list().size());
  }

  @Test
  void supportsMultipleRegistrations() {
    var reg = new ToolRegistry();
    reg.register(new ReadFileTool());
    reg.register(new LsTool());
    assertEquals(2, reg.list().size());
    assertEquals(2, List.copyOf(reg.list()).size());
  }

  @Test
  void registerMemoryToolsAddsThree() {
    var reg = new ToolRegistry();
    ToolRegistry.registerMemoryTools(reg);
    var names = reg.list().stream().map(t -> t.name()).toList();
    assertEquals(3, names.size());
    assertEquals(true, names.contains("ReadFile"));
    assertEquals(true, names.contains("WriteFile"));
    assertEquals(true, names.contains("EditFile"));
  }
}

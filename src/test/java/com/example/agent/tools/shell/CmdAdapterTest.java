package com.example.agent.tools.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CmdAdapterTest {
  @Test
  void commandLineFormat() {
    assertEquals(List.of("cmd.exe", "/c", "dir"), new CmdAdapter().commandLine("dir"));
  }

  @Test
  void denylistIncludesFormat() {
    assertTrue(new CmdAdapter().isDenylisted("format C:"));
  }

  @Test
  void denylistIncludesRmdir() {
    assertTrue(new CmdAdapter().isDenylisted("rmdir /s /q C:\\foo"));
  }
}

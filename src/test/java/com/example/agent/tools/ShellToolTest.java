package com.example.agent.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.permission.PermissionManager;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

class ShellToolTest {
  @TempDir Path tmp;

  private Tool.ToolContext ctx() {
    return new Tool.ToolContext(tmp, new PermissionManager(), () -> false);
  }

  @Test
  void deniesBlacklistedCommand() {
    var tool = new ShellTool(new CmdAdapter(), 5, 10000, false);
    StepVerifier.create(tool.execute(new ShellTool.Input("format C: /q"), ctx()))
        .assertNext(r -> assertTrue(r.isError()))
        .verifyComplete();
  }

  @Test
  void allowsNormalCommand() {
    var tool = new ShellTool(new CmdAdapter(), 5, 10000, false);
    StepVerifier.create(tool.execute(new ShellTool.Input("echo hello"), ctx()))
        .assertNext(
            r -> {
              assertFalse(r.isError());
              assertTrue(r.output().contains("hello"));
            })
        .verifyComplete();
  }

  @Test
  void blacklistedTakesPrecedenceOverSuccess() {
    // 黑名单命中时即使命令"看似无害"也拒绝
    var tool = new ShellTool(new CmdAdapter(), 5, 10000, false);
    StepVerifier.create(tool.execute(new ShellTool.Input("del /f /s /q C:\\foo"), ctx()))
        .assertNext(r -> assertTrue(r.isError()))
        .verifyComplete();
  }
}

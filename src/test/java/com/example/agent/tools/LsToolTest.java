package com.example.agent.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.permission.PermissionManager;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

class LsToolTest {
  @TempDir Path tmp;

  private Tool.ToolContext ctx() {
    return new Tool.ToolContext(tmp, new PermissionManager(), () -> false);
  }

  @Test
  void listsFiles() throws Exception {
    Files.writeString(tmp.resolve("a.txt"), "x");
    Files.createDirectory(tmp.resolve("sub"));
    var tool = new LsTool();
    StepVerifier.create(tool.execute(new LsTool.Input("."), ctx()))
        .assertNext(
            r -> {
              assertFalse(r.isError());
              assertTrue(r.output().contains("a.txt"));
              assertTrue(r.output().contains("[D] sub"));
            })
        .verifyComplete();
  }

  @Test
  void emptyDirShowsMessage() {
    var tool = new LsTool();
    StepVerifier.create(tool.execute(new LsTool.Input("."), ctx()))
        .assertNext(
            r -> {
              assertFalse(r.isError());
              assertTrue(r.output().contains("空目录") || r.output().isEmpty());
            })
        .verifyComplete();
  }

  @Test
  void rejectsPathTraversal() {
    var tool = new LsTool();
    StepVerifier.create(tool.execute(new LsTool.Input(".."), ctx()))
        .assertNext(r -> assertTrue(r.isError()))
        .verifyComplete();
  }
}

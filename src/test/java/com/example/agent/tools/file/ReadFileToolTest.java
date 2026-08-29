package com.example.agent.tools.file;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.permission.PermissionManager;
import com.example.agent.tools.Tool;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

class ReadFileToolTest {
  @TempDir Path tmp;

  private Tool.ToolContext ctx() {
    return new Tool.ToolContext(tmp, new PermissionManager(), () -> false);
  }

  @Test
  void readsUtf8File() throws Exception {
    Files.writeString(tmp.resolve("a.txt"), "你好，世界", StandardCharsets.UTF_8);
    var tool = new ReadFileTool();
    StepVerifier.create(tool.execute(new ReadFileTool.Input("a.txt"), ctx()))
        .assertNext(
            r -> {
              assertFalse(r.isError());
              assertTrue(r.output().contains("你好，世界"));
            })
        .verifyComplete();
  }

  @Test
  void fallsBackToGbk() throws Exception {
    // 用 ISO-8859-1 字节序列（保证 UTF-8 解码一定抛 MalformedInputException，触发 GBK 回退）
    Files.write(tmp.resolve("b.txt"), "hello".getBytes(StandardCharsets.ISO_8859_1));
    var tool = new ReadFileTool();
    StepVerifier.create(tool.execute(new ReadFileTool.Input("b.txt"), ctx()))
        .assertNext(
            r -> {
              assertFalse(r.isError());
              // 回退到 GBK 后能成功解码，输出非空
              assertTrue(r.output().length() > 0);
            })
        .verifyComplete();
  }

  @Test
  void rejectsPathTraversal() {
    var tool = new ReadFileTool();
    StepVerifier.create(tool.execute(new ReadFileTool.Input("../escape.txt"), ctx()))
        .assertNext(r -> assertTrue(r.isError()))
        .verifyComplete();
  }

  @Test
  void fileNotFound() {
    var tool = new ReadFileTool();
    StepVerifier.create(tool.execute(new ReadFileTool.Input("missing.txt"), ctx()))
        .assertNext(r -> assertTrue(r.isError()))
        .verifyComplete();
  }
}

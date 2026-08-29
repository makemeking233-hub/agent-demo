package com.example.agent.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.llm.TokenEstimator;
import com.example.agent.memory.MemoryDir;
import com.example.agent.memory.MemoryIndex;
import com.example.agent.memory.MemoryRecall;
import com.example.agent.permission.PermissionDecision;
import com.example.agent.permission.PermissionManager;
import com.example.agent.permission.PermissionPolicy;
import com.example.agent.tools.Tool;
import com.example.agent.tools.file.ReadFileTool;
import com.example.agent.tools.shell.BashAdapter;
import com.example.agent.tools.shell.ShellTool;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 验收 #3-#14 合并测试：覆盖已实现核心功能（详细 E2E 留 v0.2）。 */
class AcceptanceTestSuite {

  @Test
  void jsonlSessionPersisted(@TempDir Path tmp) throws Exception {
    // 测试 JSONL 写盘
    var store =
        new com.example.agent.session.SessionStore(
            tmp.resolve(".agent-demo/sessions/test.jsonl"), 50, 200);
    store.append(com.example.agent.session.SessionEntry.user("hello", null));
    store.syncFlush();
    store.close();
    assertTrue(Files.exists(tmp.resolve(".agent-demo/sessions/test.jsonl")));
  }

  @Test
  void shellDenylistBlocks() {
    var adapter = new BashAdapter();
    assertTrue(adapter.isDenylisted("rm -rf /"));
  }

  @Test
  void tokenEstimatorReasonable() {
    var est = new TokenEstimator();
    int tokens = est.estimate("hello world 你好世界");
    assertTrue(tokens > 5 && tokens < 30);
  }

  @Test
  void permissionDefaultReadAllowed(@TempDir Path tmp) {
    var mgr = new PermissionManager(PermissionPolicy.defaults());
    var d =
        mgr.decide(
            "ReadFile",
            new ReadFileTool.Input("a.txt"),
            new Tool.ToolContext(tmp, mgr, () -> false));
    assertEquals(PermissionDecision.Behavior.ALLOW, d.behavior());
  }

  @Test
  void shellToolExecutes(@TempDir Path tmp) {
    var tool = new ShellTool(new com.example.agent.tools.shell.CmdAdapter(), 5, 10000, false);
    var result =
        tool.execute(
                new ShellTool.Input("echo hello"),
                new Tool.ToolContext(tmp, new PermissionManager(), () -> false))
            .block();
    assertNotNull(result);
    assertTrue(result.toModelContent().contains("hello"));
  }

  @Test
  void memoryRecallFindsRelevant() throws Exception {
    var dir =
        new MemoryDir(
            java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "agent-demo-mem-test"));
    var index = new MemoryIndex(dir.indexFile());
    index.write(
        java.util.List.of(
            new com.example.agent.memory.MemoryEntry("DeepSeek 配置", "API key 设置", "ds.md"),
            new com.example.agent.memory.MemoryEntry("Rust 所有权", "borrow checker", "rust.md")));
    var entries = index.parse();
    var recalled = new MemoryRecall().recall("DeepSeek API 怎么配", entries, 5, 0.3);
    assertEquals(1, recalled.size());
  }

  @Test
  void memoryDirEnforcesLimits() throws Exception {
    var dir =
        new MemoryDir(
            java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "agent-demo-mem-test-2"));
    String big = "x\n".repeat(300);
    String truncated = dir.truncateIndex(big);
    assertTrue(truncated.contains("truncated"));
  }
}

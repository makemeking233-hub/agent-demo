package com.example.agent.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.core.Message;
import com.example.agent.core.MessageHistory;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.session.SessionEntry;
import com.example.agent.session.SessionStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

class SlashCommandTest {
  private final SlashCommand cmd = new SlashCommand();
  @TempDir Path tmp;

  @Test
  void ignoresNonSlash() {
    var hist = new MessageHistory(new TokenEstimator());
    int[] p = {0}, c = {0};
    assertFalse(cmd.dispatch("hello", hist, p, c, "deepseek-chat", () -> {}));
  }

  @Test
  void triggersClearCallback() {
    var hist = new MessageHistory(new TokenEstimator());
    int[] p = {0}, c = {0};
    boolean[] cleared = {false};
    cmd.dispatch("/clear", hist, p, c, "deepseek-chat", () -> cleared[0] = true);
    assertTrue(cleared[0]);
  }

  @Test
  void completesCommands() {
    assertEquals(List.of("/help", "/history"), cmd.complete("/h"));
  }

  @Test
  void estimateCost() {
    assertEquals(0, cmd.estimateCost(0, 0, "deepseek-chat"));
    // 1M input + 1M output = 2 + 8 = 10 元
    assertEquals(10, cmd.estimateCost(1_000_000, 1_000_000, "deepseek-chat"));
  }

  @Test
  void resumeWithNoSessionDirTriggersCallbackWithEmpty() {
    var hist = new MessageHistory(new TokenEstimator());
    int[] p = {0}, c = {0};
    AtomicReference<List<Message>> resumed = new AtomicReference<>(List.of());
    Consumer<List<Message>> onResume = resumed::set;
    // sessionsDir 不存在 → 触发 onResume([])（空 list，不报错）
    cmd.dispatch(
        "/resume", hist, p, c, "deepseek-chat", () -> {}, tmp.resolve("no-such-dir"), onResume);
    assertTrue(resumed.get().isEmpty());
  }

  @Test
  void resumeWithExistingSessionTriggersCallbackWithEntries() throws Exception {
    // 准备 sessions 目录 + 一个 jsonl
    Path sessionsDir = tmp.resolve("sessions");
    Files.createDirectories(sessionsDir);
    Path sessionFile = sessionsDir.resolve("test.jsonl");
    SessionStore store = new SessionStore(sessionFile, 50, 60_000);
    store.append(SessionEntry.user("hello", null));
    store.append(SessionEntry.assistant("hi", List.of(), null));
    store.syncFlush();
    store.close();

    var hist = new MessageHistory(new TokenEstimator());
    int[] p = {0}, c = {0};
    AtomicReference<List<Message>> resumed = new AtomicReference<>();
    cmd.dispatch(
        "/resume", hist, p, c, "deepseek-chat", () -> {}, sessionsDir, resumed::set);
    assertEquals(2, resumed.get().size());
    assertEquals("hello", resumed.get().get(0).content());
    assertEquals("hi", resumed.get().get(1).content());
  }
}
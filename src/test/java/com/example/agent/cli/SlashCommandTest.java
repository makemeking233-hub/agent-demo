package com.example.agent.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.core.MessageHistory;
import com.example.agent.llm.TokenEstimator;
import java.util.List;
import org.junit.jupiter.api.Test;

class SlashCommandTest {
  private final SlashCommand cmd = new SlashCommand();

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
}

package com.example.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;

class AgentCliTest {
  @Test
  void helpExitsZero() {
    int result = new CommandLine(new AgentCli(null)).execute("--help");
    assertEquals(0, result);
  }

  @Test
  void initSubcommandParses() {
    var cli = new CommandLine(new AgentCli(null));
    cli.parseArgs("init");
    // 不抛异常即可
  }
}
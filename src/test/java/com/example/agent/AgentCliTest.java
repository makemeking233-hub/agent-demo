package com.example.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class AgentCliTest {
  @Test
  void helpExitsZero() {
    int result = new CommandLine(new AgentCli()).execute("--help");
    assertEquals(0, result);
  }

  @Test
  void initSubcommandParses() {
    var cli = new CommandLine(new AgentCli());
    cli.parseArgs("init"); // init subcommand 接受，无 --help 因为 mixinStandardHelpOptions 未在子命令上
    // 不抛异常即可
  }
}

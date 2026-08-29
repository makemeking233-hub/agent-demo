package com.example.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.agent.cli.ChatCommand;
import com.example.agent.cli.InitCommand;
import org.junit.jupiter.api.Test;

import picocli.CommandLine;

/**
 * AgentCli 单元测试（不通过 Spring，直接测 picocli 派发）。
 *
 * <p>生产路径由 Spring {@code AgentCli.run} 自动 {@code addSubcommand(bean)}，见 {@link AgentCli#run(String...)}。
 */
class AgentCliTest {
  @Test
  void helpExitsZero() {
    int result =
        new CommandLine(new AgentCli())
            .addSubcommand(new ChatCommand())
            .addSubcommand(new InitCommand())
            .execute("--help");
    assertEquals(0, result);
  }

  @Test
  void initSubcommandParses() {
    var cli =
        new CommandLine(new AgentCli())
            .addSubcommand(new ChatCommand())
            .addSubcommand(new InitCommand());
    cli.parseArgs("init");
    // 不抛异常即可
  }
}
package com.example.agent.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(
    name = "chat",
    mixinStandardHelpOptions = true,
    description = "启动交互式 REPL，进入多轮对话")
public class ChatCommand implements Runnable {
    @Option(names = {"--model"}, description = "覆盖默认模型")
    String model;

    @Option(names = {"--api-key"}, description = "覆盖 API key（仅本次）")
    String apiKey;

    @Option(names = {"--system-prompt"}, description = "覆盖默认 system prompt")
    String systemPrompt;

    @Override
    public void run() {
        System.out.println("REPL 尚未实现（M0 脚手架）");
    }
}
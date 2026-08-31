package com.example.agent.plugin;

import com.example.agent.config.AgentConfig;
import com.example.agent.llm.LlmProvider;
import com.example.agent.cli.SlashCommand;
import com.example.agent.tools.ToolRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * Plugin 拿到的 DI 容器 (add-plugin-system v1.0).
 *
 * <p>Plugin 在 init 时通过 ctx 拿主 agent 提供的服务, 不依赖 Spring Bean.
 * 5 个 ExtensionPoint 容器都用 AtomicReference / UnaryOperator, 让多个 Plugin 串行写入 (后者覆盖前者).
 */
public record PluginContext(
        AgentConfig cfg,
        ToolRegistry tools,
        AtomicReference<List<LlmProvider>> providers,
        AtomicReference<List<SlashCommand>> slashCommands,
        AtomicReference<String> fragments,
        UnaryOperator<com.example.agent.llm.ChatRequest> requestMappers) {

    /** 短别名: 调 ctx.tools().register(tool) 即可. */
    public ToolRegistry tools() { return tools; }
}

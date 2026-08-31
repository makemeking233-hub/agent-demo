package com.example.agent.plugin;

import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.LlmProvider;
import com.example.agent.cli.SlashCommand;
import com.example.agent.config.AgentConfig;
import com.example.agent.tools.Tool;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * 5 个 ExtensionPoint marker interface (add-plugin-system v1.0).
 *
 * <p>Plugin 通过 implements 其中若干个声明自己提供的能力. PluginManager.init 完成后
 * AgentLoop 通过 instanceof 检测, 调对应钩子, 收集到 AgentLoop 自己的服务里.
 */
public final class ExtensionPoints {

    private ExtensionPoints() {}

    /** 工具: Plugin.init 时调 ctx.tools().register(tool) 全部. */
    public interface ToolProvider {
        default List<Tool<?, ?>> tools() { return List.of(); }
    }

    /** LLM Provider: Plugin.init 后由 AgentLoop 注册到 providers 列表. */
    public interface LlmProviderExtension {
        default LlmProvider provider() { return null; }
    }

    /** Slash 命令: Plugin.init 后由 AgentLoop 注入到 slashCommandRouter. */
    public interface SlashCommandProvider {
        default List<SlashCommand> commands() { return List.of(); }
    }

    /** System prompt 片段: Plugin.init 后由 AgentLoop 拼到 system prompt 尾部. */
    public interface SystemPromptFragment {
        default String fragment() { return ""; }
    }

    /** ChatRequest 构造修改器: 调 map(oldReq) 返新 req. PluginManager 按列表序链式调用. */
    public interface ChatRequestMapper {
        default ChatRequest map(ChatRequest req, AgentConfig cfg) {
            return req;
        }
    }
}

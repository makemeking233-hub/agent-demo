package com.example.agent.plugin;

import com.example.agent.cli.SlashCommand;
import com.example.agent.config.AgentConfig;
import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.LlmProvider;
import com.example.agent.tools.Tool;
import com.example.agent.tools.ToolRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plugin 生命周期管理 (add-plugin-system v1.0).
 *
 * <p>维护一组 Plugin, 按列表序 init, 反序 close. 单个 plugin 抛异常被隔离 (记 WARN,
 * 不影响其他 plugin). 重复 className 第二次 init 跳过.
 *
 * <p>构造时接收 AgentConfig (含 plugins 列表) + 已实例化的 Plugin 列表. 实例化逻辑
 * 由调用方负责 (AgentLoopFactory 或 ConfigLoader). PluginManager 负责 lifecycle.
 */
public class PluginManager {

    private static final Logger log = LoggerFactory.getLogger(PluginManager.class);

    private final List<Plugin> plugins;
    private final AgentConfig cfg;
    private final ToolRegistry tools;
    private final Set<String> seen = new HashSet<>();
    private PluginContext ctx;
    private boolean inited = false;

    public PluginManager(List<Plugin> plugins, AgentConfig cfg, ToolRegistry tools) {
        this.plugins = plugins;
        this.cfg = cfg;
        this.tools = tools;
    }

    /** 按列表序 init, 失败隔离. 第二次同名 plugin 跳过. */
    public void init() {
        if (inited) return;
        this.ctx = new PluginContext(
                cfg,
                tools,
                new AtomicReference<>(new ArrayList<>()),
                new AtomicReference<>(new ArrayList<>()),
                new AtomicReference<>(""),
                (UnaryOperator<ChatRequest>) r -> r);
        for (Plugin p : plugins) {
            if (!seen.add(p.name())) {
                log.warn("Plugin {} 重复, 跳过第二次 init", p.name());
                continue;
            }
            try {
                p.init(ctx);
            } catch (Exception e) {
                log.warn("Plugin {} init 失败, 隔离继续: {}", p.name(), e.toString());
            }
        }
        inited = true;
    }

    /** 反序 close, 失败隔离. */
    public void close() {
        if (!inited) return;
        for (int i = plugins.size() - 1; i >= 0; i--) {
            Plugin p = plugins.get(i);
            try {
                p.close();
            } catch (Exception e) {
                log.warn("Plugin {} close 失败, 隔离继续: {}", p.name(), e.toString());
            }
        }
        inited = false;
    }

    /** 收集所有 ToolProvider 注册的工具 (供 AgentLoop 在 init 后查). */
    @SuppressWarnings("unchecked")
    public List<Tool<?, ?>> collectTools() {
        List<Tool<?, ?>> out = new ArrayList<>();
        for (Plugin p : plugins) {
            if (p instanceof ExtensionPoints.ToolProvider tp) {
                out.addAll(tp.tools());
            }
        }
        return out;
    }

    /** 收集所有 SystemPromptFragment, 拼成单字符串 (空字符串自动跳过). */
    public String collectSystemPromptFragment() {
        StringBuilder sb = new StringBuilder();
        for (Plugin p : plugins) {
            if (p instanceof ExtensionPoints.SystemPromptFragment spf) {
                String f = spf.fragment();
                if (f != null && !f.isEmpty()) {
                    if (sb.length() > 0) sb.append("\n\n");
                    sb.append(f);
                }
            }
        }
        return sb.toString();
    }

    /** 收集所有 ChatRequestMapper, 按列表序. */
    public List<ExtensionPoints.ChatRequestMapper> collectRequestMappers() {
        List<ExtensionPoints.ChatRequestMapper> out = new ArrayList<>();
        for (Plugin p : plugins) {
            if (p instanceof ExtensionPoints.ChatRequestMapper crm) {
                out.add(crm);
            }
        }
        return out;
    }

    /** 收集所有 SlashCommand. */
    public List<SlashCommand> collectSlashCommands() {
        List<SlashCommand> out = new ArrayList<>();
        for (Plugin p : plugins) {
            if (p instanceof ExtensionPoints.SlashCommandProvider scp) {
                out.addAll(scp.commands());
            }
        }
        return out;
    }

    /** 收集所有 LlmProvider. */
    public List<LlmProvider> collectProviders() {
        List<LlmProvider> out = new ArrayList<>();
        for (Plugin p : plugins) {
            if (p instanceof ExtensionPoints.LlmProviderExtension lpe) {
                LlmProvider p2 = lpe.provider();
                if (p2 != null) out.add(p2);
            }
        }
        return out;
    }
}

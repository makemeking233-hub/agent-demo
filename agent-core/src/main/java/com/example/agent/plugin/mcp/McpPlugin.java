package com.example.agent.plugin.mcp;

import com.example.agent.config.AgentConfig;
import com.example.agent.mcp.McpClient;
import com.example.agent.mcp.McpTool;
import com.example.agent.plugin.ExtensionPoints;
import com.example.agent.plugin.Plugin;
import com.example.agent.plugin.PluginContext;
import com.example.agent.tools.Tool;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP 插件（add-plugin-system v1.0）：把 {@code cfg.mcp.servers()} 配置的 MCP server 包装为
 * {@code Plugin}, 在 init 阶段逐个 {@link McpClient#initialize()} 握手, tools() 返回包装的
 * {@link McpTool} 列表（工具名唯一化 = {@code serverName.toolName}）。
 *
 * <p>init 隔离：单个 server 握手失败只跳过该 server（记录 WARN, 不抛异常）, 不影响其他 server
 * 与主流程。
 *
 * <p>老 {@code ToolRegistry.registerMcpTools} 保留为 deprecated wrapper（见 T4.1）, 内部转发到
 * 本 Plugin 的 init + tools()。
 */
public class McpPlugin implements Plugin, ExtensionPoints.ToolProvider {

    private final List<McpClient> clients = new ArrayList<>();
    private final List<McpClient> ready = new ArrayList<>();

    /** 默认构造：init 时从 {@code cfg.mcp()} 构建客户端。 */
    public McpPlugin() {}

    /** 测试构造：注入现成客户端（跳过 cfg 构建）。 */
    public McpPlugin(List<McpClient> clients) {
        if (clients != null) this.clients.addAll(clients);
    }

    @Override
    public void init(PluginContext ctx) {
        if (clients.isEmpty()) {
            AgentConfig.Mcp mcp = ctx.cfg().mcp();
            if (mcp != null && mcp.servers() != null && !mcp.servers().isEmpty()) {
                for (AgentConfig.McpServer s : mcp.servers()) {
                    clients.add(McpClient.create(s.url(), s.name()));
                }
            }
        }
        for (McpClient c : clients) {
            if (c.initialize()) {
                ready.add(c);
            }
        }
    }

    @Override
    public List<Tool<?, ?>> tools() {
        List<Tool<?, ?>> out = new ArrayList<>();
        for (McpClient c : ready) {
            for (McpClient.ToolDescriptor d : c.listTools()) {
                out.add(new McpTool(c, d, c.name() + "." + d.name()));
            }
        }
        return out;
    }
}

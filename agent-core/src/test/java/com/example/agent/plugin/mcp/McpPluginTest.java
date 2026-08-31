package com.example.agent.plugin.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.agent.config.AgentConfig;
import com.example.agent.mcp.McpClient;
import com.example.agent.plugin.PluginManager;
import com.example.agent.tools.Tool;
import com.example.agent.tools.ToolRegistry;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class McpPluginTest {

    @Test
    void initIsolatesFailure_andUniquifiesToolName() {
        McpClient ok = mock(McpClient.class);
        when(ok.name()).thenReturn("srvA");
        when(ok.initialize()).thenReturn(true);
        when(ok.listTools())
                .thenReturn(List.of(new McpClient.ToolDescriptor("calc", "计算器", Map.of())));

        // 握手失败：initialize()=false → 不参与工具注册（隔离）
        McpClient bad = mock(McpClient.class);
        when(bad.name()).thenReturn("srvB");
        when(bad.initialize()).thenReturn(false);

        McpPlugin plugin = new McpPlugin(List.of(ok, bad));
        PluginManager pm = new PluginManager(List.of(plugin), AgentConfig.defaults(), new ToolRegistry());
        pm.init();

        List<Tool<?, ?>> collected = pm.collectTools();
        assertEquals(1, collected.size(), "握手失败的 server 应被隔离");
        assertEquals("srvA.calc", collected.get(0).name(), "工具名应为 serverName.toolName");
        verify(bad, never()).listTools();
    }

    @Test
    void sameToolNameAcrossServersIsUnique() {
        McpClient a = mock(McpClient.class);
        when(a.name()).thenReturn("srvA");
        when(a.initialize()).thenReturn(true);
        when(a.listTools()).thenReturn(List.of(new McpClient.ToolDescriptor("echo", "回显", Map.of())));

        McpClient b = mock(McpClient.class);
        when(b.name()).thenReturn("srvB");
        when(b.initialize()).thenReturn(true);
        when(b.listTools()).thenReturn(List.of(new McpClient.ToolDescriptor("echo", "回显", Map.of())));

        McpPlugin plugin = new McpPlugin(List.of(a, b));
        PluginManager pm = new PluginManager(List.of(plugin), AgentConfig.defaults(), new ToolRegistry());
        pm.init();

        List<Tool<?, ?>> collected = pm.collectTools();
        assertEquals(2, collected.size());
        assertTrue(
                collected.stream().anyMatch(t -> t.name().equals("srvA.echo")),
                "应含 srvA.echo");
        assertTrue(
                collected.stream().anyMatch(t -> t.name().equals("srvB.echo")),
                "应含 srvB.echo");
    }
}

package com.example.agent.plugin.memory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.config.AgentConfig;
import com.example.agent.plugin.PluginManager;
import com.example.agent.tools.ToolRegistry;

import java.util.List;

import org.junit.jupiter.api.Test;

class MemoryPluginTest {

    @Test
    void fragmentContainsThreeScopes() {
        PluginManager pm =
                new PluginManager(List.of(new MemoryPlugin()), AgentConfig.defaults(), new ToolRegistry());
        pm.init();
        String frag = pm.collectSystemPromptFragment();
        assertTrue(frag.contains("USER"), "应含 USER scope");
        assertTrue(frag.contains("PROJECT"), "应含 PROJECT scope");
        assertTrue(frag.contains("LOCAL"), "应含 LOCAL scope");
    }

    @Test
    void lifecycleInitThenCloseIsSafe() {
        PluginManager pm =
                new PluginManager(List.of(new MemoryPlugin()), AgentConfig.defaults(), new ToolRegistry());
        pm.init();
        // init 后再 close 不抛异常（MemoryPlugin 无外部资源, close 为空实现）
        pm.close();
        // close 后 fragment 收集仍返回空逻辑（不抛异常）
        assertTrue(pm.collectSystemPromptFragment().contains("USER"));
    }
}

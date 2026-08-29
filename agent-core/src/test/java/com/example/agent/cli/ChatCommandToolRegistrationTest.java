package com.example.agent.cli;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.config.AgentConfig;
import com.example.agent.tools.ToolRegistry;

import org.junit.jupiter.api.Test;

/** ChatCommand 工具注册接线测试：运行时必须有 ReadFile/WriteFile/EditFile/Ls/Shell 五个工具。 */
class ChatCommandToolRegistrationTest {

    @Test
    void registersShellAndLsTools() {
        ToolRegistry tools = new ToolRegistry();
        ChatCommand.registerShellAndLs(tools, AgentConfig.defaults());
        assertNotNull(tools.getRaw("Shell"), "Shell 工具应被注册");
        assertNotNull(tools.getRaw("Ls"), "Ls 工具应被注册");
    }

    @Test
    void fullToolSetHasAtLeastFiveTools() {
        ToolRegistry tools = new ToolRegistry();
        ToolRegistry.registerMemoryTools(tools);
        ChatCommand.registerShellAndLs(tools, AgentConfig.defaults());
        assertTrue(tools.list().size() >= 5, "运行时应有 Read/Write/Edit/Ls/Shell 五个工具");
    }
}

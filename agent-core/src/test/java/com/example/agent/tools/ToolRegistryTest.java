package com.example.agent.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agent.mcp.McpClient;
import com.example.agent.skill.Skill;
import com.example.agent.tools.file.LsTool;
import com.example.agent.tools.file.ReadFileTool;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

class ToolRegistryTest {
    @Test
    void registersAndRetrieves() {
        var reg = new ToolRegistry();
        var t = new ReadFileTool();
        reg.register(t);
        assertSame(t, reg.get("ReadFile"));
        assertEquals(1, reg.list().size());
    }

    @Test
    void supportsMultipleRegistrations() {
        var reg = new ToolRegistry();
        reg.register(new ReadFileTool());
        reg.register(new LsTool());
        assertEquals(2, reg.list().size());
        assertEquals(2, List.copyOf(reg.list()).size());
    }

    @Test
    void registerMemoryToolsAddsThree() {
        var reg = new ToolRegistry();
        ToolRegistry.registerMemoryTools(reg);
        var names = reg.list().stream().map(t -> t.name()).toList();
        assertEquals(3, names.size());
        assertEquals(true, names.contains("ReadFile"));
        assertEquals(true, names.contains("WriteFile"));
        assertEquals(true, names.contains("EditFile"));
    }

    // ---------- fix-jacoco-rule：补 BRANCH 覆盖率 ----------
    // ToolRegistry 的 deprecated 静态方法里几个 if 分支（null skills / null clients /
    // initialize 返回 false）在 jacoco 报告里 BRANCH < 0.7；补 5 条用例把它们跑到。

    @Test
    void getReturnsNullWhenMissing() {
        var reg = new ToolRegistry();
        assertNull(reg.get("nonexistent"));
        assertNull(reg.getRaw("nonexistent"));
    }

    @Test
    void registerSkillToolsWithNullListDoesNothing() {
        var reg = new ToolRegistry();
        ToolRegistry.registerSkillTools(reg, null);
        assertEquals(0, reg.list().size());
    }

    @Test
    void registerSkillToolsRegistersEachSkill() {
        var reg = new ToolRegistry();
        var s1 = new Skill("a", "desc-a", "content-a", Path.of("/tmp/a"));
        var s2 = new Skill("b", "desc-b", "content-b", Path.of("/tmp/b"));
        ToolRegistry.registerSkillTools(reg, List.of(s1, s2));
        // 每个 skill 注册为一个工具，工具名 = skill name
        assertEquals(2, reg.list().size());
        assertEquals("a", reg.get("a").name());
        assertEquals("b", reg.get("b").name());
    }

    @Test
    void registerMcpToolsWithNullClientsDoesNothing() {
        var reg = new ToolRegistry();
        ToolRegistry.registerMcpTools(reg, null);
        assertEquals(0, reg.list().size());
    }

    @Test
    void registerMcpToolsSkipsClientWhoseInitializeFails() {
        var reg = new ToolRegistry();
        McpClient failing = mock(McpClient.class);
        when(failing.initialize()).thenReturn(false);
        ToolRegistry.registerMcpTools(reg, List.of(failing));
        // 握手失败：什么都不注册
        assertEquals(0, reg.list().size());
    }
}


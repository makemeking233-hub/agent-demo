package com.example.agent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.permission.PermissionDecision;
import com.example.agent.tools.Tool.ToolContext;
import com.example.agent.tools.ToolResult;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

class SkillToolTest {

    private final Skill skill =
            new Skill("deploy", "部署到服务器", "步骤：1. build 2. deploy", Path.of("/tmp/deploy"));
    private final SkillTool tool = new SkillTool(skill);

    @Test
    void exposesSkillNameAndDescription() {
        assertEquals("deploy", tool.name());
        assertTrue(tool.description().contains("部署到服务器"));
    }

    @Test
    void executesReturnsSkillContent() {
        ToolResult<String> result =
                tool.execute("{}", new ToolContext(Path.of("/tmp"), null, () -> false)).block();
        assertTrue(result.toModelContent().contains("步骤"));
        assertEquals("deploy", tool.skill().name());
    }

    @Test
    void isReadOnlyAndAllowed() {
        assertTrue(tool.isReadOnly("{}"));
        assertEquals(PermissionDecision.allow(), tool.checkPermissions("{}", null));
    }
}

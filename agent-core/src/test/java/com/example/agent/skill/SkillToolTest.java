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

    // ---- T1.2：补齐「SkillTool 对外契约不变」保护网 ----

    @Test
    void descriptionCarriesFixedPrefix() {
        // 前缀「技能：调用以加载技能内容。」是模型识别 skill 的信号，重构后必须保持
        String desc = tool.description();
        assertTrue(desc.startsWith("技能：调用以加载技能内容。"), "描述前缀必须保留: " + desc);
        assertTrue(desc.endsWith("部署到服务器"), "描述以 frontmatter description 结尾: " + desc);
    }

    @Test
    void inputSchemaIsEmptyObject() {
        var schema = tool.inputSchema();
        assertEquals("object", schema.get("type"));
        assertEquals(java.util.Map.of(), schema.get("properties"));
        assertEquals(java.util.List.of(), schema.get("required"));
    }

    @Test
    void renderUseShowsSkillName() {
        assertTrue(tool.renderUse("{}").contains("deploy"));
    }
}


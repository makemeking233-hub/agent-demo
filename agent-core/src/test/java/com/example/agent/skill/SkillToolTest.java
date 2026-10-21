package com.example.agent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.permission.PermissionDecision;
import com.example.agent.tools.Tool.ToolContext;
import com.example.agent.tools.ToolResult;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

class SkillToolTest {

    /** T2 重构后 Skill 由 record 改 final class；构造需真实 SKILL.md 供 body() 读盘 */
    private final Skill skill = testSkill("deploy", "部署到服务器", "步骤：1. build 2. deploy");
    private final SkillTool tool = new SkillTool(skill);

    /** 建临时技能目录并写 SKILL.md，返回包好的 Skill。 */
    private static Skill testSkill(String name, String description, String body) {
        try {
            Path dir = java.nio.file.Files.createTempDirectory("skill-" + name);
            java.nio.file.Files.writeString(
                    dir.resolve("SKILL.md"),
                    "---\nname: " + name + "\ndescription: " + description + "\n---\n" + body);
            return new Skill(new SkillMeta(name, description, SkillScope.USER, dir));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("test skill 目录创建失败", e);
        }
    }

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


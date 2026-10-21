package com.example.agent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.permission.PermissionDecision;
import com.example.agent.tools.Tool.ToolContext;
import com.example.agent.tools.ToolCategory;
import com.example.agent.tools.ToolResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * SkillSearchTool 单元测试（add-skill-demand-loading T5）。
 *
 * <p>覆盖：工具名、schema 必填、检索命中、无命中空、异常降级、权限属性。
 */
class SkillSearchToolTest {
    @TempDir Path tmp;

    private Path writeSkill(String name, String desc) throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("skills").resolve(name));
        Files.writeString(
                dir.resolve("SKILL.md"),
                "---\nname: " + name + "\ndescription: " + desc + "\n---\n正文\n");
        return dir;
    }

    private SkillSearchTool toolWith(SkillPool pool) {
        return new SkillSearchTool(pool, 10);
    }

    private static ToolContext ctx() {
        return new ToolContext(Path.of("/tmp"), null, () -> false);
    }

    @Test
    void nameIsSkillSearch() {
        var tool = toolWith(SkillPool.bootstrap(List.of()));
        assertEquals("skill_search", tool.name());
    }

    @Test
    void descriptionExplainsUsage() {
        var tool = toolWith(SkillPool.bootstrap(List.of()));
        String d = tool.description();
        assertTrue(d.contains("检索"), "描述应说明是检索工具: " + d);
        assertTrue(d.contains("技能"), "描述应提到技能: " + d);
    }

    @Test
    void inputSchemaRequiresQuery() {
        var tool = toolWith(SkillPool.bootstrap(List.of()));
        Map<String, Object> schema = tool.inputSchema();
        assertEquals("object", schema.get("type"));
        assertEquals(List.of("query"), schema.get("required"));
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("query"));
    }

    @Test
    void executeReturnsMatchingSkills() throws Exception {
        writeSkill("pdf-form-filler", "填充 PDF 表单域");
        var pool = SkillPool.bootstrap(List.of(tmp.resolve("skills")));
        var tool = toolWith(pool);

        ToolResult<String> r = tool.execute("PDF", ctx()).block();
        assertTrue(r != null && !r.isError());
        assertTrue(r.toModelContent().contains("pdf-form-filler"), "结果应含技能名: " + r.toModelContent());
        assertTrue(r.toModelContent().contains("填充"), "结果应含描述");
    }

    @Test
    void executeReturnsEmptyForNoMatch() throws Exception {
        writeSkill("pdf-form-filler", "填充 PDF 表单域");
        var pool = SkillPool.bootstrap(List.of(tmp.resolve("skills")));
        var tool = toolWith(pool);

        ToolResult<String> r = tool.execute("完全无关词", ctx()).block();
        assertTrue(r != null && !r.isError());
        assertTrue(r.toModelContent().isBlank() || r.toModelContent().contains("无"), "无命中应返回空或说明: " + r.toModelContent());
    }

    @Test
    void executeToleratesNullPool() {
        var tool = new SkillSearchTool(null, 10);
        ToolResult<String> r = tool.execute("PDF", ctx()).block();
        assertTrue(r != null && !r.isError(), "pool 为 null 时不应抛异常");
    }

    @Test
    void executeToleratesBlankQuery() throws Exception {
        writeSkill("a", "技能 A");
        var pool = SkillPool.bootstrap(List.of(tmp.resolve("skills")));
        var tool = toolWith(pool);

        for (String q : new String[] {null, "", "   "}) {
            ToolResult<String> r = tool.execute(q, ctx()).block();
            assertTrue(r != null && !r.isError(), "空 query 不应抛异常");
        }
    }

    @Test
    void isReadOnlyAndAllowed() {
        var tool = toolWith(SkillPool.bootstrap(List.of()));
        assertTrue(tool.isReadOnly("{}"));
        assertEquals(PermissionDecision.allow(), tool.checkPermissions("{}", null));
        assertEquals(ToolCategory.READ, tool.category());
    }

    @Test
    void renderUseShowsQueryIntent() {
        var tool = toolWith(SkillPool.bootstrap(List.of()));
        assertTrue(tool.renderUse("PDF").contains("skill_search")
                || tool.renderUse("PDF").contains("PDF"));
    }
}
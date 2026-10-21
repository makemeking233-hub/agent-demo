package com.example.agent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * SkillSectionProvider 单元测试（add-skill-demand-loading T6.5）。
 *
 * <p>覆盖：候选渲染格式、描述截断、无命中/空 query/null pool 返回空串。
 */
class SkillSectionProviderTest {
    @TempDir Path tmp;

    private Path writeSkill(String name, String desc) throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("skills").resolve(name));
        Files.writeString(
                dir.resolve("SKILL.md"),
                "---\nname: " + name + "\ndescription: " + desc + "\n---\n正文\n");
        return dir;
    }

    private SkillPool poolWith(String... skills) throws Exception {
        for (int i = 0; i < skills.length; i += 2) {
            writeSkill(skills[i], skills[i + 1]);
        }
        return SkillPool.bootstrap(List.of(tmp.resolve("skills")));
    }

    @Test
    void rendersCandidateListWithHeader() throws Exception {
        var pool = poolWith("pdf-form-filler", "填充 PDF 表单域");
        var provider = new SkillSectionProvider(pool, 10, 40);

        String section = provider.sectionFor("PDF");
        assertTrue(section.startsWith(SkillSectionProvider.HEADER), "应以标题开头: " + section);
        assertTrue(section.contains("pdf-form-filler"), "应含技能名");
        assertTrue(section.contains("填充 PDF 表单域"), "应含描述");
        assertTrue(section.contains(SkillSectionProvider.FOOTER), "应含尾部引导");
    }

    @Test
    void truncatesLongDescription() throws Exception {
        // 注意：分词按空白/标点切分（MemoryRecall.tokenize 规则），不做子串切分。
        // 中文连写会整段成为单 token，故此处用空格分词构造可命中的长描述。
        String longDesc = "长描述 内容 内容 内容 内容 内容 内容 内容 内容 内容 内容";
        var pool = poolWith("long-skill", longDesc);
        var provider = new SkillSectionProvider(pool, 10, 20);

        String section = provider.sectionFor("长描述");
        assertTrue(section.contains("…"), "超长描述应被截断并加省略号: " + section);
        assertFalse(section.contains(longDesc), "不应出现完整长描述");
    }

    @Test
    void noMatchReturnsEmpty() throws Exception {
        var pool = poolWith("pdf-form-filler", "填充 PDF");
        var provider = new SkillSectionProvider(pool, 10, 40);
        assertEquals("", provider.sectionFor("完全无关词"));
    }

    @Test
    void blankQueryReturnsEmpty() throws Exception {
        var pool = poolWith("a", "技能 A");
        var provider = new SkillSectionProvider(pool, 10, 40);
        assertEquals("", provider.sectionFor(null));
        assertEquals("", provider.sectionFor(""));
        assertEquals("", provider.sectionFor("   "));
    }

    @Test
    void nullPoolReturnsEmpty() {
        var provider = new SkillSectionProvider(null, 10, 40);
        assertEquals("", provider.sectionFor("PDF"));
    }

    @Test
    void respectsCandidateLimit() throws Exception {
        var pool = poolWith(
                "pdf-a", "PDF A", "pdf-b", "PDF B", "pdf-c", "PDF C", "pdf-d", "PDF D");
        var provider = new SkillSectionProvider(pool, 2, 40);
        String section = provider.sectionFor("PDF");

        long count = section.lines().filter(l -> l.startsWith("- ")).count();
        assertEquals(2, count, "候选条数应受 k 限制: " + section);
    }

    @Test
    void truncateHelperHandlesEdgeCases() {
        assertEquals("", SkillSectionProvider.truncate(null, 10));
        assertEquals("", SkillSectionProvider.truncate("abc", 0));
        assertEquals("ab", SkillSectionProvider.truncate("ab", 10));
        assertEquals("a…", SkillSectionProvider.truncate("abcdef", 1));
    }
}
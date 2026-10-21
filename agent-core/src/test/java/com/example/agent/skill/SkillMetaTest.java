package com.example.agent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * SkillMeta 单元测试（refactor-skill-index-layer T2.2）。
 *
 * <p>覆盖：字段访问、scope 缺省为 USER、构造校验（name / description / dir 非空）。
 */
class SkillMetaTest {
    @TempDir Path tmp;

    @Test
    void exposesAllFields() {
        Path dir = tmp.resolve("my-skill");
        var meta = new SkillMeta("my-skill", "做某事", SkillScope.USER, dir);
        assertEquals("my-skill", meta.name());
        assertEquals("做某事", meta.description());
        assertEquals(SkillScope.USER, meta.scope());
        assertEquals(dir, meta.dir());
    }

    @Test
    void scopeDefaultsToUser() {
        var meta = new SkillMeta("s", "d", null, tmp);
        assertEquals(SkillScope.USER, meta.scope(), "scope 缺省为 USER");
    }

    @Test
    void rejectsBlankName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillMeta("", "d", SkillScope.USER, tmp));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillMeta("   ", "d", SkillScope.USER, tmp));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillMeta(null, "d", SkillScope.USER, tmp));
    }

    @Test
    void rejectsBlankDescription() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillMeta("s", "", SkillScope.USER, tmp));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillMeta("s", null, SkillScope.USER, tmp));
    }

    @Test
    void rejectsNullDir() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillMeta("s", "d", SkillScope.USER, null));
    }

    @Test
    void skillFilePathPointsToSkillMd() {
        var meta = new SkillMeta("s", "d", SkillScope.USER, tmp.resolve("s"));
        Path f = meta.skillFilePath();
        assertNotNull(f);
        assertTrue(f.toString().endsWith("SKILL.md"), "指向 SKILL.md: " + f);
    }
}
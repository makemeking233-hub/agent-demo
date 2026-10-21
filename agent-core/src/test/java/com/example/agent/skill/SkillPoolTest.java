package com.example.agent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * SkillPool 单元测试（refactor-skill-index-layer T4.3-T4.5）。
 *
 * <p>覆盖：bootstrap 扫两级目录、instance 幂等、load 命中/未命中、search 倒排匹配、allOf scope 过滤。
 */
class SkillPoolTest {
    @TempDir Path tmp;

    @AfterEach
    void resetSingleton() {
        SkillPool.resetForTest();
    }

    private Path writeSkill(String dirName, String name, String desc) throws Exception {
        Path dir = Files.createDirectories(tmp.resolve(dirName).resolve(name));
        Files.writeString(
                dir.resolve("SKILL.md"),
                "---\nname: " + name + "\ndescription: " + desc + "\n---\n正文内容\n");
        return dir;
    }

    @Test
    void bootstrapScansBothLevels() throws Exception {
        Path u = tmp.resolve("user-skills");
        Path p = tmp.resolve("proj-skills");
        writeSkill("user-skills", "a-skill", "用户技能");
        writeSkill("proj-skills", "b-skill", "项目技能");

        var pool = SkillPool.bootstrap(List.of(u, p));
        assertEquals(2, pool.all().size());
        assertTrue(pool.load("a-skill").isPresent());
        assertTrue(pool.load("b-skill").isPresent());
    }

    @Test
    void bootstrapAssignsScopeByRootOrder() throws Exception {
        Path u = tmp.resolve("user-skills");
        Path p = tmp.resolve("proj-skills");
        writeSkill("user-skills", "u-only", "用户级");
        writeSkill("proj-skills", "p-only", "项目级");

        var pool = SkillPool.bootstrap(List.of(u, p));
        assertEquals(SkillScope.USER, pool.load("u-only").orElseThrow().scope());
        assertEquals(SkillScope.PROJECT, pool.load("p-only").orElseThrow().scope());
    }

    @Test
    void bootstrapLaterRootOverridesSameName() throws Exception {
        Path u = tmp.resolve("user-skills");
        Path p = tmp.resolve("proj-skills");
        writeSkill("user-skills", "shared", "用户版");
        writeSkill("proj-skills", "shared", "项目版");

        var pool = SkillPool.bootstrap(List.of(u, p));
        assertEquals(1, pool.all().size(), "同名只保留一条");
        assertEquals("项目版", pool.load("shared").orElseThrow().description());
        assertEquals(SkillScope.PROJECT, pool.load("shared").orElseThrow().scope());
    }

    @Test
    void loadReturnsEmptyForUnknownName() throws Exception {
        Path u = tmp.resolve("user-skills");
        writeSkill("user-skills", "known", "已知");
        var pool = SkillPool.bootstrap(List.of(u));

        Optional<Skill> got = pool.load("unknown");
        assertTrue(got.isEmpty(), "未知 skill 应返回 empty");
    }

    @Test
    void loadExposesLazyBody() throws Exception {
        Path u = tmp.resolve("user-skills");
        writeSkill("user-skills", "lazy", "懒加载");
        var pool = SkillPool.bootstrap(List.of(u));

        Skill skill = pool.load("lazy").orElseThrow();
        assertEquals("lazy", skill.name());
        assertTrue(skill.body().contains("正文内容"), "body() 应按需读盘返回正文");
    }

    @Test
    void searchMatchesByKeyword() throws Exception {
        Path u = tmp.resolve("user-skills");
        writeSkill("user-skills", "pdf-form-filler", "填充 PDF 表单域");
        writeSkill("user-skills", "excel-pivot", "Excel 数据透视表");
        var pool = SkillPool.bootstrap(List.of(u));

        List<SkillMeta> hits = pool.search("PDF", 10);
        assertEquals(1, hits.size(), "PDF 应只命中 pdf-form-filler: " + hits);
        assertEquals("pdf-form-filler", hits.get(0).name());
    }

    @Test
    void searchRespectsLimit() throws Exception {
        Path u = tmp.resolve("user-skills");
        for (int i = 0; i < 5; i++) {
            writeSkill("user-skills", "pdf-skill-" + i, "PDF 相关技能 " + i);
        }
        var pool = SkillPool.bootstrap(List.of(u));

        List<SkillMeta> hits = pool.search("PDF", 2);
        assertEquals(2, hits.size(), "应按 limit 截断");
    }

    @Test
    void searchReturnsEmptyForUnmatchedQuery() throws Exception {
        Path u = tmp.resolve("user-skills");
        writeSkill("user-skills", "pdf-form-filler", "填充 PDF 表单域");
        var pool = SkillPool.bootstrap(List.of(u));

        assertTrue(pool.search("kubernetes", 10).isEmpty(), "无匹配应返回空列表");
    }

    @Test
    void searchWithBlankQueryReturnsEmpty() throws Exception {
        Path u = tmp.resolve("user-skills");
        writeSkill("user-skills", "any", "任意");
        var pool = SkillPool.bootstrap(List.of(u));

        assertTrue(pool.search("", 10).isEmpty());
        assertTrue(pool.search(null, 10).isEmpty());
        assertTrue(pool.search("   ", 10).isEmpty());
    }

    @Test
    void allOfFiltersByScope() throws Exception {
        Path u = tmp.resolve("user-skills");
        Path p = tmp.resolve("proj-skills");
        writeSkill("user-skills", "u-skill", "用户级");
        writeSkill("proj-skills", "p-skill", "项目级");
        var pool = SkillPool.bootstrap(List.of(u, p));

        List<SkillMeta> userOnly = pool.allOf(SkillScope.USER);
        assertEquals(1, userOnly.size());
        assertEquals("u-skill", userOnly.get(0).name());
        assertEquals(1, pool.allOf(SkillScope.PROJECT).size());
        assertEquals(2, pool.all().size());
    }

    @Test
    void instanceIsSingletonAndIdempotent() throws Exception {
        Path u = tmp.resolve("user-skills");
        writeSkill("user-skills", "s", "技能");

        var first = SkillPool.bootstrap(List.of(u));
        SkillPool.install(first);
        var second = SkillPool.instance();
        assertSame(first, second, "instance() 应返回已安装的单例");

        // 重复 bootstrap 不会覆盖已安装实例（幂等）
        SkillPool.bootstrap(List.of(u));
        assertSame(first, SkillPool.instance());
    }

    @Test
    void bootstrapWithNullOrEmptyRootsYieldsEmptyPool() {
        assertTrue(SkillPool.bootstrap(null).all().isEmpty());
        assertTrue(SkillPool.bootstrap(List.of()).all().isEmpty());
        assertTrue(SkillPool.bootstrap(List.of(tmp.resolve("nope"))).all().isEmpty());
    }

    @Test
    void keywordCountReflectsIndexedTerms() throws Exception {
        Path u = tmp.resolve("user-skills");
        writeSkill("user-skills", "alpha-skill", "描述文字");
        var pool = SkillPool.bootstrap(List.of(u));
        assertTrue(pool.keywordCount() > 0, "应索引到关键词");
    }

    @Test
    void twoPoolsAreIndependentInstances() throws Exception {
        Path u = tmp.resolve("user-skills");
        writeSkill("user-skills", "x", "技能");
        var a = SkillPool.bootstrap(List.of(u));
        var b = SkillPool.bootstrap(List.of(u));
        assertNotSame(a, b, "bootstrap 每次返回新实例，单例由 installForTest 管理");
    }

    // ---- T4.6-T4.8：索引生命周期与缓存 ----

    @Test
    void poolIsAutoCloseable() throws Exception {
        Path u = tmp.resolve("user-skills");
        writeSkill("user-skills", "s", "技能");
        var pool = SkillPool.bootstrap(List.of(u));
        assertTrue(pool instanceof AutoCloseable, "SkillPool 应实现 AutoCloseable");
        ((AutoCloseable) pool).close();
        // close 后仍可读（元数据在内存，不因 close 失效）
        assertEquals(1, pool.all().size());
    }

    @Test
    void mtimeCacheInvalidatesAfterEdit() throws Exception {
        Path u = tmp.resolve("user-skills");
        writeSkill("user-skills", "cached", "缓存测试");
        var pool = SkillPool.bootstrap(List.of(u));
        Skill s = pool.load("cached").orElseThrow();
        assertTrue(s.body().contains("正文内容"), "首次应读到正文");

        // 改正文并确保 mtime 变化
        Thread.sleep(20);
        Files.writeString(
                u.resolve("cached").resolve("SKILL.md"),
                "---\nname: cached\ndescription: 缓存测试\n---\n新正文内容\n");
        assertTrue(s.body().contains("新正文内容"), "mtime 变化后应重读");
    }

    @Test
    void searchReturnsMetaNotSkill() throws Exception {
        Path u = tmp.resolve("user-skills");
        writeSkill("user-skills", "pdf-x", "PDF 相关");
        var pool = SkillPool.bootstrap(List.of(u));
        for (SkillMeta m : pool.search("PDF", 5)) {
            assertNotSame(null, m);
            assertEquals("pdf-x", m.name());
            assertTrue(m.dir() != null, "应带 dir 供懒加载用");
        }
    }
}
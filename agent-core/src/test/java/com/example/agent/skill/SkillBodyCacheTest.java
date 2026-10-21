package com.example.agent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Skill 正文懒加载测试（refactor-skill-index-layer T2.7）。
 *
 * <p>覆盖：
 *
 * <ul>
 *   <li>首次 {@code body()} 读盘返回正文（frontmatter 之后的 body）
 *   <li>mtime 未变时复用缓存（文件被删也不报错）
 *   <li>mtime 变更后重新读盘
 *   <li>读盘失败时返回错误说明文本而非抛异常
 * </ul>
 */
class SkillBodyCacheTest {
    @TempDir Path tmp;

    private Path writeSkill(String body) throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("my-skill"));
        Files.writeString(
                dir.resolve("SKILL.md"), "---\nname: my-skill\ndescription: 做某事\n---\n" + body);
        return dir;
    }

    private static String bodyPart(String whole) {
        int idx = whole.indexOf("---", whole.indexOf("---") + 3);
        return whole.substring(idx + 3).strip();
    }

    @Test
    void firstBodyCallReadsFromDisk() throws Exception {
        Path dir = writeSkill("\n步骤 1\n步骤 2\n");
        Skill skill = new Skill(new SkillMeta("my-skill", "做某事", SkillScope.USER, dir));
        assertEquals("步骤 1\n步骤 2", skill.body());
    }

    @Test
    void nameAndDescriptionDelegateToMeta() throws Exception {
        Path dir = writeSkill("正文");
        var meta = new SkillMeta("my-skill", "做某事", SkillScope.PROJECT, dir);
        Skill skill = new Skill(meta);
        assertEquals("my-skill", skill.name());
        assertEquals("做某事", skill.description());
        assertEquals(SkillScope.PROJECT, skill.scope());
        assertEquals(dir, skill.dir());
    }

    @Test
    void metaAccessorReturnsUnderlyingMeta() throws Exception {
        Path dir = writeSkill("正文");
        var meta = new SkillMeta("my-skill", "做某事", SkillScope.USER, dir);
        assertEquals(meta, new Skill(meta).meta());
    }

    @Test
    void cachedBodyReusedWhenMtimeUnchanged() throws Exception {
        Path dir = writeSkill("原文");
        Skill skill = new Skill(new SkillMeta("my-skill", "做某事", SkillScope.USER, dir));

        assertEquals("原文", skill.body());
        // 删掉文件：mtime 判定会因文件不存在而回退，但已有缓存应仍可返回
        Files.deleteIfExists(dir.resolve("SKILL.md"));
        assertEquals("原文", skill.body(), "mtime 未变时应返回缓存，不重复读盘");
    }

    @Test
    void bodyRereadsAfterFileChange() throws Exception {
        Path dir = writeSkill("原正文");
        Skill skill = new Skill(new SkillMeta("my-skill", "做某事", SkillScope.USER, dir));
        assertEquals("原正文", skill.body());

        // 修改文件内容（mtime 会变；为保证精度显式 sleep 让文件系统时间戳更新）
        Thread.sleep(20);
        Files.writeString(dir.resolve("SKILL.md"),
                "---\nname: my-skill\ndescription: 做某事\n---\n新正文");
        assertEquals("新正文", skill.body(), "mtime 变化后应重新读盘");
    }

    @Test
    void readFailureReturnsErrorTextNotThrow() throws Exception {
        // 目录存在但 SKILL.md 不存在
        Path dir = Files.createDirectories(tmp.resolve("ghost"));
        Skill skill = new Skill(new SkillMeta("ghost", "不存在的技能", SkillScope.USER, dir));
        String body = skill.body();
        assertTrue(body.contains("加载失败") || body.contains("ghost"),
                "读盘失败应返回说明文本而非抛异常，实际: " + body);
    }

    @Test
    void bodyPartHelperWorks() throws Exception {
        String whole = "---\nname: x\ndescription: y\n---\n内容A";
        assertEquals("内容A", bodyPart(whole));
    }
}
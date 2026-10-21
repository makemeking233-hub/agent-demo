package com.example.agent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class SkillCatalogTest {
    @TempDir Path tmp;

    private Path writeSkill(Path root, String name, String content) throws Exception {
        Path dir = root.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("SKILL.md"), content);
        return dir;
    }

    @Test
    void discoversSkillWithFrontmatter() throws Exception {
        Path root = tmp.resolve("skills");
        writeSkill(
                root,
                "my-skill",
                "---\nname: my-skill\ndescription: 处理某项任务\n---\n\n执行步骤 1\n步骤 2\n");

        List<Skill> skills = SkillCatalog.discover(List.of(root));
        assertEquals(1, skills.size());
        assertEquals("my-skill", skills.get(0).name());
        assertEquals("处理某项任务", skills.get(0).description());
        // T2 重构：正文改为 body() 按需读盘，不再是构造期常驻字段
        assertTrue(skills.get(0).body().contains("执行步骤 1"));
    }

    @Test
    void skipsSkillWithoutFrontmatter() throws Exception {
        Path root = tmp.resolve("skills");
        writeSkill(root, "no-fm", "没有 frontmatter 的普通 markdown\n");

        List<Skill> skills = SkillCatalog.discover(List.of(root));
        assertTrue(skills.isEmpty(), "无 frontmatter 应跳过");
    }

    @Test
    void skipsSkillMissingNameOrDescription() throws Exception {
        Path root = tmp.resolve("skills");
        writeSkill(root, "missing-desc", "---\nname: only-name\n---\n正文\n");

        List<Skill> skills = SkillCatalog.discover(List.of(root));
        assertTrue(skills.isEmpty(), "缺 description 应跳过");
    }

    @Test
    void emptyOrMissingRootReturnsEmpty() {
        List<Skill> skills = SkillCatalog.discover(List.of(tmp.resolve("no-such-dir")));
        assertTrue(skills.isEmpty());
        assertTrue(SkillCatalog.discover(null).isEmpty());
    }

    @Test
    void laterRootOverridesSameNamedSkill() throws Exception {
        Path userRoot = tmp.resolve("user-skills");
        Path projRoot = tmp.resolve("proj-skills");
        writeSkill(userRoot, "shared", "---\nname: shared\ndescription: 用户版\n---\n用户正文\n");
        String projDir =
                writeSkill(projRoot, "shared", "---\nname: shared\ndescription: 项目版\n---\n项目正文\n")
                        .toString();

        List<Skill> skills = SkillCatalog.discover(List.of(userRoot, projRoot));
        assertEquals(1, skills.size());
        assertEquals("项目版", skills.get(0).description());
        assertEquals(projDir, skills.get(0).dir().toString());
    }

    // ---- T1.1：补齐「行为不变」保护网（重构前基线） ----

    @Test
    void nestedSubdirectoryIsNotDiscovered() throws Exception {
        // 一级深度规则：<root>/<name>/SKILL.md 才是技能，<root>/outer/inner/SKILL.md 不识别
        Path root = tmp.resolve("skills");
        Path nested = root.resolve("outer").resolve("inner");
        Files.createDirectories(nested);
        Files.writeString(
                nested.resolve("SKILL.md"),
                "---\nname: inner\ndescription: 嵌套技能\n---\n正文\n");

        List<Skill> skills = SkillCatalog.discover(List.of(root));
        assertTrue(skills.isEmpty(), "嵌套子目录不应被识别为技能");
    }

    @Test
    void skipsSkillWithUnclosedFrontmatter() throws Exception {
        // frontmatter 未闭合（没有第二个 ---）
        Path root = tmp.resolve("skills");
        writeSkill(root, "unclosed", "---\nname: unclosed\ndescription: 没闭合\n正文继续\n");

        List<Skill> skills = SkillCatalog.discover(List.of(root));
        assertTrue(skills.isEmpty(), "frontmatter 未闭合应跳过");
    }

    @Test
    void flatNameDotMdAtRootIsNotDiscovered() throws Exception {
        // T1.1 发现的既有实现与 spec 偏差（记录真实行为，不在本 change 修）：
        //   spec 说「<root>/<name>/SKILL.md 或 <root>/<name>.md 均可」，
        //   但 discover() 只遍历 root 的一级【子目录】，root 下直放的 <name>.md 不会被发现。
        //   resolveSkillFile 的 named 分支只在【子目录内】找 <dirname>.md。
        //   修复它属于行为变更，另开 change；本 change 保持该行为不变。
        Path root = tmp.resolve("skills");
        Files.createDirectories(root);
        Files.writeString(
                root.resolve("flat-skill.md"),
                "---\nname: flat-skill\ndescription: 扁平布局\n---\n正文\n");

        List<Skill> skills = SkillCatalog.discover(List.of(root));
        assertTrue(skills.isEmpty(), "root 下直放的 <name>.md 当前实现不识别（既有行为，本 change 不改）");
    }
}


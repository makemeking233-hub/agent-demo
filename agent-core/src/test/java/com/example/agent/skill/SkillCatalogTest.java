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
        assertTrue(skills.get(0).content().contains("执行步骤 1"));
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
}

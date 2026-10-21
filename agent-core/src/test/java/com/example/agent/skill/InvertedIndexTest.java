package com.example.agent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * InvertedIndex 单元测试（refactor-skill-index-layer T4.2）。
 *
 * <p>覆盖：建索引、keyword 精确命中、多 keyword 并集、无命中返回空、scope 隔离。
 */
class InvertedIndexTest {
    @TempDir Path tmp;

    private Path writeSkill(String dirName, String name, String desc) throws Exception {
        Path dir = Files.createDirectories(tmp.resolve(dirName).resolve(name));
        Files.writeString(
                dir.resolve("SKILL.md"),
                "---\nname: " + name + "\ndescription: " + desc + "\n---\n正文\n");
        return dir;
    }

    private SkillMeta meta(String name, String desc, Path dir, SkillScope scope) {
        return new SkillMeta(name, desc, scope, dir);
    }

    @Test
    void indexSingleSkill() throws Exception {
        Path dir = writeSkill("user", "pdf-form-filler", "填充 PDF 表单域");
        var idx = new InvertedIndex(List.of(meta("pdf-form-filler", "填充 PDF 表单域", dir, SkillScope.USER)));
        Set<String> hits = idx.lookup("pdf");
        assertTrue(hits.contains("pdf-form-filler"), "应命中 pdf 关键词: " + hits);
    }

    @Test
    void lookupAllUnionsMultipleKeywords() throws Exception {
        Path d1 = writeSkill("user", "pdf-form-filler", "填充 PDF 表单域");
        Path d2 = writeSkill("user", "pdf-merge", "合并 PDF 文件");
        Path d3 = writeSkill("user", "excel-pivot", "Excel 数据透视表");
        var idx = new InvertedIndex(List.of(
                meta("pdf-form-filler", "填充 PDF 表单域", d1, SkillScope.USER),
                meta("pdf-merge", "合并 PDF 文件", d2, SkillScope.USER),
                meta("excel-pivot", "Excel 数据透视表", d3, SkillScope.USER)));

        Set<String> hits = idx.lookupAll(List.of("pdf", "合并"));
        assertEquals(2, hits.size(), "pdf + 合并 应命中 2 个: " + hits);
        assertTrue(hits.contains("pdf-form-filler"));
        assertTrue(hits.contains("pdf-merge"));
    }

    @Test
    void noMatchReturnsEmptySet() {
        var idx = new InvertedIndex(List.of());
        assertTrue(idx.lookup("不存在的关键词").isEmpty());
    }

    @Test
    void scopeIsolationKeepsSeparateIndexes() throws Exception {
        Path u = writeSkill("user", "shared", "用户版");
        Path p = writeSkill("proj", "shared", "项目版");
        var userIdx = new InvertedIndex(List.of(meta("shared", "用户版", u, SkillScope.USER)));
        var projIdx = new InvertedIndex(List.of(meta("shared", "项目版", p, SkillScope.PROJECT)));

        assertTrue(userIdx.lookup("用户版").contains("shared"));
        assertTrue(projIdx.lookup("项目版").contains("shared"));
        assertTrue(userIdx.lookup("项目版").isEmpty(), "USER 索引不应命中项目版描述");
    }

    @Test
    void indexCoversNameAndDescription() throws Exception {
        // T4.2 修正：分词复用 MemoryRecall.tokenize —— 只按空白/标点切分、不做子串切分，
        // 所以 "k8s-deploy" 作为整词入索引，lookup 需传完整词元而非 "k8s"。
        Path dir = writeSkill("user", "k8s-deploy", "部署到 Kubernetes 集群");
        var idx = new InvertedIndex(List.of(meta("k8s-deploy", "部署到 Kubernetes 集群", dir, SkillScope.USER)));
        assertTrue(idx.lookup("k8s-deploy").contains("k8s-deploy"), "name 整词应被索引");
        assertTrue(idx.lookup("kubernetes").contains("k8s-deploy"), "description 词元应被索引");
        assertTrue(idx.lookup("集群").contains("k8s-deploy"), "description 中文词元应被索引");
    }

    @Test
    void emptySkillListYieldsEmptyIndex() {
        var idx = new InvertedIndex(List.of());
        assertEquals(0, idx.keywordCount());
    }
}
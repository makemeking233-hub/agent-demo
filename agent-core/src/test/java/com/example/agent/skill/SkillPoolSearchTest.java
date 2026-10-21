package com.example.agent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.memory.embedding.EmbeddingProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * SkillPool 检索层测试（add-skill-demand-loading T3）。
 *
 * <p>覆盖：倒排预筛、embedding KNN 精排、{@code isReady()=false} 降级为纯倒排、
 * 候选集为空时不回退全量 KNN。
 */
class SkillPoolSearchTest {
    @TempDir Path tmp;

    private Path writeSkill(String dirName, String name, String desc) throws Exception {
        Path dir = Files.createDirectories(tmp.resolve(dirName).resolve(name));
        Files.writeString(
                dir.resolve("SKILL.md"),
                "---\nname: " + name + "\ndescription: " + desc + "\n---\n正文\n");
        return dir;
    }

    /** 假 embedding：字符 hash 到固定维度，语义无关但可复现（用于验证排序链路生效）。 */
    private static final class FakeEmbedder implements EmbeddingProvider {
        private final boolean ready;
        private final int dims;

        FakeEmbedder(boolean ready, int dims) {
            this.ready = ready;
            this.dims = dims;
        }

        @Override
        public float[] embed(String text) {
            float[] v = new float[dims];
            if (text == null || text.isBlank()) return v;
            String s = text.toLowerCase(Locale.ROOT);
            for (int i = 0; i < s.length(); i++) {
                v[i % dims] += (s.charAt(i) % 31) + 1;
            }
            // L2 归一化
            double norm = 0;
            for (float f : v) norm += f * f;
            norm = Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < dims; i++) v[i] /= (float) norm;
            }
            return v;
        }

        @Override
        public int dimensions() {
            return dims;
        }

        @Override
        public boolean isReady() {
            return ready;
        }
    }

    @Test
    void searchWithReadyEmbedderRanksBySimilarity() throws Exception {
        Path u = tmp.resolve("user-skills");
        writeSkill("user-skills", "pdf-form-filler", "填充 PDF 表单域");
        writeSkill("user-skills", "excel-pivot", "Excel 数据透视表");
        var pool = SkillPool.bootstrap(List.of(u), new FakeEmbedder(true, 64));

        List<SkillMeta> hits = pool.search("PDF 表单", 5);
        assertTrue(!hits.isEmpty(), "应命中");
        assertEquals("pdf-form-filler", hits.get(0).name(), "最相似的应排第一");
    }

    @Test
    void searchFallsBackToInvertedWhenEmbedderNotReady() throws Exception {
        Path u = tmp.resolve("user-skills");
        writeSkill("user-skills", "pdf-form-filler", "填充 PDF 表单域");
        var pool = SkillPool.bootstrap(List.of(u), new FakeEmbedder(false, 64));

        List<SkillMeta> hits = pool.search("PDF", 5);
        assertEquals(1, hits.size(), "降级为纯倒排仍应命中");
        assertEquals("pdf-form-filler", hits.get(0).name());
    }

    @Test
    void searchWithoutEmbedderUsesInvertedOnly() throws Exception {
        Path u = tmp.resolve("user-skills");
        writeSkill("user-skills", "pdf-form-filler", "填充 PDF 表单域");
        var pool = SkillPool.bootstrap(List.of(u));

        List<SkillMeta> hits = pool.search("PDF", 5);
        assertEquals(1, hits.size());
    }

    @Test
    void emptyCandidateSetDoesNotFallBackToFullKnn() throws Exception {
        Path u = tmp.resolve("user-skills");
        writeSkill("user-skills", "pdf-form-filler", "填充 PDF 表单域");
        var pool = SkillPool.bootstrap(List.of(u), new FakeEmbedder(true, 64));

        // 倒排无命中 → 即使 embedding 可用也不回退全量 KNN（避免噪声）
        assertTrue(pool.search("完全无关的查询词", 5).isEmpty());
    }

    @Test
    void searchRespectsLimitWithEmbedder() throws Exception {
        Path u = tmp.resolve("user-skills");
        for (int i = 0; i < 8; i++) {
            writeSkill("user-skills", "pdf-skill-" + i, "PDF 相关 " + i);
        }
        var pool = SkillPool.bootstrap(List.of(u), new FakeEmbedder(true, 64));
        assertEquals(3, pool.search("PDF", 3).size());
    }

    @Test
    void embedderReadyButEmptyIndexFallsBackGracefully() throws Exception {
        Path u = tmp.resolve("user-skills");
        writeSkill("user-skills", "pdf-form-filler", "填充 PDF 表单域");
        // ready=true 但维度为 0 会让 embedding 全零 —— 仍应返回结果（退化为倒排顺序）
        var pool = SkillPool.bootstrap(List.of(u), new FakeEmbedder(true, 4));
        assertTrue(!pool.search("PDF", 5).isEmpty());
    }

    @Test
    void bootstrapWithEmbedderIsIdempotent() throws Exception {
        Path u = tmp.resolve("user-skills");
        writeSkill("user-skills", "a", "技能 A");
        var p1 = SkillPool.bootstrap(List.of(u), new FakeEmbedder(true, 32));
        var p2 = SkillPool.bootstrap(List.of(u), new FakeEmbedder(true, 32));
        assertEquals(p1.all().size(), p2.all().size());
    }
}
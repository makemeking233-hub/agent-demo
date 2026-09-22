package com.example.agent.memory.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * VectorIndex 单元测试（add-embedding-rag T3）。
 *
 * <p>覆盖 {@link InMemoryVectorIndex} 与 {@link LuceneVectorIndex}（后者当前委派给前者，T8
 * 替换实现不影响测试契约）。
 */
class VectorIndexTest {

    @TempDir Path tmp;

    /** 正交基向量（512 维，每维是单位向量的某一维）。 */
    private static float[] unitVector(int dimensions, int index) {
        float[] v = new float[dimensions];
        v[index] = 1f;
        return v;
    }

    private static float[] nearVector(float[] base, int direction, float weight) {
        // base 基础上叠加一个不同方向的小权重，再归一化——得到与 base 相似但不完全相同的向量
        float[] v = new float[base.length];
        System.arraycopy(base, 0, v, 0, base.length);
        v[direction] = weight;
        double norm = 0d;
        for (float f : v) norm += (double) f * f;
        norm = Math.sqrt(norm);
        for (int i = 0; i < v.length; i++) v[i] = (float) (v[i] / norm);
        return v;
    }

    @Test
    void addAndSearchByCosine() {
        VectorIndex idx = new InMemoryVectorIndex(512);
        idx.add("a", unitVector(512, 0));
        idx.add("b", unitVector(512, 100));
        idx.add("c", unitVector(512, 250));

        List<VectorIndex.ScoredItem> result = idx.search(unitVector(512, 100), 3);

        assertEquals(3, result.size());
        assertEquals("b", result.get(0).id());
        assertTrue(result.get(0).score() > 0.99f, "完全相同的向量 cosine 应接近 1");
        assertEquals("a", result.get(1).id(), "正交向量 cosine 应为 0");
        assertEquals("c", result.get(2).id(), "正交向量 cosine 应为 0");
    }

    @Test
    void duplicateIdOverwrites() {
        VectorIndex idx = new InMemoryVectorIndex(512);
        idx.add("a", unitVector(512, 0));
        idx.add("a", unitVector(512, 100));
        assertEquals(1, idx.size(), "重复 id 应覆盖而非追加");

        List<VectorIndex.ScoredItem> result = idx.search(unitVector(512, 100), 1);
        assertEquals("a", result.get(0).id());
        assertTrue(result.get(0).score() > 0.99f, "应查到新版本");
    }

    @Test
    void searchReturnsTopKDescending() {
        VectorIndex idx = new InMemoryVectorIndex(512);
        float[] base = unitVector(512, 0);
        idx.add("near", nearVector(base, 200, 0.1f));  // 与 base 高度相似（不同方向小权重）
        idx.add("med", nearVector(base, 200, 0.5f));   // 中等相似
        idx.add("far", unitVector(512, 200));             // 正交 → cosine=0

        List<VectorIndex.ScoredItem> top2 = idx.search(base, 2);
        assertEquals(2, top2.size());
        assertEquals("near", top2.get(0).id(), "top-1 应是 cosine 最高的");
        assertEquals("med", top2.get(1).id(), "top-2 应是次高");
        assertTrue(top2.get(0).score() > top2.get(1).score(), "结果必须按分数降序");
    }

    @Test
    void searchReturnsEmptyWhenKLargerThanIndex() {
        VectorIndex idx = new InMemoryVectorIndex(512);
        idx.add("a", unitVector(512, 0));
        List<VectorIndex.ScoredItem> r = idx.search(unitVector(512, 0), 100);
        assertEquals(1, r.size(), "库内只有 1 条，k=100 也只返回 1");
    }

    @Test
    void searchReturnsEmptyForEmptyIndex() {
        VectorIndex idx = new InMemoryVectorIndex(512);
        assertEquals(0, idx.search(unitVector(512, 0), 10).size());
        assertEquals(0, idx.size());
    }

    @Test
    void zeroOrNegativeKReturnsEmpty() {
        VectorIndex idx = new InMemoryVectorIndex(512);
        idx.add("a", unitVector(512, 0));
        assertEquals(0, idx.search(unitVector(512, 0), 0).size());
        assertEquals(0, idx.search(unitVector(512, 0), -5).size());
    }

    @Test
    void dimensionMismatchRejected() {
        VectorIndex idx = new InMemoryVectorIndex(512);
        assertThrows(IllegalArgumentException.class,
                () -> idx.add("a", new float[256]), "add 维度不匹配应抛");
        idx.add("a", unitVector(512, 0));
        assertThrows(IllegalArgumentException.class,
                () -> idx.search(new float[256], 1), "search 维度不匹配应抛");
        assertThrows(IllegalArgumentException.class,
                () -> idx.search(null, 1), "search null query 应抛");
    }

    @Test
    void closedIndexRejectsOperations() {
        VectorIndex idx = new InMemoryVectorIndex(512);
        idx.close();
        assertThrows(IllegalStateException.class, () -> idx.add("a", unitVector(512, 0)));
        assertThrows(IllegalStateException.class, () -> idx.search(unitVector(512, 0), 1));
    }

    @Test
    void closeIsIdempotent() {
        VectorIndex idx = new InMemoryVectorIndex(512);
        idx.close();
        idx.close();  // 不应抛
    }

    @Test
    void cosineReturnsZeroForZeroVector() {
        // 防御性：任一向量为零向量时不应返回 NaN
        float[] zero = new float[512];
        float[] nonzero = unitVector(512, 5);
        assertEquals(0f, InMemoryVectorIndex.cosine(zero, nonzero));
        assertEquals(0f, InMemoryVectorIndex.cosine(nonzero, zero));
        assertEquals(0f, InMemoryVectorIndex.cosine(zero, zero));
    }

    @Test
    void luceneVectorIndexDelegatesToInMemory() {
        // T3 阶段：LuceneVectorIndex 委派给 InMemoryVectorIndex；T8 替换实现时不影响契约
        VectorIndex idx = new LuceneVectorIndex(tmp.resolve(".vectors"), 512);
        idx.add("a", unitVector(512, 0));
        idx.add("b", unitVector(512, 100));
        assertEquals(2, idx.size());

        List<VectorIndex.ScoredItem> r = idx.search(unitVector(512, 100), 1);
        assertEquals("b", r.get(0).id());
        assertTrue(r.get(0).score() > 0.99f);
    }

    @Test
    void invalidDimensionsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new InMemoryVectorIndex(0));
        assertThrows(IllegalArgumentException.class, () -> new InMemoryVectorIndex(-1));
        assertThrows(IllegalArgumentException.class,
                () -> new LuceneVectorIndex(tmp.resolve(".vectors"), 0));
    }
}

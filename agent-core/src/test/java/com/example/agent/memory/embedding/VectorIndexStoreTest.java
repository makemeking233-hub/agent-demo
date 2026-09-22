package com.example.agent.memory.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.memory.MemoryDir;
import com.example.agent.memory.MemoryEntry;
import com.example.agent.memory.MemoryScope;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * VectorIndexStore 单元测试（add-embedding-rag T4）。
 *
 * <p><b>语义边界</b>：T4 阶段 {@link LuceneVectorIndex} 委派给内存索引（无持久化），
 * 故 mtime 缓存的价值体现在**同进程内**避免重复 embed；跨进程跳过 embed 需要索引自身持久化
 * （由 T8 接入 Lucene HNSW 后生效）。本测试覆盖进程内行为与缓存文件读写契约。
 *
 * <p><b>数据隔离</b>（全局规则 §10）：全部路径来自 {@link TempDir}，不触碰用户真实数据。
 */
class VectorIndexStoreTest {

    @TempDir Path tmp;

    /** 可计数的假 embedding provider（512 维，用文本 hashCode 生成确定性向量）。 */
    private static final class CountingProvider implements EmbeddingProvider {
        final AtomicInteger calls = new AtomicInteger();
        private final boolean ready;

        CountingProvider(boolean ready) {
            this.ready = ready;
        }

        @Override
        public float[] embed(String text) {
            calls.incrementAndGet();
            float[] v = new float[512];
            int h = text == null ? 0 : text.hashCode();
            v[Math.abs(h) % 512] = 1f;
            return v;
        }

        @Override
        public int dimensions() {
            return 512;
        }

        @Override
        public boolean isReady() {
            return ready;
        }
    }

    /** 建 scope 目录并写入 .md 文件。 */
    private MemoryDir prepareScope(String... filenames) throws Exception {
        Path scopeDir = tmp.resolve("scope-" + System.nanoTime());
        MemoryDir dir = new MemoryDir(scopeDir, MemoryScope.USER);
        for (String f : filenames) {
            Files.writeString(dir.entryFile(f), "# " + f + "\n内容\n");
        }
        return dir;
    }

    private static List<MemoryEntry> entries(String... filenames) {
        return java.util.Arrays.stream(filenames)
                .map(f -> new MemoryEntry(f.replace(".md", ""), "描述 " + f, f, MemoryScope.USER))
                .toList();
    }

    private static Path mtimeCache(MemoryDir dir) {
        return dir.dir().resolve(".vectors").resolve("mtime.json");
    }

    @Test
    void firstLoadEmbedsAllEntries() throws Exception {
        MemoryDir dir = prepareScope("a.md", "b.md", "c.md");
        var provider = new CountingProvider(true);
        var store = new VectorIndexStore(provider, 512);

        VectorIndex idx = store.indexFor(MemoryScope.USER, dir, entries("a.md", "b.md", "c.md"));

        assertEquals(3, provider.calls.get(), "首次加载应全部 embed");
        assertEquals(3, idx.size());
        store.close();
    }

    @Test
    void secondCallSameStoreSkipsReembedding() throws Exception {
        MemoryDir dir = prepareScope("a.md", "b.md");
        var provider = new CountingProvider(true);
        var store = new VectorIndexStore(provider, 512);

        store.indexFor(MemoryScope.USER, dir, entries("a.md", "b.md"));
        assertEquals(2, provider.calls.get());

        // 同 store 二次调用：内存索引命中 + mtime 未变 → 跳过
        store.indexFor(MemoryScope.USER, dir, entries("a.md", "b.md"));
        assertEquals(2, provider.calls.get(), "同 store 二次调用不应重复 embed");
        store.close();
    }

    @Test
    void changedMtimeTriggersReembedding() throws Exception {
        MemoryDir dir = prepareScope("a.md", "b.md");
        var provider = new CountingProvider(true);
        var store = new VectorIndexStore(provider, 512);

        store.indexFor(MemoryScope.USER, dir, entries("a.md", "b.md"));
        assertEquals(2, provider.calls.get());

        // 修改 a.md → mtime 变化
        Thread.sleep(10);
        Files.writeString(dir.entryFile("a.md"), "# a.md\n新内容\n");

        store.indexFor(MemoryScope.USER, dir, entries("a.md", "b.md"));
        assertEquals(3, provider.calls.get(), "只有 a.md 的 mtime 变了，应只重算 1 条");

        // 再次调用（此时 a.md 的 mtime 已被记录）→ 不再重算
        store.indexFor(MemoryScope.USER, dir, entries("a.md", "b.md"));
        assertEquals(3, provider.calls.get(), "mtime 稳定后不应继续重算");
        store.close();
    }

    @Test
    void corruptedMtimeCacheDoesNotThrow() throws Exception {
        MemoryDir dir = prepareScope("a.md", "b.md");
        var provider = new CountingProvider(true);
        var store = new VectorIndexStore(provider, 512);
        store.indexFor(MemoryScope.USER, dir, entries("a.md", "b.md"));
        store.flush();
        // 必须先关掉 store，否则下一个 store 打开同一 Lucene 目录会撞 write.lock
        store.close();

        // 损坏缓存文件
        Path cache = mtimeCache(dir);
        assertTrue(Files.exists(cache), "flush 后应存在 mtime.json");
        Files.writeString(cache, "{ this is not valid json ");

        // 新建 store（模拟重启）：缓存损坏 → 记 WARN 而非抛异常；索引为空 → 全量重算
        var provider2 = new CountingProvider(true);
        var store2 = new VectorIndexStore(provider2, 512);
        assertNotNull(store2.indexFor(MemoryScope.USER, dir, entries("a.md", "b.md")));
        assertEquals(2, provider2.calls.get(), "缓存损坏时应自愈为全量重算");
        store2.close();
    }

    @Test
    void unavailableProviderYieldsEmptyIndexWithoutThrowing() throws Exception {
        MemoryDir dir = prepareScope("a.md");
        var provider = new CountingProvider(false);  // isReady=false
        var store = new VectorIndexStore(provider, 512);

        VectorIndex idx = store.indexFor(MemoryScope.USER, dir, entries("a.md"));

        assertEquals(0, provider.calls.get(), "provider 不可用不应调用 embed");
        assertEquals(0, idx.size(), "不可用时索引为空");
        store.close();
    }

    @Test
    void missingEntryFileIsSkipped() throws Exception {
        MemoryDir dir = prepareScope("a.md");
        var provider = new CountingProvider(true);
        var store = new VectorIndexStore(provider, 512);

        VectorIndex idx = store.indexFor(MemoryScope.USER, dir, entries("a.md", "ghost.md"));

        assertEquals(1, provider.calls.get(), "只有存在的 a.md 被 embed");
        assertEquals(1, idx.size());
        store.close();
    }

    @Test
    void flushPersistsMtimeCache() throws Exception {
        MemoryDir dir = prepareScope("a.md", "b.md");
        var provider = new CountingProvider(true);
        var store = new VectorIndexStore(provider, 512);
        store.indexFor(MemoryScope.USER, dir, entries("a.md", "b.md"));
        store.flush();

        Path cache = mtimeCache(dir);
        assertTrue(Files.exists(cache), "flush 应把 mtime 缓存落盘");
        String content = Files.readString(cache);
        assertTrue(content.contains("a.md"), "缓存应含条目文件名");
        assertTrue(content.contains("b.md"));
        store.close();
    }

    @Test
    void scopesAreIsolated() throws Exception {
        MemoryDir userDir = prepareScope("u.md");
        MemoryDir projDir = prepareScope("p.md");
        var provider = new CountingProvider(true);
        var store = new VectorIndexStore(provider, 512);

        VectorIndex userIdx = store.indexFor(MemoryScope.USER, userDir, entries("u.md"));
        VectorIndex projIdx =
                store.indexFor(MemoryScope.PROJECT, projDir, List.of(
                        new MemoryEntry("p", "描述", "p.md", MemoryScope.PROJECT)));

        assertEquals(1, userIdx.size());
        assertEquals(1, projIdx.size());
        store.close();
    }

    @Test
    void searchAfterLoadReturnsRelevantEntry() throws Exception {
        MemoryDir dir = prepareScope("java17.md", "rust.md");
        var provider = new CountingProvider(true);
        var store = new VectorIndexStore(provider, 512);

        VectorIndex idx = store.indexFor(MemoryScope.USER, dir, entries("java17.md", "rust.md"));
        // CountingProvider 用 text.hashCode() 决定向量，故用同文本查回自身
        List<VectorIndex.ScoredItem> hits = idx.search(provider.embed("java17 描述 java17.md"), 2);

        assertEquals(2, hits.size());
        assertEquals("java17.md", hits.get(0).id(), "同文本应命中自身（cosine=1）");
        store.close();
    }

    @Test
    void closeIsIdempotent() throws Exception {
        MemoryDir dir = prepareScope("a.md");
        var store = new VectorIndexStore(new CountingProvider(true), 512);
        store.indexFor(MemoryScope.USER, dir, entries("a.md"));
        store.close();
        store.close();  // 不应抛
    }

    @Test
    void emptyEntriesYieldsEmptyIndex() throws Exception {
        MemoryDir dir = prepareScope("a.md");
        var provider = new CountingProvider(true);
        var store = new VectorIndexStore(provider, 512);

        VectorIndex idx = store.indexFor(MemoryScope.USER, dir, List.of());

        assertEquals(0, provider.calls.get());
        assertEquals(0, idx.size());
        store.close();
    }
}

package com.example.agent.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.config.AgentConfig;
import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.FinishReason;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.StreamChunk;
import com.example.agent.memory.embedding.EmbeddingProvider;
import com.example.agent.memory.embedding.VectorIndexStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MemoryRetriever 三层召回测试（add-embedding-rag T5）。
 *
 * <p>覆盖：embedding 不可用时退化为两层（与 v0.4 一致）；字面不足时 embedding 补足；
 * 字面 ∪ embedding 已满时跳过 sideQuery；仍不足时 sideQuery 触发；embedding 异常静默降级。
 *
 * <p><b>数据隔离</b>（全局规则 §10）：全部路径来自 {@link TempDir}。
 */
class MemoryRetrieverEmbeddingTest {

    @TempDir Path tmp;

    private static final int DIMS = 8;

    private AgentConfig.SideQuery sq(boolean enabled) {
        return new AgentConfig.SideQuery(enabled, 8, 3);
    }

    /** 假 embedding：所有文本返回同一个归一化向量（cosine 恒为 1），记录调用次数。 */
    private static final class FlatEmbedding implements EmbeddingProvider {
        final AtomicInteger calls = new AtomicInteger();
        private final boolean ready;

        FlatEmbedding(boolean ready) {
            this.ready = ready;
        }

        @Override
        public float[] embed(String text) {
            calls.incrementAndGet();
            float[] v = new float[DIMS];
            v[0] = 1f;
            return v;
        }

        @Override
        public int dimensions() {
            return DIMS;
        }

        @Override
        public boolean isReady() {
            return ready;
        }
    }

    /** 抛异常的假 embedding（验证静默降级）。 */
    private static final class ExplodingEmbedding implements EmbeddingProvider {
        @Override
        public float[] embed(String text) {
            throw new IllegalStateException("simulated embedding failure");
        }

        @Override
        public int dimensions() {
            return DIMS;
        }

        @Override
        public boolean isReady() {
            return true;
        }
    }

    /** 建 scope 目录 + MEMORY.md（含指定条目），并写入对应 .md 正文文件。 */
    private MemoryDir scopeWith(MemoryEntry... entries) throws Exception {
        Path base = tmp.resolve("base-" + System.nanoTime());
        MemoryDir dir = new MemoryDir(base.resolve("mem"), MemoryScope.USER);
        new MemoryIndex(dir.indexFile(), MemoryScope.USER).write(List.of(entries));
        for (MemoryEntry e : entries) {
            Files.writeString(dir.entryFile(e.filename()), "# " + e.title() + "\n");
        }
        return dir;
    }

    private static MemoryEntry entry(String title, String desc, String file) {
        return new MemoryEntry(title, desc, file, MemoryScope.USER);
    }

    /** 计数用的假 LLM provider（sideQuery 调用次数）。 */
    private static final class CountingLlm implements LlmProvider {
        final AtomicInteger calls = new AtomicInteger();
        private final String filenameToReturn;

        CountingLlm(String filenameToReturn) {
            this.filenameToReturn = filenameToReturn;
        }

        @Override
        public String name() {
            return "counting";
        }

        @Override
        public Flux<StreamChunk> streamChat(ChatRequest request) {
            calls.incrementAndGet();
            return Flux.just(
                    new StreamChunk.TextDelta(filenameToReturn + "\n"),
                    new StreamChunk.Finished(FinishReason.STOP, null));
        }

        @Override
        public int contextWindow() {
            return 1000;
        }

        @Override
        public int maxOutputTokens() {
            return 1000;
        }
    }

    // ---- 用例 ----

    /** embedding 参数为 null 时行为与 v0.4 一致（仅字面 + sideQuery）。 */
    @Test
    void nullEmbeddingDegradesToTwoLayers() throws Exception {
        MemoryDir dir = scopeWith(entry("Java 17 安装", "JDK 安装步骤", "java17.md"));

        var retriever = new MemoryRetriever(null, "deepseek-chat", new MemoryRecall(), sq(true));
        Map<MemoryScope, List<MemoryEntry>> result = retriever.retrieve("安装 Java", List.of(dir), 5);

        assertTrue(result.containsKey(MemoryScope.USER));
        assertEquals("java17.md", result.get(MemoryScope.USER).get(0).filename());
    }

    /** embedding 不可用（isReady=false）时不调用 embed，退化为两层。 */
    @Test
    void unavailableEmbeddingSkipsEmbeddingStage() throws Exception {
        MemoryDir dir = scopeWith(entry("Java 17 安装", "JDK 安装步骤", "java17.md"));
        var embedding = new FlatEmbedding(false);
        var store = new VectorIndexStore(embedding, DIMS);

        var retriever = new MemoryRetriever(
                null, "deepseek-chat", new MemoryRecall(), sq(true), embedding, store, 10);
        Map<MemoryScope, List<MemoryEntry>> result = retriever.retrieve("安装 Java", List.of(dir), 5);

        assertEquals(0, embedding.calls.get(), "isReady=false 时不应调用 embed");
        assertTrue(result.containsKey(MemoryScope.USER));
        store.close();
    }

    /** 字面完全未命中时，embedding 粗排补足（此时不调 sideQuery）。 */
    @Test
    void embeddingSupplementsWhenLiteralMisses() throws Exception {
        // 条目与 query 无字面重叠（"JVM" vs "java"，中文部分也不重叠）
        MemoryDir dir = scopeWith(entry("JVM 运行时", "虚拟机参数配置", "jvm.md"));
        var embedding = new FlatEmbedding(true);
        var store = new VectorIndexStore(embedding, DIMS);
        var llm = new CountingLlm("jvm.md");

        var retriever = new MemoryRetriever(
                llm, "deepseek-chat", new MemoryRecall(), sq(true), embedding, store, 10);
        Map<MemoryScope, List<MemoryEntry>> result = retriever.retrieve("java", List.of(dir), 5);

        assertTrue(result.containsKey(MemoryScope.USER));
        assertEquals("jvm.md", result.get(MemoryScope.USER).get(0).filename(),
                "embedding 粗排应召回 jvm.md");
        assertTrue(embedding.calls.get() > 0, "应调用 embed");
        store.close();
    }

    /** 字面 ∪ embedding 已召满时跳过 sideQuery（节省 LLM 调用）。 */
    @Test
    void fullResultSkipsSideQuery() throws Exception {
        MemoryDir dir = scopeWith(
                entry("JVM 运行时", "虚拟机参数配置", "jvm.md"),
                entry("Kotlin 协程", "挂起函数", "kotlin.md"),
                entry("Rust 所有权", "borrow checker", "rust.md"));
        var embedding = new FlatEmbedding(true);
        var store = new VectorIndexStore(embedding, DIMS);
        var llm = new CountingLlm("jvm.md");

        // k=2，embedding 能补足到 2 条 → 不应触发 sideQuery
        var retriever = new MemoryRetriever(
                llm, "deepseek-chat", new MemoryRecall(), sq(true), embedding, store, 10);
        Map<MemoryScope, List<MemoryEntry>> result = retriever.retrieve("java", List.of(dir), 2);

        assertEquals(2, result.get(MemoryScope.USER).size());
        assertEquals(0, llm.calls.get(), "已召满时不应调用 sideQuery");
        store.close();
    }

    /** 三层都开启且 embedding 关掉时才走 sideQuery（回归既有行为）。 */
    @Test
    void sideQueryStillWorksWhenEmbeddingDisabled() throws Exception {
        MemoryDir dir = scopeWith(
                entry("JVM 运行时", "虚拟机参数配置", "jvm.md"),
                entry("Kotlin 协程", "挂起函数", "kotlin.md"),
                entry("Rust 所有权", "borrow checker", "rust.md"));
        var llm = new CountingLlm("jvm.md");

        // 不传 embedding（null）→ 走字面 + sideQuery 两层
        var retriever = new MemoryRetriever(llm, "deepseek-chat", new MemoryRecall(), sq(true));
        Map<MemoryScope, List<MemoryEntry>> result = retriever.retrieve("java", List.of(dir), 2);

        assertTrue(llm.calls.get() > 0, "embedding 缺失时应触发 sideQuery");
        assertTrue(result.get(MemoryScope.USER).stream()
                .anyMatch(e -> e.filename().equals("jvm.md")));
    }

    /** embedding 抛异常时静默降级，不阻断召回（回落到 sideQuery）。 */
    @Test
    void explodingEmbeddingDegradesGracefully() throws Exception {
        // 放满 minCandidates=3 条候选，使 sideQuery 在 embedding 失败后能接管
        MemoryDir dir = scopeWith(
                entry("JVM 运行时", "虚拟机参数配置", "jvm.md"),
                entry("Kotlin 协程", "挂起函数", "kotlin.md"),
                entry("Rust 所有权", "borrow checker", "rust.md"));
        var embedding = new ExplodingEmbedding();
        var store = new VectorIndexStore(embedding, DIMS);
        var llm = new CountingLlm("jvm.md");

        var retriever = new MemoryRetriever(
                llm, "deepseek-chat", new MemoryRecall(), sq(true), embedding, store, 10);

        // 不应抛异常
        Map<MemoryScope, List<MemoryEntry>> result = retriever.retrieve("java", List.of(dir), 5);

        assertTrue(llm.calls.get() > 0, "embedding 异常后应回落到 sideQuery");
        assertFalse(result.isEmpty() || result.get(MemoryScope.USER).isEmpty());
        assertTrue(result.get(MemoryScope.USER).stream()
                .anyMatch(e -> e.filename().equals("jvm.md")));
        store.close();
    }

    /** 字面已召满时连 embedding 都不该跑（最大化短路）。 */
    @Test
    void fullLiteralSkipsEmbeddingToo() throws Exception {
        MemoryDir dir = scopeWith(entry("Java 17 安装", "JDK 安装步骤", "java17.md"));
        var embedding = new FlatEmbedding(true);
        var store = new VectorIndexStore(embedding, DIMS);
        var llm = new CountingLlm("java17.md");

        var retriever = new MemoryRetriever(
                llm, "deepseek-chat", new MemoryRecall(), sq(true), embedding, store, 10);
        // k=1，字面直接命中 1 条 → 应短路
        Map<MemoryScope, List<MemoryEntry>> result = retriever.retrieve("安装 Java", List.of(dir), 1);

        assertEquals(1, result.get(MemoryScope.USER).size());
        assertEquals(0, embedding.calls.get(), "字面已召满不应调用 embed");
        assertEquals(0, llm.calls.get(), "字面已召满不应调用 sideQuery");
        store.close();
    }
}

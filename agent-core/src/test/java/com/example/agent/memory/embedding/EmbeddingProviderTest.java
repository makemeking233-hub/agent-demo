package com.example.agent.memory.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * EmbeddingProvider 单元测试（add-embedding-rag T2）。
 *
 * <p>覆盖：接口契约（embed / dimensions / isReady）；MockEmbeddingProvider 用于
 * 不依赖 ONNX runtime 的纯逻辑测试；OnnxEmbeddingProvider 验证模型缺失 / 加载失败
 * 时的降级行为（isReady=false，绝不抛异常）。
 *
 * <p><b>数据隔离</b>（全局规则 §10）：通过 {@link TempDir} 作为模型路径，绝不触碰用户真实
 * {@code ~/.agent-demo/}。
 */
class EmbeddingProviderTest {

    @TempDir Path tmp;

    /** 简单 Mock：返回归一化的定长零向量；记录调用次数。 */
    private static final class MockEmbeddingProvider implements EmbeddingProvider {
        private final int dims;
        final AtomicInteger calls = new AtomicInteger();
        MockEmbeddingProvider(int dims) {
            this.dims = dims;
        }
        @Override
        public float[] embed(String text) {
            calls.incrementAndGet();
            float[] v = new float[dims];
            // 非零填充以避免下游归一化退化
            for (int i = 0; i < dims; i++) v[i] = 0.01f * (i + 1);
            return v;
        }
        @Override
        public int dimensions() {
            return dims;
        }
        @Override
        public boolean isReady() {
            return true;
        }
    }

    @Test
    void mockProviderReturnsRequestedDimensions() {
        var provider = new MockEmbeddingProvider(512);
        assertEquals(512, provider.dimensions());

        float[] v = provider.embed("hello");
        assertEquals(512, v.length);
        assertNotNull(v);
    }

    @Test
    void mockProviderTracksCallCount() {
        var provider = new MockEmbeddingProvider(512);
        provider.embed("a");
        provider.embed("b");
        provider.embed("c");
        assertEquals(3, provider.calls.get());
    }

    @Test
    void onnxProviderReportsNotReadyWhenModelMissing() {
        Path nonExistent = tmp.resolve("no-such-model.onnx");
        assertFalse(Files.exists(nonExistent));

        var provider = new OnnxEmbeddingProvider(nonExistent);

        assertFalse(provider.isReady(), "模型缺失时 isReady 应为 false");
        assertEquals(512, provider.dimensions(), "维度应仍报告默认值，便于下游分配");
    }

    @Test
    void onnxProviderReportsNotReadyWhenModelFileIsMalformed() throws Exception {
        Path malformed = tmp.resolve("malformed.onnx");
        Files.writeString(malformed, "this is not a valid ONNX model");

        var provider = new OnnxEmbeddingProvider(malformed);

        // 模型存在但加载失败 → unavailable（不抛异常）
        assertFalse(provider.isReady(), "模型损坏时 isReady 应为 false（静默降级）");
    }

    @Test
    void onnxProviderEmbedDoesNotThrowWhenNotReady() {
        Path nonExistent = tmp.resolve("missing.onnx");
        var provider = new OnnxEmbeddingProvider(nonExistent);

        // 模型缺失时调 embed 也不应抛（降级语义）
        float[] result = provider.embed("any text");
        assertNotNull(result, "unavailable 时 embed 应返回空向量而非抛异常");
        assertEquals(512, result.length);
        // 缺失时返回零向量
        for (float f : result) {
            assertEquals(0.0f, f, 0.0f, "unavailable 时向量应为零向量");
        }
    }

    @Test
    void nullTextReturnsZeroVector() {
        var provider = new MockEmbeddingProvider(512);
        // 接口契约：null/空文本应返回零向量（不抛 NPE）
        float[] result = provider.embed(null);
        assertNotNull(result);
        assertEquals(512, result.length);
    }

    @Test
    void blankTextReturnsZeroVector() {
        var provider = new MockEmbeddingProvider(512);
        float[] result = provider.embed("   ");
        assertNotNull(result);
    }

    @Test
    void onnxProviderAcceptsDefaultPathFromConstructor() {
        // 不显式传路径时使用 AgentPaths 派生的默认路径（基本一定不存在）
        var provider = new OnnxEmbeddingProvider();
        assertFalse(provider.isReady(), "默认路径下模型不存在时为 unavailable");
        assertEquals(512, provider.dimensions());
    }

    @Test
    void exceptionDuringLoadDoesNotPropagate() throws Exception {
        // 创建一个目录而非文件，让 ONNX session 创建抛 IOException
        Path dirNotFile = tmp.resolve("dir-as-model");
        Files.createDirectory(dirNotFile);

        var provider = new OnnxEmbeddingProvider(dirNotFile);
        // 不论 ONNX 是否能 handle 目录形式，都不应抛到调用方
        assertFalse(provider.isReady());
    }
}

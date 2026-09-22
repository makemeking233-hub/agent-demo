package com.example.agent.memory.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * ONNX 真实推理端到端测试（add-embedding-rag T8b.4）。
 *
 * <p><b>默认跳过</b>：需要真实模型文件（~95MB）与 vocab.txt。仅当本地存在模型时才运行
 *（用 {@link EnabledIf} 条件），因此 CI 不会因缺模型而失败。
 *
 * <p>模型准备：
 *
 * <pre>
 * bash scripts/download-embedding-model.sh
 * </pre>
 *
 * <p>本测试验证的是 embedding 的**核心价值**：同义/近义文本的向量相似度应显著高于无关文本。
 * 这是纯字面召回做不到的（「代码规范」与「编码风格」无 token 重叠）。
 */
class OnnxEmbeddingProviderE2ETest {

    /**
     * 模型路径。
     *
     * <p>优先读系统属性 {@code agent.embedding.model.path}（便于在没有把模型放进测试 agentHome 时
     * 指定位置）；否则用 provider 的默认路径。
     *
     * <p>注意：surefire 已把 {@code agent.demo.home} 固定为 {@code target/test-home}
     * （见 agent-core/pom.xml，目的是让"忘记隔离"的测试也安全），因此默认路径下不会有模型，
     * 除非显式把模型放进去或用本属性指定。
     */
    private static Path modelPath() {
        String override = System.getProperty("agent.embedding.model.path");
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        return OnnxEmbeddingProvider.defaultModelPath();
    }

    /** 词表路径（与模型同目录）。 */
    private static Path vocabPath() {
        return modelPath().resolveSibling("vocab.txt");
    }

    /** 条件：模型（含 external data）与词表都存在且体积合理。 */
    @SuppressWarnings("unused")
    static boolean modelAvailable() {
        try {
            Path m = modelPath();
            Path v = vocabPath();
            if (!Files.isRegularFile(m) || !Files.isRegularFile(v)) return false;
            // onnx-community 的导出使用 external data：model.onnx 仅 ~41KB，
            // 真实权重在同目录的 model.onnx_data（~95MB）
            long total = Files.size(m);
            Path data = m.resolveSibling("model.onnx_data");
            if (Files.isRegularFile(data)) {
                total += Files.size(data);
            }
            return total > 1024L * 1024L;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @EnabledIf("modelAvailable")
    void modelLoadsAndProducesNormalizedVector() {
        var provider = new OnnxEmbeddingProvider(modelPath());

        assertTrue(provider.isReady(), "模型与词表齐备时 isReady 应为 true");
        assertEquals(512, provider.dimensions());

        float[] v = provider.embed("安装 Java");

        assertEquals(512, v.length);
        // L2 归一化后模长应为 1
        double norm = 0d;
        for (float f : v) norm += (double) f * f;
        norm = Math.sqrt(norm);
        assertEquals(1.0d, norm, 1e-3, "输出应为 L2 归一化向量");

        // 不应是零向量
        boolean allZero = true;
        for (float f : v) {
            if (f != 0f) {
                allZero = false;
                break;
            }
        }
        assertTrue(!allZero, "真实推理不应返回零向量");
    }

    @Test
    @EnabledIf("modelAvailable")
    void identicalTextProducesIdenticalVector() {
        var provider = new OnnxEmbeddingProvider(modelPath());

        float[] a = provider.embed("如何配置 DeepSeek API key");
        float[] b = provider.embed("如何配置 DeepSeek API key");

        assertEquals(a.length, b.length);
        for (int i = 0; i < a.length; i++) {
            assertEquals(a[i], b[i], 1e-6, "同一输入的向量应完全一致（确定性推理）");
        }
    }

    /**
     * 核心价值验证：同义改写（无字面重叠）的相似度应显著高于无关文本。
     *
     * <p>「代码规范」与「编码风格」没有共同的 token（字面召回为 0），但语义相近；
     * 而「今天的天气怎么样」与「代码规范」无关。embedding 应能区分这两组。
     */
    @Test
    @EnabledIf("modelAvailable")
    void synonymsScoreHigherThanUnrelated() {
        var provider = new OnnxEmbeddingProvider(modelPath());

        float[] query = provider.embed("代码规范");
        float[] synonym = provider.embed("编码风格约定");
        float[] unrelated = provider.embed("今天天气真好适合出去散步");

        float simSynonym = dot(query, synonym);
        float simUnrelated = dot(query, unrelated);

        assertTrue(
                simSynonym > simUnrelated,
                "同义文本相似度应高于无关文本（synonym=" + simSynonym + ", unrelated=" + simUnrelated + "）");
    }

    /** 中文语义：相近主题的句子相似度应高于跨主题。 */
    @Test
    @EnabledIf("modelAvailable")
    void relatedTopicScoresHigherThanDifferentTopic() {
        var provider = new OnnxEmbeddingProvider(modelPath());

        float[] javaDoc = provider.embed("Java 17 的 JDK 安装与环境变量配置");

        float[] sameTopic = provider.embed("如何安装 JDK 并设置 JAVA_HOME");
        float[] otherTopic = provider.embed("红烧肉的家常做法步骤");

        assertTrue(
                dot(javaDoc, sameTopic) > dot(javaDoc, otherTopic),
                "同主题相似度应高于跨主题");
    }

    /** 归一化向量的 cosine 等价于点积（这是选 L2 归一化的原因：检索更快）。 */
    private static float dot(float[] a, float[] b) {
        float s = 0f;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) s += a[i] * b[i];
        return s;
    }
}

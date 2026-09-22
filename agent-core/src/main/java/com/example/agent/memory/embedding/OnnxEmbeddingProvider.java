package com.example.agent.memory.embedding;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.agent.config.AgentPaths;

/**
 * 本地 ONNX embedding 提供者（add-embedding-rag T2/T8b）。
 *
 * <p>封装 bge-small-zh-v1.5 的完整推理链：
 *
 * <pre>
 * text -> BertWordPieceTokenizer -> [input_ids, attention_mask, token_type_ids]
 *      -> OrtSession.run -> last_hidden_state[1, seq, 512]
 *      -> CLS 池化（取 [0][0]） -> L2 归一化 -> float[512]
 * </pre>
 *
 * <p><b>懒加载</b>：首次 {@link #embed(String)} 或 {@link #isReady()} 时加载 ONNX session 与词表；
 * 成功后跨调用复用（OrtSession 线程安全）。加载走 {@code synchronized} 保证只发生一次。
 *
 * <p><b>降级语义</b>（与既有 "sideQuery 失败静默降级" 一致）：
 *
 * <ul>
 *   <li>模型文件 / 词表缺失 → {@link #isReady()}=false；{@link #embed} 返回零向量
 *   <li>加载抛异常（文件损坏 / ONNX native lib 不可用）→ 同上，记 WARN
 *   <li>推理抛异常 → 记 WARN 并返回零向量
 * </ul>
 *
 * <p><b>安全约束（T8b.0 教训）</b>：任何"推理不可用"的情形都必须让 {@code isReady()=false}。
 * 若在推理不可用时返回 {@code true}，{@code embed()} 的零向量会让 KNN 任意返回 k 条
 *（零向量与所有条目的 cosine 都是 0），反而往召回结果注入无关条目。
 */
public class OnnxEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(OnnxEmbeddingProvider.class);

    /** 默认 embedding 维度（bge-small-zh-v1.5 是 512）。 */
    private static final int DEFAULT_DIMENSIONS = 512;

    /** ONNX 模型在 agentHome 下的子路径（git 忽略）。 */
    private static final String MODEL_RELATIVE_PATH = "models/bge-small-zh-v1.5/model.onnx";

    /** 词表文件名（与模型同目录）。 */
    private static final String VOCAB_FILE_NAME = "vocab.txt";

    /**
     * ONNX external data 文件名（与 model.onnx 同目录）。
     *
     * <p>onnx-community 导出的 bge-small-zh 采用 external data 模式：
     * {@code model.onnx} 仅约 41KB（结构与引用），真实权重在 {@code model.onnx_data}（约 95MB）。
     * 缺失该文件时 ONNX 加载会失败。
     */
    private static final String EXTERNAL_DATA_FILE_NAME = "model.onnx_data";

    /** 轻量健康检查的文件大小阈值（字节）。 */
    private static final long MIN_HEALTHY_MODEL_BYTES = 1024L * 1024L;

    /** 模型输入名候选（不同导出脚本命名略有差异）。 */
    private static final String[] INPUT_IDS_NAMES = {"input_ids", "inputIds", "ids"};
    private static final String[] ATTENTION_MASK_NAMES = {"attention_mask", "attentionMask", "mask"};
    private static final String[] TOKEN_TYPE_NAMES = {"token_type_ids", "tokenTypeIds", "segment_ids"};

    /** 模型输出名候选。 */
    private static final String[] OUTPUT_NAMES = {
        "last_hidden_state", "token_embeddings", "hidden_states", "output"
    };

    private final Path modelPath;
    private final Path vocabPath;

    private volatile boolean loadAttempted = false;
    private volatile boolean unavailable = true;

    /** ONNX 运行时（进程级单例，由 OrtEnvironment 管理）。 */
    private OrtEnvironment env;
    private OrtSession session;
    private BertWordPieceTokenizer tokenizer;

    /** 实际可用的输入 / 输出名（加载时探测一次）。 */
    private String inputIdsName;
    private String attentionMaskName;
    private String tokenTypeName;
    private String outputName;

    public OnnxEmbeddingProvider() {
        this(defaultModelPath());
    }

    public OnnxEmbeddingProvider(Path modelPath) {
        this(modelPath, modelPath == null ? null : modelPath.resolveSibling(VOCAB_FILE_NAME));
    }

    public OnnxEmbeddingProvider(Path modelPath, Path vocabPath) {
        this.modelPath = modelPath;
        this.vocabPath = vocabPath;
    }

    /** 解析默认模型路径：{@code <agentHome>/models/bge-small-zh-v1.5/model.onnx}。 */
    public static Path defaultModelPath() {
        return Path.of(AgentPaths.agentHome().toString(), "models", "bge-small-zh-v1.5", "model.onnx");
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[DEFAULT_DIMENSIONS];
        }
        if (!ensureLoaded()) {
            return new float[DEFAULT_DIMENSIONS];
        }
        try {
            return runInference(text);
        } catch (Exception e) {
            log.warn("embedding inference failed for text (len={}): {}", text.length(), e.toString());
            return new float[DEFAULT_DIMENSIONS];
        }
    }

    @Override
    public int dimensions() {
        return DEFAULT_DIMENSIONS;
    }

    @Override
    public boolean isReady() {
        return ensureLoaded();
    }

    /**
     * 懒加载 ONNX session + 词表。仅执行一次（线程安全）。
     *
     * @return {@code true} 表示可正常推理
     */
    private synchronized boolean ensureLoaded() {
        if (loadAttempted) return !unavailable;
        loadAttempted = true;
        try {
            if (!Files.isRegularFile(modelPath)) {
                log.warn(
                        "embedding model not found at {}; memory retrieval falls back to literal + sideQuery. "
                                + "Run `bash scripts/download-embedding-model.sh` to enable, "
                                + "or set memory.embedding.enabled=false to silence this warning.",
                        modelPath);
                unavailable = true;
                return false;
            }
            long size = Files.size(modelPath);
            // external data 模式下 model.onnx 很小（~41KB），真实权重在同目录的 model.onnx_data
            Path externalData = modelPath.resolveSibling(EXTERNAL_DATA_FILE_NAME);
            boolean hasExternalData = Files.isRegularFile(externalData);
            long effectiveSize = size + (hasExternalData ? Files.size(externalData) : 0L);
            if (effectiveSize < MIN_HEALTHY_MODEL_BYTES) {
                log.warn(
                        "embedding model {} is suspiciously small ({} bytes total, expected >= {}); "
                                + "treat as unavailable. If the model uses external data, ensure {} exists.",
                        modelPath, effectiveSize, MIN_HEALTHY_MODEL_BYTES, externalData);
                unavailable = true;
                return false;
            }
            BertWordPieceTokenizer tk =
                    new BertWordPieceTokenizer(vocabPath, BertWordPieceTokenizer.DEFAULT_MAX_LENGTH, true, true);
            if (!tk.isReady()) {
                log.warn(
                        "vocab.txt not usable at {}; embedding layer disabled. "
                                + "Run `bash scripts/download-embedding-model.sh` to fetch it alongside the model.",
                        vocabPath);
                unavailable = true;
                return false;
            }
            this.env = OrtEnvironment.getEnvironment();
            try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
                opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
                this.session = env.createSession(modelPath.toString(), opts);
            }
            this.tokenizer = tk;
            if (!resolveIONames()) {
                log.warn(
                        "cannot locate expected inputs/outputs in ONNX model {}; embedding layer disabled.",
                        modelPath);
                closeSessionQuietly();
                unavailable = true;
                return false;
            }
            log.info(
                    "embedding model loaded: {} ({} bytes), vocab={} tokens, input=[{},{},{}], output={}",
                    modelPath, size, tk.vocabSize(), inputIdsName, attentionMaskName, tokenTypeName, outputName);
            unavailable = false;
            return true;
        } catch (Throwable t) {
            // 包含 UnsatisfiedLinkError（ONNX native lib 缺失）等 Error——同样降级而非崩进程
            log.warn("failed to initialize embedding model at {}: {}", modelPath, t.toString());
            closeSessionQuietly();
            unavailable = true;
            return false;
        }
    }

    /**
     * 从 session 的元数据探测输入 / 输出名（兼容不同导出脚本的命名）。
     *
     * @return {@code true} 表示必需的名字都已定位
     */
    private boolean resolveIONames() {
        try {
            var inNames = session.getInputNames();
            inputIdsName = pick(inNames, INPUT_IDS_NAMES);
            attentionMaskName = pick(inNames, ATTENTION_MASK_NAMES);
            tokenTypeName = pick(inNames, TOKEN_TYPE_NAMES);
            var outNames = session.getOutputNames();
            outputName = pick(outNames, OUTPUT_NAMES);
            // token_type_ids 是可选的（部分导出不含）；input_ids 与输出必需
            if (tokenTypeName == null) {
                tokenTypeName = pick(inNames, TOKEN_TYPE_NAMES);  // 仍为 null 时按"无该输入"处理
            }
            return inputIdsName != null && outputName != null;
        } catch (RuntimeException e) {
            log.warn("cannot inspect ONNX model IO: {}", e.toString());
            return false;
        }
    }

    /** 在候选名里挑第一个存在于集合中的；都没有则返回第一个候选（由调用方判断可用性）。 */
    private static String pick(java.util.Set<String> available, String[] candidates) {
        for (String c : candidates) {
            if (available.contains(c)) return c;
        }
        return null;
    }

    /**
     * 执行一次推理：tokenize → run → CLS 池化 → L2 归一化。
     *
     * @param text 输入文本
     * @return 512 维归一化向量
     * @throws Exception ONNX 调用失败
     */
    private float[] runInference(String text) throws Exception {
        long[] ids = tokenizer.encode(text);
        long[] mask = tokenizer.attentionMask(text);
        long[] shape = {1, ids.length};

        OnnxTensor idsTensor = null;
        OnnxTensor maskTensor = null;
        OnnxTensor typeTensor = null;
        try {
            idsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape);
            maskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(mask), shape);
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put(inputIdsName, idsTensor);
            if (attentionMaskName != null) {
                inputs.put(attentionMaskName, maskTensor);
            }
            if (tokenTypeName != null) {
                long[] types = tokenizer.tokenTypeIds(ids.length);
                typeTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(types), shape);
                inputs.put(tokenTypeName, typeTensor);
            }
            try (OrtSession.Result result = session.run(inputs)) {
                float[] vector = extractClsVector(result);
                l2Normalize(vector);
                return vector;
            }
        } finally {
            closeQuietly(idsTensor);
            closeQuietly(maskTensor);
            closeQuietly(typeTensor);
        }
    }

    /**
     * 从推理结果取 [CLS] 位置的句向量。
     *
     * <p>支持两种输出形状：{@code [1, seq, dim]}（token 级，取 [0][0]）与 {@code [1, dim]}
     *（句级，如已做过池化的导出）。
     *
     * @param result ONNX 结果
     * @return 句向量（长度应为 512）
     */
    private float[] extractClsVector(OrtSession.Result result) throws Exception {
        Object raw = result.get(outputName)
                .orElseThrow(() -> new IllegalStateException("output '" + outputName + "' missing"))
                .getValue();
        if (raw instanceof float[][][] tokenLevel) {
            if (tokenLevel.length == 0 || tokenLevel[0].length == 0) {
                throw new IllegalStateException("empty token-level output");
            }
            return tokenLevel[0][0].clone();  // CLS
        }
        if (raw instanceof float[][] sentenceLevel) {
            if (sentenceLevel.length == 0) {
                throw new IllegalStateException("empty sentence-level output");
            }
            return sentenceLevel[0].clone();
        }
        if (raw instanceof float[] flat) {
            return flat.clone();
        }
        throw new IllegalStateException("unexpected ONNX output type: " + raw.getClass());
    }

    /** 原地做 L2 归一化（零向量保持零向量，避免 NaN）。 */
    static void l2Normalize(float[] v) {
        double sum = 0d;
        for (float f : v) sum += (double) f * f;
        double norm = Math.sqrt(sum);
        if (norm == 0d) return;
        for (int i = 0; i < v.length; i++) v[i] = (float) (v[i] / norm);
    }

    private void closeSessionQuietly() {
        if (session != null) {
            try {
                session.close();
            } catch (Exception ignored) {
                // 关闭失败不影响主流程
            }
            session = null;
        }
    }

    private static void closeQuietly(OnnxTensor t) {
        if (t == null) return;
        try {
            t.close();
        } catch (Exception ignored) {
            // 同上
        }
    }

    /** 暴露给装配层的模型路径访问器。 */
    public Path modelPath() {
        return Objects.requireNonNull(modelPath);
    }

    /** 暴露给装配层的词表路径访问器。 */
    public Path vocabPath() {
        return vocabPath;
    }
}

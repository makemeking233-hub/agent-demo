package com.example.agent.memory.embedding;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.agent.config.AgentPaths;

/**
 * 本地 ONNX embedding 提供者（add-embedding-rag T2）。
 *
 * <p>封装 bge-small-zh-v1.5 模型加载与推理。懒初始化：首次 {@link #embed(String)}
 * 时加载 ONNX session；加载成功后该 session 跨调用复用（OrtSession 线程安全）。
 *
 * <p><b>降级语义</b>（与既有 "sideQuery 失败静默降级" 一致）：
 *
 * <ul>
 *   <li>模型文件不存在 → {@link #isReady()} 返回 false；{@link #embed} 返回零向量
 *   <li>加载过程抛异常（文件损坏 / ONNX native lib 缺失）→ 同上，记 WARN
 *   <li>推理抛异常 → 捕获后记 WARN 并返回零向量，下次调用时尝试重新加载
 * </ul>
 *
 * <p><b>线程安全</b>：{@link #initSession} 用 {@code synchronized} 保证 session 初始化只发生一次；
 * 推理调用走 {@code OrtSession.run()}（线程安全）。
 *
 * <p><b>T8 待办</b>：当前实现是「骨架 + 静默降级」（不引入 ONNX 依赖），T8 加
 * {@code onnxruntime} 依赖后把 {@code initSession()} 与 {@code runEmbedding()} 替换为真实调用。
 * 测试不受影响：缺失模型 / 加载失败两条路径在替换前后行为一致。
 */
public class OnnxEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(OnnxEmbeddingProvider.class);

    /** 默认 embedding 维度（bge-small-zh-v1.5 是 512）。 */
    private static final int DEFAULT_DIMENSIONS = 512;

    /** ONNX 模型在 agentHome 下的子路径（git 忽略）。 */
    private static final String MODEL_RELATIVE_PATH = "models/bge-small-zh-v1.5/model.onnx";

    private final Path modelPath;
    /** null 表示尚未尝试加载；非 null 表示已尝试过一次（成功或失败）。 */
    private volatile boolean loadAttempted = false;
    private volatile boolean loadFailed = false;

    public OnnxEmbeddingProvider() {
        this(defaultModelPath());
    }

    public OnnxEmbeddingProvider(Path modelPath) {
        this.modelPath = modelPath;
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
        // T8：替换为真实 OrtSession.run 调用。当前为骨架（loadFailed=true 时不进此分支）
        return new float[DEFAULT_DIMENSIONS];
    }

    @Override
    public int dimensions() {
        return DEFAULT_DIMENSIONS;
    }

    @Override
    public boolean isReady() {
        if (!loadAttempted) {
            ensureLoaded();
        }
        return loadAttempted && !loadFailed;
    }

    /** 轻量健康检查的文件大小阈值（字节）。ONNX 模型通常 ≥ 数十 MB，过小的文件几乎可断定为损坏或占位符。 */
    private static final long MIN_HEALTHY_MODEL_BYTES = 1024L * 1024L;

    /**
     * 尝试加载 ONNX session。仅执行一次（线程安全）。
     *
     * <p>T8 占位实现：仅做轻量健康检查（文件存在 + 体积 ≥ 1MB），不真正加载。
     * 真正的 {@code OrtSession.create()} 调用留待 T8 加 onnxruntime 依赖后补上。
     *
     * @return {@code true} 表示 session 可用（骨架阶段 = 通过轻量健康检查）
     */
    private synchronized boolean ensureLoaded() {
        if (loadAttempted) return !loadFailed;
        loadAttempted = true;
        try {
            if (!Files.exists(modelPath)) {
                log.warn(
                        "embedding model not found at {}; memory retrieval will fall back to "
                                + "literal + sideQuery only. Run `bash scripts/download-embedding-model.sh` to enable, "
                                + "or set memory.embedding.enabled=false to silence this warning.",
                        modelPath);
                loadFailed = true;
                return false;
            }
            if (!Files.isRegularFile(modelPath)) {
                log.warn("embedding model path {} is not a regular file; unavailable.", modelPath);
                loadFailed = true;
                return false;
            }
            long size;
            try {
                size = Files.size(modelPath);
            } catch (java.io.IOException ioe) {
                log.warn("cannot stat embedding model {}: {}", modelPath, ioe.toString());
                loadFailed = true;
                return false;
            }
            if (size < MIN_HEALTHY_MODEL_BYTES) {
                log.warn(
                        "embedding model {} is suspiciously small ({} bytes, expected ≥ {});"
                                + " treat as corrupted/unavailable.",
                        modelPath, size, MIN_HEALTHY_MODEL_BYTES);
                loadFailed = true;
                return false;
            }
            log.debug("embedding model path found at {} ({} bytes); T8 will wire ONNX session.",
                    modelPath, size);
            return true;
        } catch (RuntimeException e) {
            log.warn("failed to initialize embedding model at {}: {}", modelPath, e.toString());
            loadFailed = true;
            return false;
        }
    }

    /** 用于测试：重置懒加载状态（无 ONNX session 可重置，仅清 flag）。 */
    void resetForTest() {
        loadAttempted = false;
        loadFailed = false;
    }

    /** 暴露给装配层的模型路径访问器。 */
    public Path modelPath() {
        return Objects.requireNonNull(modelPath);
    }
}

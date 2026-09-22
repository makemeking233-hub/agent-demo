package com.example.agent.memory.embedding;

/**
 * Embedding 模型提供者（add-embedding-rag T2）。
 *
 * <p>抽象「把字符串转成定长 embedding 向量」这一职责；{@link OnnxEmbeddingProvider} 是当前唯一
 * 实现（本地 ONNX Runtime + bge-small-zh-v1.5）。
 *
 * <p><b>契约</b>（所有实现必须遵守）：
 *
 * <ul>
 *   <li>{@link #dimensions()} 返回模型输出维度（如 bge-small-zh 为 512）；{@code isReady()=false}
 *       时仍 SHALL 返回该默认维度，便于下游分配 buffer
 *   <li>{@link #embed(String)} 对 {@code null} / 空 / 空白字符串 SHALL 返回零向量（不抛 NPE）
 *   <li>{@link #embed(String)} 在模型不可用时 SHALL 返回零向量（不抛异常），与既有
 *       「sideQuery 失败静默降级」原则一致
 *   <li>实现必须是线程安全的（AgentLoop 每轮请求都可能并发调用）
 * </ul>
 */
public interface EmbeddingProvider {

    /**
     * 把文本转成 embedding 向量。
     *
     * @param text 输入文本（{@code null} / 空 / 空白都安全）
     * @return 长度为 {@link #dimensions()} 的浮点向量；不可用时返回零向量
     */
    float[] embed(String text);

    /**
     * embedding 维度（如 bge-small-zh-v1.5 为 512）。
     *
     * @return 维度（常量；{@code isReady()=false} 时仍 SHALL 返回设计维度）
     */
    int dimensions();

    /**
     * 模型是否就绪。
     *
     * @return {@code true} = 可正常调用 {@link #embed}；{@code false} = 模型缺失 / 加载失败 /
     *         配置关闭等，调用 {@link #embed} 将返回零向量
     */
    boolean isReady();
}

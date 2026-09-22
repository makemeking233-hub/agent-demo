package com.example.agent.memory.embedding;

import java.util.List;

/**
 * 向量索引（add-embedding-rag T3）。
 *
 * <p>抽象「按 cosine 相似度检索 top-k」这一职责；
 * {@link InMemoryVectorIndex} 是骨架实现（暴力 cosine，无持久化），
 * {@code LuceneVectorIndex} 是生产实现（T8 接入 Lucene HNSW 后生效）。
 *
 * <p><b>契约</b>：
 *
 * <ul>
 *   <li>{@link #add(String, float[])} 对同一 {@code id} 重复调用 SHALL 覆盖（不抛异常）
 *   <li>{@link #search(float[], int)} 返回按 cosine 相似度**降序**排列的 top-k；k ≤ 0 返回空列表；
 *         query 与库内向量维度不一致 SHALL 抛 {@link IllegalArgumentException}
 *   <li>{@link #size()} 返回当前库内向量数（去重后）
 *   <li>{@link #flush()} 与 {@link #close()} 是幂等的；close 后再调 add/search SHALL 抛
 *       {@link IllegalStateException}
 * </ul>
 */
public interface VectorIndex {

    /**
     * 把向量加入索引；同一 id 重复加入覆盖。
     *
     * @param id     唯一标识（通常用 memory 文件名）
     * @param vector embedding 向量（长度必须等于 {@link EmbeddingProvider#dimensions()}）
     * @throws IllegalArgumentException 若向量维度不匹配
     */
    void add(String id, float[] vector);

    /**
     * 按 cosine 相似度检索 top-k。
     *
     * @param query 查询向量
     * @param k     返回条数上限（{@code k <= 0} 返回空列表）
     * @return 按相似度降序排列的 (id, score) 列表
     * @throws IllegalArgumentException 若 query 维度与索引不一致
     */
    List<ScoredItem> search(float[] query, int k);

    /** 库内向量条目数（按 id 去重）。 */
    int size();

    /** 把内存状态刷盘（如有）。幂等。 */
    void flush();

    /** 释放资源。幂等。close 后再调 add/search 抛 IllegalStateException。 */
    void close();

    /** 检索结果（id + cosine 相似度）。cosine ∈ [-1, 1]，bge 等归一化模型通常 ∈ [0, 1]。 */
    record ScoredItem(String id, float score) {}
}

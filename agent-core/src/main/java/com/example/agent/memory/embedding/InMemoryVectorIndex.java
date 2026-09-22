package com.example.agent.memory.embedding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 纯内存向量索引（add-embedding-rag T3 骨架实现）。
 *
 * <p>用 {@link LinkedHashMap} 存 id -> vector，按 cosine 相似度暴力检索；适合 < 数千条
 * 条目的小规模场景（如个人 memory 目录）。
 *
 * <p>生产路径将由 {@code LuceneVectorIndex}（T8 接入 Lucene HNSW）取代；本类保留作为
 * fallback 与测试参考实现。
 *
 * <p><b>线程安全</b>：用 {@link ReentrantReadWriteLock}，read 多并发、write 独占；
 * 简单场景下 {@code synchronized} 也够用，但读多写少用 RWLock 更合适。
 */
public class InMemoryVectorIndex implements VectorIndex {

    private static final Logger log = LoggerFactory.getLogger(InMemoryVectorIndex.class);

    private final int dimensions;
    private final Map<String, float[]> store = new LinkedHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile boolean closed = false;

    public InMemoryVectorIndex(int dimensions) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be > 0, got " + dimensions);
        }
        this.dimensions = dimensions;
    }

    @Override
    public void add(String id, float[] vector) {
        if (closed) {
            throw new IllegalStateException("index already closed");
        }
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (vector == null || vector.length != dimensions) {
            throw new IllegalArgumentException(
                    "vector length must equal dimensions " + dimensions + ", got "
                            + (vector == null ? "null" : vector.length));
        }
        lock.writeLock().lock();
        try {
            // 防御性拷贝，避免外部修改影响存储
            float[] copy = new float[vector.length];
            System.arraycopy(vector, 0, copy, 0, vector.length);
            store.put(id, copy);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<ScoredItem> search(float[] query, int k) {
        if (closed) {
            throw new IllegalStateException("index already closed");
        }
        if (k <= 0 || store.isEmpty()) {
            return Collections.emptyList();
        }
        if (query == null || query.length != dimensions) {
            throw new IllegalArgumentException(
                    "query length must equal dimensions " + dimensions + ", got "
                            + (query == null ? "null" : query.length));
        }
        // 暴力 cosine：对每条算 cosine 相似度，取 top-k
        List<ScoredItem> all = new ArrayList<>(store.size());
        lock.readLock().lock();
        try {
            for (Map.Entry<String, float[]> e : store.entrySet()) {
                float score = cosine(query, e.getValue());
                all.add(new ScoredItem(e.getKey(), score));
            }
        } finally {
            lock.readLock().unlock();
        }
        // 降序 top-k（用 sort，O(N log N)；量更大时可换部分选择 O(N + k log k)）
        all.sort((a, b) -> Float.compare(b.score(), a.score()));
        if (all.size() > k) {
            return all.subList(0, k);
        }
        return all;
    }

    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return store.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void flush() {
        // 纯内存实现无副作用；幂等。
    }

    @Override
    public void close() {
        closed = true;
        lock.writeLock().lock();
        try {
            store.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * cosine 相似度（点积 / 模长乘积）。
     *
     * @param a 查询向量
     * @param b 库内向量
     * @return cosine ∈ [-1, 1]；任一向量为零向量时返回 0（避免 NaN）
     */
    static float cosine(float[] a, float[] b) {
        double dot = 0d, na = 0d, nb = 0d;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0d || nb == 0d) return 0f;
        return (float) (dot / (Math.sqrt(na) * Math.sqrt(nb)));
    }
}

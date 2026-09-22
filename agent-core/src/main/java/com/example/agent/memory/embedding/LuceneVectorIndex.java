package com.example.agent.memory.embedding;

import java.nio.file.Path;
import java.util.List;

/**
 * Lucene HNSW 向量索引（add-embedding-rag T3 占位 + T8 接入）。
 *
 * <p>T3 阶段：仅占位，不引入 Lucene 依赖。构造时把请求代理给 {@link InMemoryVectorIndex}，
 * 保证接口可用且测试可跑。
 *
 * <p>T8 阶段：替换实现为基于 {@code Lucene94HnswVectorFormat} 的真实 HNSW 索引，持久化到
 * {@code indexDir}，跨重启可用。
 *
 * <p>切换点（位于 {@link #add} / {@link #search} / {@link #close}）：T8 把委派逻辑改为真实
 * Lucene 调用即可，对外接口契约不变。
 */
public class LuceneVectorIndex implements VectorIndex {

    private final Path indexDir;
    private final int dimensions;
    private final InMemoryVectorIndex delegate;

    public LuceneVectorIndex(Path indexDir, int dimensions) {
        if (indexDir == null) {
            throw new IllegalArgumentException("indexDir must not be null");
        }
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be > 0, got " + dimensions);
        }
        this.indexDir = indexDir;
        this.dimensions = dimensions;
        // T3 骨架：代理给 InMemoryVectorIndex；T8 替换为 Lucene IndexWriter/IndexSearcher
        this.delegate = new InMemoryVectorIndex(dimensions);
    }

    @Override
    public void add(String id, float[] vector) {
        delegate.add(id, vector);
    }

    @Override
    public List<ScoredItem> search(float[] query, int k) {
        return delegate.search(query, k);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public void flush() {
        // T8：delegate 写入磁盘（IndexWriter.commit()）
        delegate.flush();
    }

    @Override
    public void close() {
        delegate.close();
    }

    /** 索引目录（{@code <scopeDir>/.vectors/}）。供装配层记录日志/调试。 */
    public Path indexDir() {
        return indexDir;
    }

    /** 索引维度（供调试）。 */
    public int dimensions() {
        return dimensions;
    }
}

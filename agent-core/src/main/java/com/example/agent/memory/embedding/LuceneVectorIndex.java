package com.example.agent.memory.embedding;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lucene HNSW 向量索引（add-embedding-rag T8）。
 *
 * <p>基于 Lucene 9.11 的 KNN 向量检索能力（{@link KnnFloatVectorField} + {@link KnnFloatVectorQuery}），
 * 底层使用 HNSW 图索引，提供 cosine 相似度 top-k 检索，并持久化到 {@code indexDir}。
 *
 * <p><b>持久化</b>：索引写入 {@code indexDir}（FSDirectory）。{@link #flush()} 调用
 * {@code IndexWriter.commit()}；未 commit 的数据通过 NRT reader（{@link DirectoryReader#open(IndexWriter)}）
 * 对检索可见，故 add 后无需 flush 即可搜到。
 *
 * <p><b>维度一致</b>：Lucene 要求同一字段的所有向量维度相同，构造时固定的 {@code dimensions}
 * 与该约束一致；维度不匹配的 add 会被本类提前拒绝（抛 {@link IllegalArgumentException}）。
 *
 * <p><b>索引目录不可用时的降级</b>：若 {@code indexDir} 无法创建（权限/磁盘），退回
 * {@link ByteBuffersDirectory}（纯内存），仅丧失跨重启持久化，检索能力不受影响。
 */
public class LuceneVectorIndex implements VectorIndex {

    private static final Logger log = LoggerFactory.getLogger(LuceneVectorIndex.class);

    /** 向量字段名。 */
    private static final String FIELD_VECTOR = "vector";

    /** 条目 id 字段名。 */
    private static final String FIELD_ID = "id";

    private final Path indexDir;
    private final int dimensions;
    private final Directory directory;
    private final IndexWriter writer;
    private final VectorSimilarityFunction similarity = VectorSimilarityFunction.COSINE;

    private volatile boolean closed = false;
    /** 当前可见的 reader（NRT）；add 后置空以触发重建。 */
    private volatile DirectoryReader reader;

    public LuceneVectorIndex(Path indexDir, int dimensions) {
        if (indexDir == null) {
            throw new IllegalArgumentException("indexDir must not be null");
        }
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be > 0, got " + dimensions);
        }
        this.indexDir = indexDir;
        this.dimensions = dimensions;

        Directory dir = openDirectory(indexDir);
        IndexWriter w;
        try {
            w = new IndexWriter(dir, newWriterConfig());
        } catch (IOException | RuntimeException e) {
            // 常见原因：write.lock 被同进程另一实例持有（web 端多会话并发），或目录损坏。
            // 退回内存目录：检索能力不受影响，仅丧失跨重启持久化。
            log.warn(
                    "cannot open Lucene index at {} ({}); falling back to in-memory index "
                            + "(another instance may hold the write lock)",
                    indexDir, e.toString());
            closeQuietly(dir);
            dir = new ByteBuffersDirectory();
            try {
                // 注意：不能复用同一个 IndexWriterConfig 实例（Lucene 会拒绝）
                w = new IndexWriter(dir, newWriterConfig());
            } catch (IOException e2) {
                closeQuietly(dir);
                throw new IllegalStateException("cannot open even in-memory Lucene index", e2);
            }
        }
        this.directory = dir;
        this.writer = w;
    }

    /** 每次 new IndexWriter 都需要独立的 config 实例（Lucene 禁止跨 writer 共享）。 */
    private static IndexWriterConfig newWriterConfig() {
        IndexWriterConfig cfg = new IndexWriterConfig();
        // 纯向量检索场景不需要分词器（字段均为 StringField / KnnFloatVectorField）
        cfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        return cfg;
    }

    /** 打开索引目录：优先 FSDirectory（持久化），失败则退回内存目录。 */
    private static Directory openDirectory(Path indexDir) {
        try {
            return FSDirectory.open(indexDir);
        } catch (IOException | RuntimeException e) {
            log.warn(
                    "cannot open Lucene index dir {} ({}); falling back to in-memory index "
                            + "(no cross-restart persistence)",
                    indexDir, e.toString());
            return new ByteBuffersDirectory();
        }
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
        Document doc = new Document();
        doc.add(new StringField(FIELD_ID, id, Field.Store.YES));
        doc.add(new KnnFloatVectorField(FIELD_VECTOR, vector, similarity));
        try {
            // updateDocument 按 id 去重：同一 id 重复 add 会覆盖旧文档
            writer.updateDocument(new Term(FIELD_ID, id), doc);
            invalidateReader();
        } catch (IOException e) {
            throw new IllegalStateException("cannot add vector for id=" + id, e);
        }
    }

    @Override
    public List<ScoredItem> search(float[] query, int k) {
        if (closed) {
            throw new IllegalStateException("index already closed");
        }
        if (k <= 0) {
            return Collections.emptyList();
        }
        if (query == null || query.length != dimensions) {
            throw new IllegalArgumentException(
                    "query length must equal dimensions " + dimensions + ", got "
                            + (query == null ? "null" : query.length));
        }
        try {
            DirectoryReader r = currentReader();
            if (r == null || r.numDocs() == 0) {
                return Collections.emptyList();
            }
            IndexSearcher searcher = new IndexSearcher(r);
            int limit = Math.min(k, Math.max(1, r.numDocs()));
            TopDocs topDocs = searcher.search(new KnnFloatVectorQuery(FIELD_VECTOR, query, limit), limit);
            List<ScoredItem> out = new ArrayList<>(topDocs.scoreDocs.length);
            for (ScoreDoc sd : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(sd.doc);
                String id = doc.get(FIELD_ID);
                if (id != null) {
                    out.add(new ScoredItem(id, sd.score));
                }
            }
            return out;
        } catch (IOException e) {
            log.warn("lucene knn search failed: {}", e.toString());
            return Collections.emptyList();
        }
    }

    @Override
    public int size() {
        if (closed) return 0;
        try {
            DirectoryReader r = currentReader();
            return r == null ? 0 : r.numDocs();
        } catch (IOException e) {
            log.warn("lucene numDocs failed: {}", e.toString());
            return 0;
        }
    }

    @Override
    public void flush() {
        if (closed) return;
        try {
            writer.commit();
            invalidateReader();
        } catch (IOException e) {
            log.warn("lucene commit failed: {}", e.toString());
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        closeQuietly(reader);
        reader = null;
        try {
            writer.close();
        } catch (IOException e) {
            log.warn("lucene writer close failed: {}", e.toString());
        }
        closeQuietly(directory);
    }

    /** 索引目录（供装配层日志/调试）。 */
    public Path indexDir() {
        return indexDir;
    }

    /** 索引维度（供调试）。 */
    public int dimensions() {
        return dimensions;
    }

    // ---- 内部 ----

    /** 当前 NRT reader；未初始化或已失效时重建。 */
    private DirectoryReader currentReader() throws IOException {
        DirectoryReader r = reader;
        if (r == null) {
            synchronized (this) {
                if (reader == null) {
                    reader = DirectoryReader.open(writer);
                }
                r = reader;
            }
        }
        return r;
    }

    /** 让下次检索重建 reader（add / commit 后调用）。 */
    private void invalidateReader() {
        synchronized (this) {
            closeQuietly(reader);
            reader = null;
        }
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException ignored) {
            // 关闭失败不影响主流程
        }
    }
}

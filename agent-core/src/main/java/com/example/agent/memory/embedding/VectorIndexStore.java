package com.example.agent.memory.embedding;

import com.example.agent.memory.MemoryDir;
import com.example.agent.memory.MemoryEntry;
import com.example.agent.memory.MemoryScope;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 向量索引存储（add-embedding-rag T4）。
 *
 * <p>按 scope 维护独立的 {@link VectorIndex}，并用 mtime 比对避免重复计算 embedding：
 *
 * <ol>
 *   <li>首次加载：对每个 .md 文件算 embedding 并加入索引
 *   <li>再次加载：若索引中已有该 entry 且文件 mtime 未变 → 跳过；mtime 变化 → 重算
 * </ol>
 *
 * <p><b>mtime 缓存</b>：{@code <scopeDir>/.vectors/mtime.json}（git 忽略），形如
 * {@code {"java17.md": 1758000000000}}。缓存损坏 / 缺失时记 WARN 并从空缓存开始（自愈）。
 *
 * <p><b>跨进程语义</b>：T4 阶段 {@link LuceneVectorIndex} 委派给内存索引（无持久化），故重启后
 * 索引为空、必然全量重算；mtime 缓存的价值体现在同进程内。T8 接入 Lucene HNSW 持久化后，
 * 跨进程亦可跳过未变文件。
 *
 * <p><b>线程安全</b>：索引按 scope 缓存在 {@link ConcurrentHashMap}；单个 scope 的加载走
 * {@code synchronized}（同一 scope 串行加载）。
 */
public class VectorIndexStore implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(VectorIndexStore.class);

    /** mtime 缓存文件名（位于 {@code <scopeDir>/.vectors/}）。 */
    private static final String MTIME_CACHE_FILE = "mtime.json";

    /** 索引子目录名。 */
    private static final String VECTORS_DIR = ".vectors";

    private final EmbeddingProvider provider;
    private final int dimensions;
    private final ObjectMapper json = new ObjectMapper();

    /** scope -> 索引实例。 */
    private final Map<MemoryScope, VectorIndex> indexes = new ConcurrentHashMap<>();

    /** scope -> (entry filename -> 上次索引时的 mtime 毫秒)。 */
    private final Map<MemoryScope, Map<String, Long>> indexedMtimes = new ConcurrentHashMap<>();

    /** scope -> 该 scope 索引所在的目录（flush 时定位 mtime.json）。 */
    private final Map<MemoryScope, Path> scopeDirs = new ConcurrentHashMap<>();

    private volatile boolean closed = false;

    /**
     * 构造索引存储。
     *
     * @param provider   embedding 提供者（{@link EmbeddingProvider#isReady()}=false 时索引保持为空）
     * @param dimensions 向量维度（须与 provider 一致）
     */
    public VectorIndexStore(EmbeddingProvider provider, int dimensions) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be > 0, got " + dimensions);
        }
        this.provider = provider;
        this.dimensions = dimensions;
    }

    /**
     * 加载并同步某 scope 的向量索引（mtime 比对懒加载）。
     *
     * @param scope   作用域（USER / PROJECT）
     * @param dir     该 scope 的 memory 目录
     * @param entries 该 scope 的条目（来自 MEMORY.md 索引）
     * @return 该 scope 的索引（provider 不可用时为空索引，不抛异常）
     */
    public synchronized VectorIndex indexFor(
            MemoryScope scope, MemoryDir dir, List<MemoryEntry> entries) {
        if (closed) {
            throw new IllegalStateException("store already closed");
        }
        VectorIndex idx = indexes.computeIfAbsent(scope, s -> newIndex(dir));
        if (dir == null || dir.dir() == null) {
            return idx;
        }
        scopeDirs.put(scope, dir.dir());
        if (!provider.isReady()) {
            // 降级：模型不可用 → 不 embed，索引保持为空；三层召回退化为字面 + sideQuery
            return idx;
        }
        if (entries == null || entries.isEmpty()) {
            return idx;
        }

        Map<String, Long> cached = loadMtimeCache(dir);
        Map<String, Long> known = indexedMtimes.computeIfAbsent(scope, s -> new HashMap<>());
        // 用 mtime 缓存初始化已知集合（跨进程重启时索引虽空，但缓存记录了上次状态；
        // 由于索引非持久化，仍需重算——这里以"索引实际内容"为准，即 known 集合）
        for (MemoryEntry e : entries) {
            Path file = dir.entryFile(e.filename());
            if (file == null || !Files.isRegularFile(file)) {
                continue;
            }
            long mtime = lastModified(file);
            Long prev = known.get(e.filename());
            if (prev != null && prev == mtime) {
                continue;  // 已索引且 mtime 未变 → 跳过
            }
            float[] vec = provider.embed(embeddingText(e));
            if (vec == null || vec.length != dimensions) {
                log.warn(
                        "embedding for {} returned invalid vector ({} dims, expected {}); skipping",
                        e.filename(), vec == null ? "null" : vec.length, dimensions);
                continue;
            }
            idx.add(e.filename(), vec);
            known.put(e.filename(), mtime);
        }
        // 保留缓存中尚未被本次 entries 覆盖的旧条目（文件可能临时不在索引里）
        cached.forEach(known::putIfAbsent);
        return idx;
    }

    /**
     * 把各 scope 的 mtime 缓存落盘。幂等。
     */
    @Override
    public synchronized void close() {
        if (closed) return;
        flush();
        closed = true;
    }

    /** 把各 scope 的 mtime 缓存写入 {@code <scopeDir>/.vectors/mtime.json}。幂等。 */
    public synchronized void flush() {
        if (closed && indexes.isEmpty()) return;
        for (Map.Entry<MemoryScope, Map<String, Long>> e : indexedMtimes.entrySet()) {
            Path dir = scopeDirs.get(e.getKey());
            if (dir == null) continue;
            writeMtimeCache(dir, e.getValue());
        }
        for (VectorIndex idx : indexes.values()) {
            try {
                idx.flush();
            } catch (RuntimeException ex) {
                log.warn("flush vector index failed: {}", ex.toString());
            }
        }
    }

    /** 该 scope 的索引；不存在时返回 {@code null}（未加载过）。 */
    public VectorIndex indexOf(MemoryScope scope) {
        return indexes.get(scope);
    }

    /**
     * 组装 embedding 输入文本（title + description，与 {@code MemoryRecall} 的评分输入一致）。
     */
    static String embeddingText(MemoryEntry e) {
        String title = e.title() == null ? "" : e.title();
        String desc = e.description() == null ? "" : e.description();
        return title + " " + desc;
    }

    private VectorIndex newIndex(MemoryDir dir) {
        Path vectorsDir = dir != null && dir.dir() != null ? dir.dir().resolve(VECTORS_DIR) : null;
        if (vectorsDir != null) {
            try {
                Files.createDirectories(vectorsDir);
            } catch (IOException ex) {
                log.warn("cannot create vectors dir {}: {}", vectorsDir, ex.toString());
            }
        }
        return new LuceneVectorIndex(vectorsDir, dimensions);
    }

    private static long lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException ex) {
            return 0L;
        }
    }

    private Map<String, Long> loadMtimeCache(MemoryDir dir) {
        Path cache = mtimeCachePath(dir);
        if (cache == null || !Files.isRegularFile(cache)) {
            return Map.of();
        }
        try {
            return json.readValue(cache.toFile(), new TypeReference<LinkedHashMap<String, Long>>() {});
        } catch (IOException | RuntimeException ex) {
            log.warn("mtime cache {} unreadable ({}); falling back to full recompute",
                    cache, ex.toString());
            return Map.of();
        }
    }

    private void writeMtimeCache(Path scopeDir, Map<String, Long> mtimes) {
        Path vectorsDir = scopeDir.resolve(VECTORS_DIR);
        try {
            Files.createDirectories(vectorsDir);
            json.writerWithDefaultPrettyPrinter()
                    .writeValue(vectorsDir.resolve(MTIME_CACHE_FILE).toFile(), mtimes);
        } catch (IOException | RuntimeException ex) {
            log.warn("cannot write mtime cache under {}: {}", vectorsDir, ex.toString());
        }
    }

    private static Path mtimeCachePath(MemoryDir dir) {
        return dir == null || dir.dir() == null ? null : dir.dir().resolve(VECTORS_DIR).resolve(MTIME_CACHE_FILE);
    }
}

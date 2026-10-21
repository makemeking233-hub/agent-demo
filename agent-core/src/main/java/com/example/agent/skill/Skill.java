package com.example.agent.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 一个技能（refactor-skill-index-layer T2.3 由 record 改为 final class，实现正文按需加载）。
 *
 * <p>启动期只把 frontmatter 解析成 {@link SkillMeta} 常驻索引；{@link #body()} 首次调用时才从
 * {@code SKILL.md} 读盘，并用 mtime 做缓存有效性判据——文件未变时复用缓存（允许偶发重复读盘，
 * 换来零同步开销；读盘是幂等的）。
 *
 * <p>失败语义：读盘失败返回错误说明文本，**不抛异常**（与「记忆 / 工具失败不阻断主对话」一致）。
 */
public final class Skill {
    private static final Logger log = LoggerFactory.getLogger(Skill.class);

    /** frontmatter 分隔符 */
    private static final String FRONTMATTER_DELIM = "---";

    private final SkillMeta meta;

    /** 已读正文缓存（{@code null} 表示尚未加载） */
    private volatile String cachedBody;

    /** 缓存对应的 mtime（毫秒）；{@code -1} 表示无缓存 */
    private volatile long cachedMtimeMs = -1L;

    /**
     * 构造技能。
     *
     * @param meta 技能元数据（不可空）
     */
    public Skill(SkillMeta meta) {
        if (meta == null) {
            throw new IllegalArgumentException("skill meta 不可空");
        }
        this.meta = meta;
    }

    /** @return 技能名 */
    public String name() {
        return meta.name();
    }

    /** @return 技能描述 */
    public String description() {
        return meta.description();
    }

    /** @return 技能作用域 */
    public SkillScope scope() {
        return meta.scope();
    }

    /** @return 技能所在目录 */
    public Path dir() {
        return meta.dir();
    }

    /** @return 底层元数据 */
    public SkillMeta meta() {
        return meta;
    }

    /**
     * 取技能正文（frontmatter 之后的部分），带 mtime 缓存。
     *
     * @return 正文文本；读盘失败时返回错误说明文本（不抛异常）
     */
    public String body() {
        Path file = meta.skillFilePath();
        long now = currentMtime(file);
        // 缓存有效：mtime 一致，或文件已消失但此前缓存过（此刻无法判定变更，按缓存返回）
        if (cachedBody != null && (now == cachedMtimeMs || now < 0L)) {
            return cachedBody;
        }
        String fresh = readBody(file);
        cachedBody = fresh;
        cachedMtimeMs = now;
        return fresh;
    }

    /** 读 mtime；文件不存在或读失败返回 -1。 */
    private static long currentMtime(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return -1L;
        }
    }

    /** 读正文；失败返回说明文本。 */
    private String readBody(Path file) {
        try {
            String whole = Files.readString(file);
            return extractBody(whole);
        } catch (IOException | RuntimeException e) {
            log.warn("skill 正文读取失败: {}", file, e);
            return "[skill " + name() + " 正文加载失败: " + e.getMessage() + "]";
        }
    }

    /**
     * 从整份 SKILL.md 中截出 frontmatter 之后的正文。
     *
     * <p>找不到闭合的第二个 {@code ---} 时返回全文（兜底，不抛异常）。
     */
    static String extractBody(String whole) {
        if (whole == null) return "";
        int first = whole.indexOf(FRONTMATTER_DELIM);
        if (first < 0) return whole.strip();
        int second = whole.indexOf(FRONTMATTER_DELIM, first + FRONTMATTER_DELIM.length());
        if (second < 0) return whole.strip();
        return whole.substring(second + FRONTMATTER_DELIM.length()).strip();
    }

    @Override
    public String toString() {
        return "Skill{" + name() + "}";
    }
}
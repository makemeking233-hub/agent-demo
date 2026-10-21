package com.example.agent.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 技能发现与 frontmatter 解析（refactor-skill-index-layer T3 重构：只读 frontmatter）。
 *
 * <p>重构前后行为不变：仍从两个根目录一级深度发现、仍以「后发现的根目录覆盖同名」为覆盖优先级、
 * 仍对 frontmatter 缺失 / 未闭合 / 缺 name 或 description 的技能跳过并记 WARN。
 *
 * <p>重构带来的唯一实现差异：{@link #discoverMeta} **只读到 frontmatter 结束的 {@code ---} 为止**，
 * 不把正文读进常驻内存；正文由 {@link Skill#body()} 在工具被调用时按需读盘。
 */
public final class SkillCatalog {
    private static final Logger log = LoggerFactory.getLogger(SkillCatalog.class);

    private static final String FRONTMATTER_DELIM = "---";
    private static final String SKILL_FILE = "SKILL.md";

    private SkillCatalog() {}

    /**
     * 从多个根目录发现技能（**兼容包装**：内部调 {@link #discoverMeta} 再包 {@link Skill}）。
     *
     * <p>保留此方法是为了不破坏既有调用方（{@code SkillsPlugin.tools()} 与既有测试）；它现在不再
     * 把正文常驻内存——{@link Skill} 持有的是 {@link SkillMeta}，正文在 {@code body()} 时才读。
     *
     * @param roots 技能根目录列表（后出现的根目录覆盖同名技能）
     * @return 发现的技能列表（无则空）
     */
    public static List<Skill> discover(List<Path> roots) {
        List<Skill> result = new ArrayList<>();
        for (SkillMeta meta : discoverMeta(roots)) {
            result.add(new Skill(meta));
        }
        return result;
    }

    /**
     * 从多个根目录发现技能元数据（**只读 frontmatter，不读正文**）。
     *
     * <p>覆盖优先级不变：后发现的根目录条目覆盖同名先发现的（项目级覆盖用户级）。
     *
     * @param roots 技能根目录列表
     * @return 发现的元数据列表（按根目录 + 名称顺序；无则空）
     */
    public static List<SkillMeta> discoverMeta(List<Path> roots) {
        List<SkillMeta> result = new ArrayList<>();
        if (roots == null) return result;
        for (Path root : roots) {
            if (root == null || !Files.isDirectory(root)) continue;
            for (Path skillDir : listSkillDirs(root)) {
                Path skillFile = resolveSkillFile(skillDir);
                if (skillFile == null) continue;
                SkillMeta meta = parseMeta(skillDir, skillFile);
                if (meta != null) {
                    // 同名覆盖（项目级覆盖用户级）
                    result.removeIf(m -> m.name().equals(meta.name()));
                    result.add(meta);
                }
            }
        }
        return result;
    }

    /** 列出根目录下的一级子目录名（作为 skill 目录候选）。 */
    private static List<Path> listSkillDirs(Path root) {
        List<Path> dirs = new ArrayList<>();
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(Files::isDirectory).forEach(dirs::add);
        } catch (IOException e) {
            log.warn("读取 skill 根目录失败: {}", root, e);
        }
        return dirs;
    }

    /**
     * 解析 skill 目录下的 {@code SKILL.md} 或 {@code <name>.md}。
     *
     * @return 技能文件路径；找不到时 {@code null}
     */
    private static Path resolveSkillFile(Path skillDir) {
        Path explicit = skillDir.resolve(SKILL_FILE);
        if (Files.isRegularFile(explicit)) return explicit;
        Path named = skillDir.resolve(skillDir.getFileName() + ".md");
        return Files.isRegularFile(named) ? named : null;
    }

    /**
     * 解析一个技能文件为元数据（**只读到 frontmatter 结束，不读正文**）。
     *
     * <p>用 {@link BufferedReader} 逐行读到第二个 {@code ---} 即停——10000 个 skill 的启动成本
     * 从「读全文」降到「读 frontmatter」。解析规则与重构前逐字等价。
     *
     * @param skillDir 技能目录
     * @param file     技能文件路径
     * @return 元数据；frontmatter 缺失 / 未闭合 / 缺 name 或 description 时 {@code null}（跳过）
     */
    private static SkillMeta parseMeta(Path skillDir, Path file) {
        String name = null;
        String description = null;
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String firstLine = reader.readLine();
            if (firstLine == null || !firstLine.trim().equals(FRONTMATTER_DELIM)) {
                log.warn("技能无 frontmatter，跳过: {}", file);
                return null;
            }
            boolean closed = false;
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.equals(FRONTMATTER_DELIM)) {
                    closed = true;
                    break; // ← 读到 frontmatter 结束即停，正文不读
                }
                int colon = trimmed.indexOf(':');
                if (colon > 0) {
                    String key = trimmed.substring(0, colon).trim();
                    String value = trimmed.substring(colon + 1).trim();
                    if ("name".equals(key)) name = value;
                    else if ("description".equals(key)) description = value;
                }
            }
            if (!closed) {
                log.warn("技能 frontmatter 未闭合，跳过: {}", file);
                return null;
            }
        } catch (IOException e) {
            log.warn("读取技能文件失败: {}", file, e);
            return null;
        }
        if (name == null || name.isBlank() || description == null || description.isBlank()) {
            log.warn("技能缺 name/description（frontmatter），跳过: {}", file);
            return null;
        }
        return new SkillMeta(name.trim(), description.trim(), SkillScope.USER, skillDir);
    }
}
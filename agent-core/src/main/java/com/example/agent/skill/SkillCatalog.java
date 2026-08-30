package com.example.agent.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 技能目录发现器（add-skills-system change）。
 *
 * <p>从若干根目录发现技能（一级深度）：{@code <root>/<name>/SKILL.md} 或 {@code <root>/<name>.md}。
 * 解析 {@code SKILL.md} 顶部 frontmatter（{@code ---} 包裹，{@code key: value}），读取必填的
 * name / description；缺失或格式错误时跳过该技能并记录 WARN。
 *
 * <p>嵌套子目录不识别；无目录时返回空列表（不影响主流程）。
 */
public final class SkillCatalog {
    private static final Logger log = LoggerFactory.getLogger(SkillCatalog.class);

    private static final String FRONTMATTER_DELIM = "---";
    private static final String SKILL_FILE = "SKILL.md";

    private SkillCatalog() {}

    /**
     * 从多个根目录发现技能（后发现的根目录条目覆盖同名先发现的）。
     *
     * @param roots 技能根目录列表
     * @return 发现的技能列表（按根目录 + 名称顺序；无则空）
     */
    public static List<Skill> discover(List<Path> roots) {
        List<Skill> result = new ArrayList<>();
        if (roots == null) return result;
        for (Path root : roots) {
            if (root == null || !Files.isDirectory(root)) continue;
            for (Path skillDir : listSkillDirs(root)) {
                Path skillFile = resolveSkillFile(skillDir);
                if (skillFile == null) continue;
                Skill skill = parse(skillDir, skillFile);
                if (skill != null) {
                    // 同名覆盖（项目级覆盖用户级）
                    result.removeIf(s -> s.name().equals(skill.name()));
                    result.add(skill);
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
     * 解析 skill 目录下的 SKILL.md 或 &lt;name&gt;.md。
     *
     * @param skillDir 技能目录（{@code <root>/<name>/}）
     * @return 技能文件路径；找不到时 {@code null}
     */
    private static Path resolveSkillFile(Path skillDir) {
        Path explicit = skillDir.resolve(SKILL_FILE);
        if (Files.isRegularFile(explicit)) return explicit;
        Path named = skillDir.resolve(skillDir.getFileName() + ".md");
        return Files.isRegularFile(named) ? named : null;
    }

    /**
     * 解析一个技能文件。
     *
     * @param skillDir 技能目录
     * @param file     技能文件（SKILL.md 或 &lt;name&gt;.md）
     * @return 解析出的 {@link Skill}；frontmatter 缺失/无有效 name+description 时 {@code null}（跳过）
     */
    private static Skill parse(Path skillDir, Path file) {
        String text;
        try {
            text = Files.readString(file);
        } catch (IOException e) {
            log.warn("读取技能文件失败: {}", file, e);
            return null;
        }
        if (text == null || text.isBlank()) return null;
        // 解析 frontmatter（首行与第二个 --- 之间）
        String[] lines = text.split("\n", -1);
        if (lines.length < 1 || !lines[0].trim().equals(FRONTMATTER_DELIM)) {
            log.warn("技能无 frontmatter，跳过: {}", file);
            return null;
        }
        String name = null;
        String description = null;
        int bodyStart = -1;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.equals(FRONTMATTER_DELIM)) {
                bodyStart = i + 1;
                break;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                if ("name".equals(key)) name = value;
                else if ("description".equals(key)) description = value;
            }
        }
        if (bodyStart < 0) {
            log.warn("技能 frontmatter 未闭合，跳过: {}", file);
            return null;
        }
        if (name == null || name.isBlank() || description == null || description.isBlank()) {
            log.warn("技能缺 name/description（frontmatter），跳过: {}", file);
            return null;
        }
        StringBuilder body = new StringBuilder();
        for (int i = bodyStart; i < lines.length; i++) {
            body.append(lines[i]).append("\n");
        }
        return new Skill(name.trim(), description.trim(), body.toString(), skillDir);
    }
}

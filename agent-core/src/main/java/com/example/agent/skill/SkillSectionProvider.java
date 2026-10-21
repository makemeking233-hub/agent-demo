package com.example.agent.skill;

import com.example.agent.memory.MemorySectionSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 技能候选段提供者（add-skill-demand-loading T6.5）。
 *
 * <p>把「按当轮用户提问检索技能 → 渲染成 system prompt 候选小节」封装为 {@link MemorySectionSource}
 * （签名 `String sectionFor(String query)` 与记忆段完全一致，故直接复用该抽象，无需为技能单开接口）。
 *
 * <p>渲染格式：
 *
 * <pre>
 * # Available Skills / 可能相关的技能
 *
 * - pdf-form-filler: 填充 PDF 表单域，支持 AcroForm/XFA
 * - excel-pivot: 基于数据透视表汇总 Excel
 *
 * 调用同名工具或 skill_search 获取完整指令。
 * </pre>
 *
 * <p><b>失败语义</b>：任何异常返回空串（本轮不注入），绝不阻断主对话——与记忆段一致。
 */
public class SkillSectionProvider implements MemorySectionSource {

    private static final Logger log = LoggerFactory.getLogger(SkillSectionProvider.class);

    /** 小节标题。 */
    static final String HEADER = "# Available Skills / 可能相关的技能";

    /** 尾部引导语。 */
    static final String FOOTER = "调用同名工具或 skill_search 获取完整指令。";

    private final SkillPool pool;
    private final int k;
    private final int truncateChars;

    /**
     * 构造候选段提供者。
     *
     * @param pool          技能池（{@code null} → 恒返回空串）
     * @param k             候选条数（{@code <= 0} 回退 20）
     * @param truncateChars 描述截断字符数（{@code <= 0} 回退 40）
     */
    public SkillSectionProvider(SkillPool pool, int k, int truncateChars) {
        this.pool = pool;
        this.k = k > 0 ? k : 20;
        this.truncateChars = truncateChars > 0 ? truncateChars : 40;
    }

    /**
     * 按查询产出技能候选小节。
     *
     * @param query 当轮用户提问；{@code null} / 空白返回空串（无查询则无召回）
     * @return system prompt 候选小节；无命中 / 失败返回空串
     */
    @Override
    public String sectionFor(String query) {
        if (pool == null || query == null || query.isBlank()) return "";
        try {
            List<SkillMeta> hits = pool.search(query, k);
            if (hits.isEmpty()) return "";
            StringBuilder sb = new StringBuilder(HEADER).append("\n\n");
            for (SkillMeta m : hits) {
                sb.append("- ")
                        .append(m.name())
                        .append(": ")
                        .append(truncate(m.description(), truncateChars))
                        .append("\n");
            }
            sb.append("\n").append(FOOTER).append("\n");
            return sb.toString();
        } catch (RuntimeException e) {
            log.warn("技能候选段生成失败，本轮不注入: {}", e.toString());
            return "";
        }
    }

    /**
     * 按字符数截断描述（超长补省略号）。
     *
     * @param text 原文
     * @param max  最大字符数（{@code <= 0} 返回空串）
     * @return 截断后文本
     */
    static String truncate(String text, int max) {
        if (text == null) return "";
        if (max <= 0) return "";
        String t = text.strip();
        if (t.length() <= max) return t;
        return t.substring(0, max) + "…";
    }
}
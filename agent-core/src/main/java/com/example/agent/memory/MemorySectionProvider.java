package com.example.agent.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 记忆段提供者（fix-memory-recall-wiring T3）。
 *
 * <p>把「按当前用户提问产出 system prompt 记忆段」封装成一个可注入的对象，供 {@code AgentLoop}
 * 在每轮请求组装时调用。这是修复「召回链路未接通」的关键一环：此前召回在启动期发生且 query 为空，
 * 导致 {@link MemoryRetriever} 与 {@link SideQuerySelector} 在生产路径上不可达。
 *
 * <p><b>失败语义</b>：任何异常都返回空串（本轮不注入记忆段），绝不阻断主对话流——与既有
 * 「sideQuery 失败静默降级」的设计原则一致。
 */
public class MemorySectionProvider {

    private static final Logger log = LoggerFactory.getLogger(MemorySectionProvider.class);

    /** 渲染器（基于首个目录构造；为 {@code null} 表示无可用目录，{@link #sectionFor} 恒返回空串） */
    private final MemoryPromptBuilder builder;

    /** 召回器（可空：为 {@code null} 时降级为字面召回） */
    private final MemoryRetriever retriever;

    /** 参与注入的 memory 目录（USER / PROJECT / LOCAL） */
    private final List<MemoryDir> dirs;

    /** 附加记忆指引（可空） */
    private final String extraGuidelines;

    /** 每 scope 召回条数上限 */
    private final int k;

    /**
     * 构造记忆段提供者。
     *
     * @param retriever       召回器（可空 → 纯字面召回）
     * @param dirs            参与注入的 memory 目录；为空或首元素为 {@code null} 时 {@link #sectionFor}
     *                        恒返回空串
     * @param extraGuidelines 附加记忆指引（可空）
     * @param k               每 scope 召回条数上限
     */
    public MemorySectionProvider(
            MemoryRetriever retriever, List<MemoryDir> dirs, String extraGuidelines, int k) {
        this.retriever = retriever;
        // 不用 List.copyOf：它拒绝 null 元素，而调用方可能传入含 null 的列表（此时应降级而非抛错）
        this.dirs = dirs == null ? List.of() : new ArrayList<>(dirs);
        this.extraGuidelines = extraGuidelines;
        this.k = k;
        MemoryDir first = this.dirs.isEmpty() ? null : this.dirs.get(0);
        this.builder = first == null ? null : new MemoryPromptBuilder(first);
    }

    /**
     * 按查询产出记忆段。
     *
     * @param query 当轮用户提问；{@code null} / 空白时返回空串（无查询则无召回）
     * @return system prompt 记忆段文本（含 {@code (relevant)} 小节）；失败或无可用目录时返回空串
     */
    public String sectionFor(String query) {
        if (builder == null || query == null || query.isBlank()) return "";
        try {
            return builder.build(query, dirs, retriever, extraGuidelines, k);
        } catch (Exception e) {
            log.warn("记忆段生成失败，本轮不注入记忆段: {}", e.toString());
            return "";
        }
    }
}

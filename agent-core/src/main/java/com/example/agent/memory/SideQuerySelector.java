package com.example.agent.memory;

import com.example.agent.core.Message;
import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.StreamChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SideQuery 语义召回选择器（详见 add-memory-sidequery change / memory-design.md §6.3）。
 *
 * <p>在字面 token 重叠召回命中不足时，复用当前 {@link LlmProvider} 发起一次轻量调用，从候选条目里
 * 挑选与查询最相关的 k 条（按 filename 返回）。解析失败 / 调用失败 / 超时 → 返回空列表，静默降级，
 * 绝不让 sideQuery 故障影响主流程。
 */
public class SideQuerySelector {

    /** 匹配候选 text 中出现的文件名（放宽：支持纯文本行或 JSON 列表） */
    private static final Pattern FILENAME = Pattern.compile("([A-Za-z0-9._-]+\\.md)");

    private final LlmProvider provider;
    private final String model;

    /**
     * 构造选择器。
     *
     * @param provider LLM provider（{@code null} 时 {@link #select} 恒返回空列表）
     * @param model    发起选择时使用的模型名
     */
    public SideQuerySelector(LlmProvider provider, String model) {
        this.provider = provider;
        this.model = model;
    }

    /**
     * 从候选中选出与 query 最相关的条目（按 filename）。
     *
     * @param query 查询文本
     * @param candidates 候选条目（已按范围截断）
     * @param k 最多选择条数
     * @return 选中条目的 filename 列表；失败/降级时返回空列表
     */
    public List<String> select(String query, List<MemoryEntry> candidates, int k) {
        if (provider == null || candidates == null || candidates.isEmpty()) return List.of();
        try {
            String prompt = buildPrompt(query, candidates, k);
            ChatRequest req =
                    new ChatRequest(
                            model,
                            "You select the most relevant memory entries for a query.",
                            List.of(new Message.User(prompt)),
                            List.of(),
                            0.2,
                            k * 64 + 128,
                            null);
            StringBuilder out = new StringBuilder();
            provider.streamChat(req).doOnNext(chunk -> {
                        if (chunk instanceof StreamChunk.TextDelta t) out.append(t.text());
                    })
                    .then()
                    .block();
            return parseFilenames(out.toString(), k);
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 构造选择 prompt：列出候选（每行 filename — 描述），要求模型返回最相关的 filename。
     */
    private String buildPrompt(String query, List<MemoryEntry> candidates, int k) {
        StringBuilder sb = new StringBuilder();
        sb.append("Query: ").append(query).append("\n\n");
        sb.append("Candidates:\n");
        for (int i = 0; i < candidates.size(); i++) {
            MemoryEntry e = candidates.get(i);
            sb.append(i + 1)
                    .append(". ")
                    .append(e.filename())
                    .append(" — ")
                    .append(e.title())
                    .append(": ")
                    .append(e.description())
                    .append("\n");
        }
        sb.append("\nReturn the filenames of the ").append(k).append(" most relevant, one per line.\n");
        return sb.toString();
    }

    /**
     * 从模型输出里抽取出现的 filename，去重后保留前 {@code k} 个。
     *
     * @param output 模型输出文本
     * @param k 最多保留数
     * @return filename 列表
     */
    private List<String> parseFilenames(String output, int k) {
        if (output == null || output.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        Matcher m = FILENAME.matcher(output);
        while (m.find() && result.size() < k) {
            String f = m.group(1);
            if (!result.contains(f)) result.add(f);
        }
        return result;
    }
}

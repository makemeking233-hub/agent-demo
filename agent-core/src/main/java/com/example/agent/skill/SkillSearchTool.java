package com.example.agent.skill;

import com.example.agent.permission.PermissionDecision;
import com.example.agent.tools.Tool;
import com.example.agent.tools.ToolCategory;
import com.example.agent.tools.ToolResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 技能检索工具（add-skill-demand-loading T5）。
 *
 * <p>在 {@code search} 与 {@code hybrid} 档注册（{@code all} 档全暴露，不需要检索）。
 * 模型调它按关键词找技能，得到「技能名 + 描述」列表后，用同名工具（hybrid 直露的那些）
 * 或 {@code skill_search} 反复精化来定位目标技能。
 *
 * <p><b>为什么需要这个工具</b>：候选名单（system prompt 末尾）是框架按当轮 query 给的固定 top-k，
 * 模型无法自行换关键词重搜。有了 {@code skill_search}，模型可以多次检索、逐步精化——这是
 * 「按需加载」相对「一次性给候选」的关键增量。
 *
 * <p><b>只读、免确认</b>：与 {@link SkillTool} 一致，检索不触碰文件系统副作用。
 */
public class SkillSearchTool implements Tool<String, String> {

    private static final Logger log = LoggerFactory.getLogger(SkillSearchTool.class);

    /** 检索入口（可空 = 返回空结果，不抛异常）。 */
    private final SkillPool pool;

    /** 默认返回条数上限。 */
    private final int limit;

    /**
     * 构造检索工具。
     *
     * @param pool  技能池（{@code null} 时检索恒空）
     * @param limit 返回条数上限（{@code <= 0} 回退 20）
     */
    public SkillSearchTool(SkillPool pool, int limit) {
        this.pool = pool;
        this.limit = limit > 0 ? limit : 20;
    }

    @Override
    public String name() {
        return "skill_search";
    }

    @Override
    public String description() {
        return "按关键词检索可用技能，返回匹配的技能名与描述。"
                + "找到目标后用同名工具加载完整指令；可多次调用以换关键词精化检索。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties",
                        Map.of(
                                "query",
                                Map.of(
                                        "type", "string",
                                        "description", "检索关键词，如「pdf 表单」「部署」")),
                "required", List.of("query"));
    }

    @Override
    public boolean isReadOnly(String input) {
        return true;
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.READ;
    }

    @Override
    public PermissionDecision checkPermissions(String input, Tool.ToolContext ctx) {
        return PermissionDecision.allow();
    }

    @Override
    public String renderUse(String input) {
        String q = extractQuery(input);
        return "SkillSearch(" + (q == null ? "" : q) + ")";
    }

    @Override
    public String renderResult(String output) {
        return output;
    }

    @Override
    public String parseArguments(String argumentsJson) {
        return argumentsJson;
    }

    @Override
    public Mono<ToolResult<String>> execute(String input, Tool.ToolContext ctx) {
        return Mono.fromSupplier(
                () -> {
                    String query = extractQuery(input);
                    if (query == null || query.isBlank()) {
                        return ToolResult.ok("");
                    }
                    if (pool == null) {
                        return ToolResult.ok("");
                    }
                    try {
                        List<SkillMeta> hits = pool.search(query, limit);
                        if (hits.isEmpty()) return ToolResult.ok("");
                        StringBuilder sb = new StringBuilder();
                        for (SkillMeta m : hits) {
                            sb.append(m.name()).append(": ").append(m.description()).append("\n");
                        }
                        return ToolResult.ok(sb.toString());
                    } catch (RuntimeException e) {
                        // 检索失败不阻断主对话（与「记忆召回失败静默降级」一致）
                        log.warn("skill_search 检索失败: {}", e.toString());
                        return ToolResult.ok("");
                    }
                });
    }

    /**
     * 从工具入参中提取 {@code query}。
     *
     * <p>兼容三种形态：JSON 对象 `{"query":"x"}`、纯字符串、以及带引号的裸串。
     *
     * @param input 原始入参
     * @return 提取到的 query；无法提取时返回 {@code null}
     */
    static String extractQuery(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return null;
        int idx = s.indexOf("\"query\"");
        if (idx >= 0) {
            int colon = s.indexOf(':', idx);
            if (colon > 0) {
                int start = s.indexOf('"', colon);
                if (start >= 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = start + 1; i < s.length(); i++) {
                        char c = s.charAt(i);
                        if (c == '\\' && i + 1 < s.length()) {
                            sb.append(s.charAt(++i));
                        } else if (c == '"') {
                            return sb.toString();
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
        }
        // 裸字符串形态（{"query":...} 未命中时按原样）
        return s;
    }
}
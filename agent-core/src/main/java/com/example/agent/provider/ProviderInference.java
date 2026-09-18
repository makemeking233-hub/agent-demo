package com.example.agent.provider;

import java.util.Locale;
import java.util.Map;

/**
 * 模型名 → provider 推断工具类（add-provider-catalog-abstract task 6.1）。
 *
 * <p>v0.1 简化版：按模型名前缀静态推断。后续可改为查 {@code ModelCatalog}（已知 provider/model
 * 列表）而非字符串匹配。
 *
 * <p>推断规则（按顺序匹配首个命中）：
 *
 * <ol>
 *   <li>以 {@code o1/o3/o4/gpt-} 开头 → {@code openai}
 *   <li>以 {@code claude-} 开头 → {@code anthropic}
 *   <li>以 {@code deepseek-} 开头 → {@code deepseek}
 *   <li>其他（如 {@code abab6.5s-chat} 等 MiniMax 模型名）→ 推断失败返回 {@code null}；
 *       调用方从 {@code agent.chat.default-provider} 兜底
 * </ol>
 */
public final class ProviderInference {

    /** add-provider-catalog-abstract：prefix → providerId 静态映射（首个匹配命中即返回） */
    private static final Map<String, String> PREFIX_TO_PROVIDER = Map.of(
            "o1", "openai",
            "o3", "openai",
            "o4", "openai",
            "gpt-", "openai",
            "claude-", "anthropic",
            "deepseek-", "deepseek");

    private ProviderInference() {}

    /**
     * 推断模型名对应的 provider id；推断失败返回 {@code null}。
     *
     * @param model 模型名（如 {@code deepseek-chat} / {@code gpt-4o} / {@code claude-opus-4-20250514}）
     * @return provider id；null = 无法推断，调用方应回退 {@code default-provider}
     */
    public static String inferProvider(String model) {
        if (model == null) return null;
        String m = model.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : PREFIX_TO_PROVIDER.entrySet()) {
            if (m.startsWith(e.getKey())) return e.getValue();
        }
        return null;
    }
}

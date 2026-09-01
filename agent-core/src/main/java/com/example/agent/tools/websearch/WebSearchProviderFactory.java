package com.example.agent.tools.websearch;

import com.example.agent.config.AgentConfig;
import com.example.agent.config.EnvKeys;

/**
 * 网络搜索 provider 工厂（add-web-search-tool change）。
 *
 * <p>解析顺序（D4）：
 *
 * <ol>
 *   <li>显式配置 {@code search.provider}（非空）→ 用配置的（{@code deepseek} / {@code tavily}）。
 *   <li>未配置 → 按模型推断：{@code provider.type}=="deepseek" 或模型名以 {@code deepseek} 开头 → deepseek；
 *       否则 → tavily。
 *   <li>均不可用 → 默认 tavily（无 key 时由上层 Fail-Closed）。
 * </ol>
 */
public final class WebSearchProviderFactory {
    private WebSearchProviderFactory() {}

    /**
     * 按配置创建搜索 provider。
     *
     * @param cfg 已加载配置
     * @return 对应的 {@link WebSearchProvider} 实例
     */
    public static WebSearchProvider create(AgentConfig cfg) {
        String explicit = cfg.search() != null ? cfg.search().provider() : null;
        String provider;
        if (explicit != null && !explicit.isBlank()) {
            provider = explicit.toLowerCase();
        } else {
            provider = infer(cfg);
        }
        return switch (provider) {
            case "deepseek" -> new DeepSeekWebSearchProvider(
                    cfg.provider().apiKey(), System.getenv(EnvKeys.DEEPSEEK_SEARCH_BASE_URL));
            default -> new TavilyWebSearchProvider(System.getenv(EnvKeys.TAVILY_API_KEY));
        };
    }

    /** 未显式配置时按模型推断 provider 名。 */
    private static String infer(AgentConfig cfg) {
        String type =
                cfg.provider() != null && cfg.provider().type() != null
                        ? cfg.provider().type().toLowerCase()
                        : "";
        String model =
                cfg.provider() != null && cfg.provider().model() != null
                        ? cfg.provider().model().toLowerCase()
                        : "";
        if ("deepseek".equals(type) || model.startsWith("deepseek")) {
            return "deepseek";
        }
        return "tavily";
    }
}

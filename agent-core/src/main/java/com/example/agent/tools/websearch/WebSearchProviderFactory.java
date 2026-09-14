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
 *
 * <p><strong>fix-websearch-key-priority</strong>：新增 {@link #create(AgentConfig, String, String, String)}
 * 重载接受显式 keys；{@code null} 表示沿用 cfg + 系统环境变量。Web 场景下用 env-merged keys
 * 传入，让 web_search 与主对话（{@code ChatController.send}）共享同一 key 优先级：
 * {@code DEEPSEEK_API_KEY} env > {@code agent.provider.api-key} yaml > {@code cfg.provider().apiKey()}。
 */
public final class WebSearchProviderFactory {
    private WebSearchProviderFactory() {}

    /**
     * 按配置创建搜索 provider（CLI 默认路径，向后兼容）。
     *
     * @param cfg 已加载配置
     * @return 对应的 {@link WebSearchProvider} 实例
     */
    public static WebSearchProvider create(AgentConfig cfg) {
        return create(cfg, null, null, null);
    }

    /**
     * 按配置 + 显式 keys 创建搜索 provider（fix-websearch-key-priority T1）。
     *
     * <p>任何 {@code keys} 参数为 {@code null} 时：
     * <ul>
     *   <li>{@code deepseekApiKey=null} → 用 {@code cfg.provider().apiKey()}（ConfigLoader.applyEnv 已 env-merged）
     *   <li>{@code tavilyApiKey=null} → 用 {@code System.getenv(TAVILY_API_KEY)}
     *   <li>{@code deepseekBaseUrl=null} → 用 {@code System.getenv(DEEPSEEK_SEARCH_BASE_URL)}
     * </ul>
     *
     * @param cfg 已加载配置
     * @param deepseekApiKey DeepSeek API key（null 走 cfg）
     * @param tavilyApiKey Tavily API key（null 走 env）
     * @param deepseekBaseUrl DeepSeek Anthropic-compatible base URL（null 走 env）
     * @return 对应的 {@link WebSearchProvider} 实例
     */
    public static WebSearchProvider create(
            AgentConfig cfg,
            String deepseekApiKey,
            String tavilyApiKey,
            String deepseekBaseUrl) {
        String explicit = cfg.search() != null ? cfg.search().provider() : null;
        String provider;
        if (explicit != null && !explicit.isBlank()) {
            provider = explicit.toLowerCase();
        } else {
            provider = infer(cfg);
        }
        String resolvedDeepseekKey = pickFirstNonBlank(deepseekApiKey, cfg.provider().apiKey());
        String resolvedBaseUrl = pickFirstNonBlank(
                deepseekBaseUrl, System.getenv(EnvKeys.DEEPSEEK_SEARCH_BASE_URL));
        String resolvedTavilyKey = pickFirstNonBlank(tavilyApiKey, System.getenv(EnvKeys.TAVILY_API_KEY));
        return switch (provider) {
            case "deepseek" -> new DeepSeekWebSearchProvider(
                    resolvedDeepseekKey, resolvedBaseUrl);
            default -> new TavilyWebSearchProvider(resolvedTavilyKey);
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

    private static String pickFirstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return (b != null && !b.isBlank()) ? b : null;
    }
}
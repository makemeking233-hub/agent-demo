package com.example.agent.tools.websearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.example.agent.config.AgentConfig;

import org.junit.jupiter.api.Test;

class WebSearchProviderFactoryTest {

    @Test
    void deepseekModelChoosesDeepSeekProvider() {
        AgentConfig cfg = AgentConfig.defaults(); // type=deepseek, model=deepseek-chat
        assertInstanceOf(DeepSeekWebSearchProvider.class, WebSearchProviderFactory.create(cfg));
    }

    @Test
    void nonDeepseekModelFallsBackToTavily() {
        AgentConfig cfg = withProvider("minimax", "minimax-text");
        assertInstanceOf(TavilyWebSearchProvider.class, WebSearchProviderFactory.create(cfg));
    }

    @Test
    void deepseekModelNameWithOtherTypeChoosesDeepSeek() {
        AgentConfig cfg = withProvider("openai", "deepseek-v3");
        assertInstanceOf(DeepSeekWebSearchProvider.class, WebSearchProviderFactory.create(cfg));
    }

    @Test
    void explicitProviderWinsOverInference() {
        AgentConfig cfg = withSearch(withProvider("deepseek", "deepseek-chat"), "tavily");
        assertInstanceOf(TavilyWebSearchProvider.class, WebSearchProviderFactory.create(cfg));
    }

    @Test
    void explicitDeepSeekWithNonDeepseekModel() {
        AgentConfig cfg = withSearch(withProvider("minimax", "minimax-text"), "deepseek");
        assertInstanceOf(DeepSeekWebSearchProvider.class, WebSearchProviderFactory.create(cfg));
    }

    // ===== fix-websearch-key-priority T4.1 =====

    /** 显式 deepseekKey 覆盖 cfg.provider().apiKey() — Web 场景用 env-merged key 调 factory */
    @Test
    void explicitDeepseekKeyWinsOverCfg() {
        AgentConfig cfg = withProvider("deepseek", "deepseek-chat");
        WebSearchProvider p = WebSearchProviderFactory.create(cfg, "sk-explicit", null, null);
        assertInstanceOf(DeepSeekWebSearchProvider.class, p);
        assertThat(p).extracting("apiKey").isEqualTo("sk-explicit");
    }

    /** 显式 deepseekKey=null 走 cfg.provider().apiKey() — CLI 场景向后兼容 */
    @Test
    void nullDeepseekKeyFallsBackToCfg() {
        AgentConfig cfg = withProviderAndKey("deepseek", "deepseek-chat", "sk-cfg");
        WebSearchProvider p = WebSearchProviderFactory.create(cfg, null, null, null);
        assertInstanceOf(DeepSeekWebSearchProvider.class, p);
        assertThat(p).extracting("apiKey").isEqualTo("sk-cfg");
    }

    /** 显式 tavilyKey=null + 显式 search.provider="tavily" → 走 Tavily 路径，tavily key 由 provider 默认从 env 读 */
    @Test
    void nullTavilyKeyPassesToTavilyProvider() {
        AgentConfig cfg = withSearch(withProvider("minimax", "minimax-text"), "tavily");
        WebSearchProvider p = WebSearchProviderFactory.create(cfg, null, "tv-explicit", null);
        assertInstanceOf(TavilyWebSearchProvider.class, p);
        assertThat(p).extracting("apiKey").isEqualTo("tv-explicit");
    }

    /** 显式 search.provider="deepseek" + tavilyKey 非空 → 仍走 deepseek 路径，tavily key 被忽略 */
    @Test
    void deepseekPathIgnoresTavilyKey() {
        AgentConfig cfg = withSearch(withProvider("deepseek", "deepseek-chat"), "deepseek");
        WebSearchProvider p = WebSearchProviderFactory.create(cfg, "sk-ds", "tv-ignored", null);
        assertInstanceOf(DeepSeekWebSearchProvider.class, p);
        assertThat(p).extracting("apiKey").isEqualTo("sk-ds");
    }

    /** 显式 deepseekBaseUrl 传入 DeepSeekWebSearchProvider */
    @Test
    void deepseekBaseUrlOverridesDefault() {
        AgentConfig cfg = withProvider("deepseek", "deepseek-chat");
        WebSearchProvider p = WebSearchProviderFactory.create(cfg, "sk", null, "https://custom.example.com/anthropic/v1");
        assertInstanceOf(DeepSeekWebSearchProvider.class, p);
        assertThat(p).extracting("endpoint").asString()
                .startsWith("https://custom.example.com/anthropic/v1/messages");
    }

    // ===== fix-jacoco-rule：补 pickFirstNonBlank / infer 的容错分支 =====

    /** search.provider 为空白 → 不视为「显式配置」，回落到模型推断 */
    @Test
    void blankExplicitProviderFallsBackToInference() {
        AgentConfig cfg = withSearch(withProvider("deepseek", "deepseek-chat"), "   ");
        // 空白 explicit 被忽略 → infer 出 deepseek
        assertInstanceOf(DeepSeekWebSearchProvider.class, WebSearchProviderFactory.create(cfg));
    }

    /** 显式 key 为空白 → 视同未提供，回落 cfg.provider().apiKey()（pickFirstNonBlank 的 a-blank 分支） */
    @Test
    void blankDeepseekKeyFallsBackToCfgKey() {
        AgentConfig cfg = withProviderAndKey("deepseek", "deepseek-chat", "sk-cfg");
        WebSearchProvider p = WebSearchProviderFactory.create(cfg, "   ", null, null);
        assertInstanceOf(DeepSeekWebSearchProvider.class, p);
        assertThat(p).extracting("apiKey").isEqualTo("sk-cfg");
    }

    /**
     * 显式 tavilyKey 为空白 → 回落到 {@code System.getenv(TAVILY_API_KEY)}。
     *
     * <p>只断言「仍构造成 Tavily provider」：env 里是否有 TAVILY_API_KEY 取决于运行机器，
     * 断言具体 key 值会变成环境相关的脆弱测试。本用例的价值是跑过 pickFirstNonBlank 的
     * a-blank 与 b 分支。
     */
    @Test
    void blankTavilyKeyFallsBackToEnv() {
        AgentConfig cfg = withSearch(withProvider("minimax", "minimax-text"), "tavily");
        WebSearchProvider p = WebSearchProviderFactory.create(cfg, null, "  ", null);
        assertInstanceOf(TavilyWebSearchProvider.class, p);
    }

    /** provider.type 与 model 均为 null → infer 走「两者都空」分支，回落 tavily */
    @Test
    void nullTypeAndModelInferTavily() {
        AgentConfig cfg = withProvider(null, null);
        assertInstanceOf(TavilyWebSearchProvider.class, WebSearchProviderFactory.create(cfg));
    }

    private static AgentConfig withProvider(String type, String model) {
        AgentConfig d = AgentConfig.defaults();
        return new AgentConfig(
                new AgentConfig.Provider(
                        type,
                        d.provider().apiKey(),
                        d.provider().baseUrl(),
                        model,
                        d.provider().maxOutputTokens()),
                d.permission(),
                d.cost(),
                d.context(),
                d.shell(),
                d.memoryInject(),
                d.logging(),
                d.memory(),
                d.mcp(),
                d.worktree(),
                d.plugins(),
                d.search(),
                d.voice());
    }

    /** 显式 apiKey 的 withProvider（fix-websearch-key-priority T4.1） */
    private static AgentConfig withProviderAndKey(String type, String model, String apiKey) {
        AgentConfig d = AgentConfig.defaults();
        return new AgentConfig(
                new AgentConfig.Provider(
                        type,
                        apiKey,
                        d.provider().baseUrl(),
                        model,
                        d.provider().maxOutputTokens()),
                d.permission(),
                d.cost(),
                d.context(),
                d.shell(),
                d.memoryInject(),
                d.logging(),
                d.memory(),
                d.mcp(),
                d.worktree(),
                d.plugins(),
                d.search(),
                d.voice());
    }

    private static AgentConfig withSearch(AgentConfig base, String provider) {
        return new AgentConfig(
                base.provider(),
                base.permission(),
                base.cost(),
                base.context(),
                base.shell(),
                base.memoryInject(),
                base.logging(),
                base.memory(),
                base.mcp(),
                base.worktree(),
                base.plugins(),
                new AgentConfig.Search(
                        provider, base.search().maxResults(), base.search().timeoutMs()),
                base.voice());
    }
}


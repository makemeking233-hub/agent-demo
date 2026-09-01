package com.example.agent.tools.websearch;

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
                d.search());
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
                        provider, base.search().maxResults(), base.search().timeoutMs()));
    }
}

package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agent.web.api.catalog.ModelCatalog;
import com.example.agent.web.api.catalog.ProviderCatalogProperties;
import com.example.agent.web.api.dto.ModelEntry;
import com.example.agent.web.api.dto.SendRequest;
import com.example.agent.web.stream.ChatStreamService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;

/**
 * ChatController provider 推断 + default-provider 兜底（add-provider-catalog-abstract task 6.4）。
 *
 * <p>不启动 Spring 上下文：直接 new {@link ChatController}（4 参：streams / env / catalog /
 * catalogProps —— merge main 后 `fix-stale-model-fallback` 新增了后两个依赖），
 * mock {@link ChatStreamService} / {@link Environment} / {@link ModelCatalog} /
 * {@link ProviderCatalogProperties}，捕获 {@code streams.create(...)} 的第一个参数（providerId）。
 *
 * <p>与 main 的 `fix-stale-model-fallback` 对齐：非法 model 会被 `resolveModel` 判为 null →
 * 控制器直接返 400 `invalid_model`，**不再静默回退**（本文件对应断言 `unknownModelIsRejectedWith400`）。
 * provider 推断只对「合法 model」生效；default-provider 兜底用 mock catalog 构造
 * 「在目录内但前缀不可识别」的 model 来覆盖。
 */
class ChatControllerProviderInferenceTest {

    private ChatStreamService streams;
    private Environment env;
    private ModelCatalog catalog;
    private ProviderCatalogProperties catalogProps;
    private ChatController controller;
    private final AtomicReference<String> capturedProviderId = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        streams = mock(ChatStreamService.class);
        env = mock(Environment.class);
        catalog = mock(ModelCatalog.class);
        catalogProps = mock(ProviderCatalogProperties.class);

        when(env.getProperty("DEEPSEEK_API_KEY")).thenReturn("sk-test-fake");
        when(catalogProps.defaultProvider()).thenReturn("deepseek");
        when(catalogProps.defaultModel()).thenReturn("deepseek-chat");

        // create(...) 6 参：捕获第 1 个参数 providerId；返回一个最小 ActiveStream
        when(streams.create(any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    capturedProviderId.set(inv.getArgument(0));
                    return stubActiveStream(inv.getArgument(1), inv.getArgument(2));
                });
        when(streams.workspaceExists(any())).thenReturn(true);
        when(streams.start(any(), any())).thenReturn(true);

        controller = new ChatController(streams, env, catalog, catalogProps);
    }

    /** 让 catalog 认下这些 model id（resolveModel 以 catalog 为唯一真源）。 */
    private void acceptModels(String... ids) {
        for (String id : ids) {
            when(catalog.modelById(id)).thenReturn(Optional.of(new ModelEntry(id, id, false, List.of())));
        }
    }

    private static ChatStreamService.ActiveStream stubActiveStream(String sessionId, String model) {
        return new ChatStreamService.ActiveStream(
                "stream-1", sessionId, model, System.currentTimeMillis(),
                Sinks.many().replay().all(), null, null,
                new java.util.concurrent.atomic.AtomicBoolean(false), null);
    }

    private ResponseEntity<?> send(String model) {
        SendRequest req = new SendRequest("hi", "sess-1", "read_only", null, model, null);
        return (ResponseEntity<?>) controller.send(req).block();
    }

    private String providerIdFor(String model) {
        ResponseEntity<?> resp = send(model);
        assertThat(resp).isNotNull();
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        return capturedProviderId.get();
    }

    @Test
    void infersDeepSeekFromDeepSeekModel() {
        acceptModels("deepseek-chat");
        assertThat(providerIdFor("deepseek-chat")).isEqualTo("deepseek");
    }

    @Test
    void infersDeepSeekFromReasonerModel() {
        acceptModels("deepseek-reasoner");
        assertThat(providerIdFor("deepseek-reasoner")).isEqualTo("deepseek");
    }

    @Test
    void infersOpenAiFromGptModel() {
        acceptModels("gpt-4o");
        assertThat(providerIdFor("gpt-4o")).isEqualTo("openai");
    }

    @Test
    void infersAnthropicFromClaudeModel() {
        acceptModels("claude-opus-4-20250514");
        assertThat(providerIdFor("claude-opus-4-20250514")).isEqualTo("anthropic");
    }

    @Test
    void fallsBackToConfiguredDefaultProviderWhenPrefixUnrecognized() {
        // abab6.5s-chat 在目录里（合法），但前缀无法推断 → 走 catalogProps.defaultProvider()
        acceptModels("abab6.5s-chat");
        when(catalogProps.defaultProvider()).thenReturn("minimax");
        assertThat(providerIdFor("abab6.5s-chat")).isEqualTo("minimax");
    }

    @Test
    void fallsBackToHardcodedDeepSeekWhenConfiguredProviderMissing() {
        acceptModels("abab6.5s-chat");
        when(catalogProps.defaultProvider()).thenReturn(null);
        assertThat(providerIdFor("abab6.5s-chat")).isEqualTo("deepseek");
    }

    @Test
    void fallsBackToHardcodedDeepSeekWhenConfiguredProviderBlank() {
        acceptModels("abab6.5s-chat");
        when(catalogProps.defaultProvider()).thenReturn("   ");
        assertThat(providerIdFor("abab6.5s-chat")).isEqualTo("deepseek");
    }

    @Test
    void nullModelUsesCatalogDefaultModel() {
        // 未指定 model → 走 catalogProps.defaultModel()，再推断 provider
        acceptModels("deepseek-chat");
        assertThat(providerIdFor(null)).isEqualTo("deepseek");
    }
}

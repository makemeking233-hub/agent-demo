package com.example.agent.web.wecom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agent.core.AgentLoop;
import com.example.agent.core.Message;
import com.example.agent.web.api.catalog.ProviderCatalogProperties;
import com.example.agent.web.api.dto.ModelEntry;
import com.example.agent.web.api.dto.ProviderGroup;
import com.example.agent.web.stream.ChatStreamService;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * 微信通道模型来源（fix-stale-model-fallback T5）。
 *
 * <p>此前 {@code WecomMessageDispatcher} 用类内常量 {@code DEFAULT_MODEL = "deepseek-chat"} ——
 * 该 id 已被上游停用且不在 {@code agent.chat.providers} 目录中，等于微信通道**每一轮都在请求
 * 一个已下线的模型**。本测试锁死：模型必须等于配置的 {@code agent.chat.default-model}，
 * 且不得等于任何硬编码 id。
 */
class WecomMessageDispatcherModelTest {

    private static final String CONFIGURED_DEFAULT = "deepseek-v4-pro";

    @Test
    void dispatchPassesConfiguredDefaultModelNotHardcodedId() {
        AtomicReference<String> capturedModel = new AtomicReference<>();
        ChatStreamService streams = mock(ChatStreamService.class);
        when(streams.create(any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    capturedModel.set(inv.getArgument(1));
                    return activeStream(inv.getArgument(0), inv.getArgument(1));
                });
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatStreamService> streamsProvider = mock(ObjectProvider.class);
        when(streamsProvider.getObject()).thenReturn(streams);

        WecomSessionMapper sessionMapper = mock(WecomSessionMapper.class);
        when(sessionMapper.getOrCreate("user-1")).thenReturn("sess-wecom");

        WecomMessageDispatcher dispatcher = new WecomMessageDispatcher(
                mock(WecomCrypto.class),
                wecomProps(),
                sessionMapper,
                mock(WecomReplyPusher.class),
                streamsProvider,
                catalogProps());

        boolean dispatched = dispatcher.dispatch(new WecomEvent("user-1", "hi", "text", 0L, null));

        assertThat(dispatched).isTrue();
        assertThat(capturedModel.get()).isEqualTo(CONFIGURED_DEFAULT);
        // 反向断言：绝不能回到那个已被上游停用的硬编码 id
        assertThat(capturedModel.get()).isNotEqualTo("deepseek-chat");
    }

    @Test
    void configuredDefaultIsALegalCatalogId() {
        // 兜底值必须在目录内，否则微信通道会再次把非法 id 发给上游
        assertThat(catalog().modelById(CONFIGURED_DEFAULT)).isPresent();
        assertThat(catalog().modelById("deepseek-chat")).isEmpty();
    }

    private static ProviderCatalogProperties catalogProps() {
        return new ProviderCatalogProperties(catalog().providers(), "deepseek", CONFIGURED_DEFAULT);
    }

    private static com.example.agent.web.api.catalog.ModelCatalog catalog() {
        return new com.example.agent.web.api.catalog.ModelCatalog(List.of(new ProviderGroup(
                "deepseek",
                "DeepSeek",
                List.of(
                        new ModelEntry("deepseek-v4-flash", "DeepSeek-V4-Flash", false, List.of()),
                        new ModelEntry(CONFIGURED_DEFAULT, "DeepSeek-V4-Pro", true, List.of())))));
    }

    /** 真实 record 而非 mock：字段在 dispatch 中并未被读取，构造仅为满足依赖。 */
    private static WecomConfigProperties wecomProps() {
        return new WecomConfigProperties(
                true, "corp", "agent", "secret", "token", "aes-key", "https://example.invalid", null);
    }

    private static ChatStreamService.ActiveStream activeStream(String sessionId, String model) {
        AgentLoop loop = mock(AgentLoop.class);
        when(loop.processTurn(any(Message.User.class))).thenReturn(Mono.empty());
        return new ChatStreamService.ActiveStream(
                "stream-wecom",
                sessionId,
                model,
                System.currentTimeMillis(),
                Sinks.many().replay().all(),
                loop,
                null,
                new AtomicBoolean(false),
                null);
    }
}

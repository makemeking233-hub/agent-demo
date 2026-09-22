package com.example.agent.provider.minimax;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.time.Duration;

/**
 * {@link MiniMaxProvider} 构造器与协议元数据覆盖（fix-jacoco-rule）。
 *
 * <p>该包此前 LINE 0.529：{@code validateProviderHook} 已由
 * {@link MiniMaxProviderValidateProviderTest} 覆盖，但三个构造器与
 * {@code baseUrl}/{@code chatEndpoint}/{@code contextWindow}/{@code maxOutputTokens}
 * 从未被跑到。本类与 {@link MiniMaxProvider} 同包，可直接访问 {@code protected} 方法。
 */
class MiniMaxProviderTest {

    @Test
    void singleArgConstructorUsesChinaEndpointBaseUrl() {
        MiniMaxProvider p = new MiniMaxProvider("test-key");

        assertThat(p.baseUrl()).isEqualTo("https://api.minimaxi.com");
        assertThat(p.name()).isEqualTo("minimax");
    }

    @Test
    void twoArgConstructorHonoursCustomBaseUrl() {
        String custom = "http://localhost:18999";
        MiniMaxProvider p = new MiniMaxProvider("test-key", custom);

        assertThat(p.baseUrl()).isEqualTo(custom);
    }

    @Test
    void fourArgConstructorAcceptsExplicitTimeouts() {
        MiniMaxProvider p =
                new MiniMaxProvider(
                        "test-key",
                        "http://localhost:18999",
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(2));

        assertThat(p.baseUrl()).isEqualTo("http://localhost:18999");
        assertThat(p.name()).isEqualTo("minimax");
    }

    @Test
    void chatEndpointDiffersFromStandardOpenAiPath() {
        MiniMaxProvider p = new MiniMaxProvider("test-key");

        // MiniMax 中国版用 /v1/text/chatcompletion_v2，不是 OpenAI 标准的 /v1/chat/completions
        assertThat(p.chatEndpoint()).isEqualTo("/v1/text/chatcompletion_v2");
    }

    @Test
    void contextWindowAndMaxOutputFollowOfficialDocs() {
        MiniMaxProvider p = new MiniMaxProvider("test-key");

        assertThat(p.contextWindow()).isEqualTo(128_000);
        assertThat(p.maxOutputTokens()).isEqualTo(8_192);
    }
}

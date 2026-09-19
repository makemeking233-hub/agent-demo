package com.example.agent.provider.deepseek;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link DeepSeekProvider#baseUrl()} 应回传构造器传入的 baseUrl（fix-provider-baseurl）。
 *
 * <p>此前该方法固定返回 {@code BASE_URL} 常量，忽略构造器参数。本测试与
 * {@link DeepSeekProvider} 同包（{@code com.example.agent.provider.deepseek}），
 * 因此可直接访问 {@code protected} 方法，无需反射或子类包装。
 */
class DeepSeekProviderBaseUrlTest {

    @Test
    void baseUrlReturnsValuePassedToConstructor() {
        String customUrl = "http://localhost:18999";
        DeepSeekProvider provider = new DeepSeekProvider("test-key", customUrl);
        // 同包可直接调 protected baseUrl()
        assertThat(provider.baseUrl()).isEqualTo(customUrl);
    }

    @Test
    void baseUrlDefaultsToApiRootWhenSingleArgConstructorUsed() {
        DeepSeekProvider provider = new DeepSeekProvider("test-key");
        assertThat(provider.baseUrl()).isEqualTo("https://api.deepseek.com");
    }
}

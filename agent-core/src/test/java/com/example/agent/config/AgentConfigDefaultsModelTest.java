package com.example.agent.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * fix-cli-residue：CLI 路径不再以已停用的 {@code deepseek-chat} 作为默认模型。
 *
 * <p>{@code AgentConfig.defaults().provider().model()} 此前固定返回 {@code "deepseek-chat"}，
 * 与 web profile `application-web.yml` 中的 `deepseek-v4-flash` 不一致——CLI 启动后
 * 缺省会发一个上游已停用的 model id。
 */
class AgentConfigDefaultsModelTest {

    @Test
    void defaultsModelIsDeepseekV4Flash() {
        AgentConfig cfg = AgentConfig.defaults();
        // 与 web profile application-web.yml 的 agent.chat.default-model 对齐
        assertThat(cfg.provider().model()).isEqualTo("deepseek-v4-flash");
        assertThat(cfg.provider().type()).isEqualTo("deepseek");
        // baseUrl 不动
        assertThat(cfg.provider().baseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(cfg.provider().apiKey()).isEmpty();
        assertThat(cfg.provider().maxOutputTokens()).isEqualTo(8192);
    }
}

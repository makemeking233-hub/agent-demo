package com.example.agent.web.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.agent.config.AgentConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** T2.1: WebConfig 启动时校验 host != 0.0.0.0 (spec §Trusted Host Auth / Scenario: Bind to 0.0.0.0 rejected). */
class WebConfigTest {

    @Test
    void rejectsBindingTo0000() {
        var env = new MockEnvironment().withProperty("DEEPSEEK_API_KEY", "sk-test");
        var cfg = new WebConfig(new WebProperties("0.0.0.0", 8080, List.of("192.168.1.0/24"), null),
                env, AgentConfig.defaults());
        assertThatThrownBy(cfg::validateAll)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("0.0.0.0")
                .hasMessageContaining("not supported");
    }

    @Test
    void acceptsLoopbackHost() {
        // 配 api key 让 voice 门禁通过；只测 host 校验
        var env = new MockEnvironment().withProperty("DEEPSEEK_API_KEY", "sk-test");
        var cfg = new WebConfig(new WebProperties("127.0.0.1", 8080, List.of(), null),
                env, AgentConfig.defaults());
        assertThatCode(cfg::validateAll).doesNotThrowAnyException();
        assertThat(cfg.props().host()).isEqualTo("127.0.0.1");
    }

    @Test
    void acceptsLanHost() {
        var env = new MockEnvironment().withProperty("DEEPSEEK_API_KEY", "sk-test");
        var cfg = new WebConfig(new WebProperties("192.168.1.42", 8080, List.of("192.168.1.0/24"), null),
                env, AgentConfig.defaults());
        assertThatCode(cfg::validateAll).doesNotThrowAnyException();
        assertThat(cfg.props().host()).isEqualTo("192.168.1.42");
    }

    /** improve-voice-accuracy T8.4: postProcess.enabled=true 但 DeepSeek key 缺失 → 启动失败. */
    @Test
    void rejectsStartupWhenVoiceEnabledButApiKeyMissing() {
        var cfg = new WebConfig(new WebProperties("127.0.0.1", 8080, List.of(), null),
                new MockEnvironment(), AgentConfig.defaults());
        assertThatThrownBy(cfg::validateVoiceConfig)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DeepSeek API key");
    }

    /** improve-voice-accuracy T8.4: postProcess.enabled=true 且 DEEPSEEK_API_KEY 配置 → 通过. */
    @Test
    void acceptsStartupWhenVoiceEnabledAndEnvKeyPresent() {
        var env = new MockEnvironment().withProperty("DEEPSEEK_API_KEY", "sk-test");
        var cfg = new WebConfig(new WebProperties("127.0.0.1", 8080, List.of(), null),
                env, AgentConfig.defaults());
        assertThatCode(cfg::validateVoiceConfig).doesNotThrowAnyException();
    }

    /** improve-voice-accuracy T8.4: postProcess.enabled=true 且 application-local.yml agent.provider.api-key 配置 → 通过. */
    @Test
    void acceptsStartupWhenVoiceEnabledAndSpringKeyPresent() {
        var env = new MockEnvironment().withProperty("agent.provider.api-key", "sk-yaml");
        var cfg = new WebConfig(new WebProperties("127.0.0.1", 8080, List.of(), null),
                env, AgentConfig.defaults());
        assertThatCode(cfg::validateVoiceConfig).doesNotThrowAnyException();
    }

    /** improve-voice-accuracy T8.4: postProcess.enabled=false 时跳过 key 校验. */
    @Test
    void skipsKeyCheckWhenVoiceDisabled() {
        AgentConfig noVoice = withVoiceDisabled(AgentConfig.defaults());
        var cfg = new WebConfig(new WebProperties("127.0.0.1", 8080, List.of(), null),
                new MockEnvironment(), noVoice);
        assertThatCode(cfg::validateVoiceConfig).doesNotThrowAnyException();
    }

    /** improve-voice-accuracy T8.4: 空白环境变量视为未配置. */
    @Test
    void rejectsBlankEnvApiKey() {
        var env = new MockEnvironment().withProperty("DEEPSEEK_API_KEY", "   ");
        var cfg = new WebConfig(new WebProperties("127.0.0.1", 8080, List.of(), null),
                env, AgentConfig.defaults());
        assertThatThrownBy(cfg::validateVoiceConfig)
                .isInstanceOf(IllegalStateException.class);
    }

    private static AgentConfig withVoiceDisabled(AgentConfig base) {
        return new AgentConfig(
                base.provider(), base.permission(), base.cost(), base.context(), base.shell(),
                base.memoryInject(), base.logging(), base.memory(), base.mcp(), base.worktree(),
                base.plugins(), base.search(),
                new AgentConfig.Voice(new AgentConfig.PostProcess(false)));
    }
}
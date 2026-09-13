package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agent.web.api.dto.ModelsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * ModelsController（add-models-dropdown-v0）：返回每个 model 的 {@code reasoningEfforts} 数组。
 */
class ModelsControllerTest {

    @Test
    void listsDeepseekChatWithEmptyReasoningEfforts() {
        MockEnvironment env = new MockEnvironment();
        ModelsController c = new ModelsController(env);
        ModelsResponse resp = c.list().block();
        assertThat(resp).isNotNull();
        // 默认 supported-models = [deepseek-chat, deepseek-reasoner]
        var chat = resp.models().stream().filter(m -> "deepseek-chat".equals(m.id())).findFirst().orElseThrow();
        assertThat(chat.supportsReasoning()).isFalse();
        assertThat(chat.reasoningEfforts()).isEmpty();
    }

    @Test
    void listsDeepseekReasonerWithThreeEffortLevels() {
        MockEnvironment env = new MockEnvironment();
        ModelsController c = new ModelsController(env);
        ModelsResponse resp = c.list().block();
        var reasoner = resp.models().stream().filter(m -> "deepseek-reasoner".equals(m.id())).findFirst().orElseThrow();
        assertThat(reasoner.supportsReasoning()).isTrue();
        assertThat(reasoner.reasoningEfforts()).containsExactly("low", "medium", "high");
    }

    @Test
    void customSupportedModelsConfigReturned() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("agent.chat.supported-models", "o1,o1-mini");
        ModelsController c = new ModelsController(env);
        ModelsResponse resp = c.list().block();
        assertThat(resp.models()).hasSize(2);
        // o1 / o1-mini 触发 supportsReasoning=true（包含 "o1"）
        resp.models().forEach(m -> {
            assertThat(m.supportsReasoning()).isTrue();
            assertThat(m.reasoningEfforts()).containsExactly("low", "medium", "high");
        });
    }
}
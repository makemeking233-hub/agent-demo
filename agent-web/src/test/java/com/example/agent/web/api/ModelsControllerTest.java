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
    void listsDeepseekV4FlashWithEmptyReasoningEfforts() {
        MockEnvironment env = new MockEnvironment();
        ModelsController c = new ModelsController(env);
        ModelsResponse resp = c.list().block();
        assertThat(resp).isNotNull();
        // 默认 supported-models = [deepseek-v4-flash, deepseek-reasoner, deepseek-v4-pro]
        // 注意：v0.1 移除 deepseek-v4-flash-vision-exp（OpenAiCompatibleMapper 不支持多模态 content，
        // 调用会 PrematureCloseException；v0.2+ 加 image_url 支持后再启用）。
        assertThat(resp.models()).hasSize(3);
        assertThat(resp.models()).extracting("id")
                .doesNotContain("deepseek-v4-flash-vision-exp");
        var v4Flash = resp.models().stream().filter(m -> "deepseek-v4-flash".equals(m.id())).findFirst().orElseThrow();
        assertThat(v4Flash.supportsReasoning()).isFalse();
        assertThat(v4Flash.reasoningEfforts()).isEmpty();
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

    @Test
    void deepseekV4ProSupportsReasoning() {
        // add-deepseek-v4-models: deepseek-v4-pro 触发 supportsReasoning=true
        MockEnvironment env = new MockEnvironment();
        env.setProperty("agent.chat.supported-models", "deepseek-v4-flash,deepseek-v4-pro");
        ModelsController c = new ModelsController(env);
        ModelsResponse resp = c.list().block();
        assertThat(resp.models()).hasSize(2);
        var v4Flash = resp.models().stream().filter(m -> m.id().equals("deepseek-v4-flash")).findFirst().orElseThrow();
        assertThat(v4Flash.supportsReasoning()).isFalse();
        assertThat(v4Flash.reasoningEfforts()).isEmpty();
        var v4Pro = resp.models().stream().filter(m -> m.id().equals("deepseek-v4-pro")).findFirst().orElseThrow();
        assertThat(v4Pro.supportsReasoning()).isTrue();
        assertThat(v4Pro.reasoningEfforts()).containsExactly("low", "medium", "high");
    }
}
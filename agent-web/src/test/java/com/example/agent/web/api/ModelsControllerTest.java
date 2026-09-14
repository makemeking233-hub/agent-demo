package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agent.web.api.catalog.ModelCatalog;
import com.example.agent.web.api.catalog.ProviderCatalogProperties;
import com.example.agent.web.api.catalog.ProviderCatalogService;
import com.example.agent.web.api.dto.ModelsResponse;
import com.example.agent.web.api.dto.ProviderGroup;
import com.example.agent.web.api.dto.ReasoningEffort;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ModelsController（add-provider-catalog-abstract）：返回 {@code providers[]} 嵌套结构。
 *
 * <p>直接构造 {@link ModelCatalog} + {@link ModelsController}（不走 Spring 上下文）。
 */
class ModelsControllerTest {

    private static ModelCatalog catalogFrom(ProviderGroup... groups) {
        return new ModelCatalog(List.of(groups));
    }

    private static ProviderGroup deepseekProvider() {
        return new ProviderGroup(
                "deepseek",
                "DeepSeek",
                List.of(
                        new com.example.agent.web.api.dto.ModelEntry(
                                "deepseek-v4-flash", "DeepSeek-V4-Flash", false, List.of()),
                        new com.example.agent.web.api.dto.ModelEntry(
                                "deepseek-reasoner",
                                "DeepSeek Reasoner",
                                true,
                                List.of(
                                        new ReasoningEffort("low", "Low", null),
                                        new ReasoningEffort("medium", "Medium", null),
                                        new ReasoningEffort("high", "High", null)))));
    }

    @Test
    void listsNestedProvidersWithDeepseekV4Flash() {
        ModelCatalog catalog = catalogFrom(deepseekProvider());
        ModelsController c = new ModelsController(catalog);
        ModelsResponse resp = c.list().block();
        assertThat(resp).isNotNull();
        assertThat(resp.providers()).hasSize(1);
        var deepseek = resp.providers().get(0);
        assertThat(deepseek.id()).isEqualTo("deepseek");
        assertThat(deepseek.name()).isEqualTo("DeepSeek");
        assertThat(deepseek.models()).hasSize(2);
        var v4Flash = deepseek.models().stream()
                .filter(m -> "deepseek-v4-flash".equals(m.id()))
                .findFirst()
                .orElseThrow();
        assertThat(v4Flash.supportsReasoning()).isFalse();
        assertThat(v4Flash.reasoningEfforts()).isEmpty();
    }

    @Test
    void reasonerExposesThreeEffortLevels() {
        ModelCatalog catalog = catalogFrom(deepseekProvider());
        ModelsController c = new ModelsController(catalog);
        ModelsResponse resp = c.list().block();
        var reasoner = resp.providers().get(0).models().stream()
                .filter(m -> "deepseek-reasoner".equals(m.id()))
                .findFirst()
                .orElseThrow();
        assertThat(reasoner.supportsReasoning()).isTrue();
        assertThat(reasoner.reasoningEfforts()).hasSize(3);
        assertThat(reasoner.reasoningEfforts()).extracting("id")
                .containsExactly("low", "medium", "high");
    }

    @Test
    void emptyCatalogReturnsEmptyProviders() {
        ModelCatalog catalog = new ModelCatalog(List.of());
        ModelsController c = new ModelsController(catalog);
        ModelsResponse resp = c.list().block();
        assertThat(resp.providers()).isEmpty();
    }

    @Test
    void flatModelsFieldMirrorsProvidersForBackwardCompat() {
        // add-provider-catalog-abstract 过渡期:前端 chat.ts 还在读 models[],
        // 后端同时输出 providers + flat models,前端不报错
        ModelCatalog catalog = catalogFrom(deepseekProvider());
        ModelsController c = new ModelsController(catalog);
        ModelsResponse resp = c.list().block();
        assertThat(resp.providers()).hasSize(1);
        assertThat(resp.models()).hasSize(2);
        assertThat(resp.models()).extracting("id")
                .containsExactlyInAnyOrder("deepseek-v4-flash", "deepseek-reasoner");
        // flat 的 reasoningEfforts 是 String 列表,id 一致
        var flatReasoner = resp.models().stream()
                .filter(m -> "deepseek-reasoner".equals(m.id())).findFirst().orElseThrow();
        assertThat(flatReasoner.reasoningEfforts()).containsExactly("low", "medium", "high");
    }

    @Test
    void multipleProvidersOrderedByYml() {
        ProviderGroup openai = new ProviderGroup(
                "openai",
                "OpenAI",
                List.of(new com.example.agent.web.api.dto.ModelEntry(
                        "o1", "o1", true,
                        List.of(new ReasoningEffort("low", "Low", null)))));
        ModelCatalog catalog = catalogFrom(deepseekProvider(), openai);
        ModelsController c = new ModelsController(catalog);
        ModelsResponse resp = c.list().block();
        assertThat(resp.providers()).extracting("id").containsExactly("deepseek", "openai");
    }

    // ----- ProviderCatalogService 启动校验 -----

    @Test
    void startupValidationRejectsSupportsReasoningFalseWithNonEmptyEfforts() {
        // add-models-dropdown-v0 spec: supportsReasoning=false 必须 reasoningEfforts=[]
        ProviderGroup bad = new ProviderGroup(
                "bad",
                "Bad",
                List.of(new com.example.agent.web.api.dto.ModelEntry(
                        "bad-model", "Bad", false,
                        List.of(new ReasoningEffort("low", "Low", null)))));
        ProviderCatalogProperties props = new ProviderCatalogProperties(
                List.of(bad), "bad", "bad-model");
        ProviderCatalogService svc = new ProviderCatalogService(props);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                svc::init,
                "supportsReasoning=false 但 reasoningEfforts 非空 → 启动必须失败");
    }

    @Test
    void startupValidationRejectsEffortWithBlankId() {
        ProviderGroup bad = new ProviderGroup(
                "bad",
                "Bad",
                List.of(new com.example.agent.web.api.dto.ModelEntry(
                        "bad-model",
                        "Bad",
                        true,
                        List.of(new ReasoningEffort("", "Low", null)))));
        ProviderCatalogProperties props = new ProviderCatalogProperties(
                List.of(bad), "bad", "bad-model");
        ProviderCatalogService svc = new ProviderCatalogService(props);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, svc::init);
    }
}
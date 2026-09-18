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

    /**
     * 单元测试不跑 {@link ProviderCatalogService#init()}，故 props 的默认值仅供 {@code list()} 回填，
     * 无需与传入的 catalog 严格一致；默认值合法性由
     * {@code startupValidationRejectsDefault*} 三个用例单独覆盖。
     */
    private static ProviderCatalogProperties defaultProps() {
        return new ProviderCatalogProperties(List.of(), "deepseek", "deepseek-v4-flash");
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
        ModelsController c = new ModelsController(catalog, defaultProps());
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
        ModelsController c = new ModelsController(catalog, defaultProps());
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
        ModelsController c = new ModelsController(catalog, defaultProps());
        ModelsResponse resp = c.list().block();
        assertThat(resp.providers()).isEmpty();
    }

    @Test
    void flatModelsFieldMirrorsProvidersForBackwardCompat() {
        // add-provider-catalog-abstract 过渡期:前端 chat.ts 还在读 models[],
        // 后端同时输出 providers + flat models,前端不报错
        ModelCatalog catalog = catalogFrom(deepseekProvider());
        ModelsController c = new ModelsController(catalog, defaultProps());
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
        ModelsController c = new ModelsController(catalog, defaultProps());
        ModelsResponse resp = c.list().block();
        assertThat(resp.providers()).extracting("id").containsExactly("deepseek", "openai");
    }

    // ----- 默认 provider/model 随响应下发（fix-stale-model-fallback T2） -----

    @Test
    void responseCarriesConfiguredDefaultsForFrontendFallback() {
        // 前端兜底值必须来自服务端配置，而不是硬编码模型 id
        ProviderCatalogProperties props = new ProviderCatalogProperties(
                List.of(deepseekProvider()), "deepseek", "deepseek-v4-flash");
        ModelsController c = new ModelsController(catalogFrom(deepseekProvider()), props);
        ModelsResponse resp = c.list().block();
        assertThat(resp).isNotNull();
        assertThat(resp.defaultProvider()).isEqualTo("deepseek");
        assertThat(resp.defaultModel()).isEqualTo("deepseek-v4-flash");
        // 默认值必须真的在目录里（否则前端会拿到一个发不出去的 id）
        assertThat(resp.models()).extracting("id").contains(resp.defaultModel());
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

    // ----- 默认 provider/model 必须存在于目录（fix-stale-model-fallback） -----

    @Test
    void startupValidationRejectsDefaultModelOutsideCatalog() {
        // default-model 本身非法 → 未指定 model 的请求会把非法 id 透传给上游，必须启动即失败
        ProviderCatalogProperties props = new ProviderCatalogProperties(
                List.of(deepseekProvider()), "deepseek", "deepseek-chat");
        ProviderCatalogService svc = new ProviderCatalogService(props);
        IllegalStateException ex = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                svc::init,
                "default-model 不在目录中 → 启动必须失败");
        assertThat(ex.getMessage()).contains("deepseek-chat").contains("default-model");
    }

    @Test
    void startupValidationRejectsDefaultProviderOutsideCatalog() {
        ProviderCatalogProperties props = new ProviderCatalogProperties(
                List.of(deepseekProvider()), "minimax", "deepseek-v4-flash");
        ProviderCatalogService svc = new ProviderCatalogService(props);
        IllegalStateException ex = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                svc::init,
                "default-provider 不在目录中 → 启动必须失败");
        assertThat(ex.getMessage()).contains("minimax").contains("default-provider");
    }

    @Test
    void startupValidationRejectsDefaultModelBelongingToAnotherProvider() {
        // default-model 存在但属于别的 provider → 默认选择这一对无法解析，同样要拒
        ProviderGroup openai = new ProviderGroup(
                "openai",
                "OpenAI",
                List.of(new com.example.agent.web.api.dto.ModelEntry(
                        "o1", "o1", true,
                        List.of(new ReasoningEffort("low", "Low", null)))));
        ProviderCatalogProperties props = new ProviderCatalogProperties(
                List.of(deepseekProvider(), openai), "deepseek", "o1");
        ProviderCatalogService svc = new ProviderCatalogService(props);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, svc::init);
    }

    @Test
    void startupValidationAcceptsDefaultModelInsideCatalog() {
        ProviderCatalogProperties props = new ProviderCatalogProperties(
                List.of(deepseekProvider()), "deepseek", "deepseek-v4-flash");
        ProviderCatalogService svc = new ProviderCatalogService(props);
        svc.init();
        assertThat(svc.defaultModel()).isEqualTo("deepseek-v4-flash");
        assertThat(svc.defaultProvider()).isEqualTo("deepseek");
    }
}
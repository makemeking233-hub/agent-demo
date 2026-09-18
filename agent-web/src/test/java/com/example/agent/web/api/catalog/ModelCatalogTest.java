package com.example.agent.web.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agent.web.api.dto.ModelEntry;
import com.example.agent.web.api.dto.ProviderGroup;
import com.example.agent.web.api.dto.ReasoningEffort;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ModelCatalog 按 model id 查找（fix-stale-model-fallback T1.1）。
 *
 * <p>前端只传 `model` 不传 `provider`，所以服务端的合法性校验必须能跨 provider 按 id 查。
 * 本测试覆盖命中 / 未命中 / null 入参 / 跨 provider 命中 / 顺序 / 空目录 / models 为 null。
 */
class ModelCatalogTest {

    private static ModelEntry entry(String id, boolean supportsReasoning) {
        return new ModelEntry(
                id,
                id,
                supportsReasoning,
                supportsReasoning ? List.of(new ReasoningEffort("low", "Low", null)) : List.of());
    }

    private static ModelCatalog twoProviderCatalog() {
        return new ModelCatalog(List.of(
                new ProviderGroup(
                        "deepseek",
                        "DeepSeek",
                        List.of(entry("deepseek-v4-flash", false), entry("deepseek-reasoner", true))),
                new ProviderGroup("openai", "OpenAI", List.of(entry("o1", true)))));
    }

    @Test
    void findsModelInFirstProvider() {
        assertThat(twoProviderCatalog().modelById("deepseek-v4-flash"))
                .isPresent()
                .get()
                .extracting(ModelEntry::id)
                .isEqualTo("deepseek-v4-flash");
    }

    @Test
    void findsModelInLaterProvider() {
        assertThat(twoProviderCatalog().modelById("o1")).isPresent();
    }

    @Test
    void returnsEmptyForStaleIdOutsideCatalog() {
        // deepseek-chat 已被上游停用且不在目录中，必须查不到
        assertThat(twoProviderCatalog().modelById("deepseek-chat")).isEmpty();
    }

    @Test
    void returnsEmptyForNullId() {
        assertThat(twoProviderCatalog().modelById(null)).isEmpty();
    }

    @Test
    void returnsEmptyForEmptyCatalog() {
        assertThat(new ModelCatalog(List.of()).modelById("deepseek-v4-flash")).isEmpty();
    }

    @Test
    void returnsEmptyWhenProviderModelsIsNull() {
        // yaml 里 provider 缺 models 字段时不应 NPE（ModelsResponse 已有同样的兜底）
        ModelCatalog catalog = new ModelCatalog(List.of(new ProviderGroup("broken", "Broken", null)));
        assertThat(catalog.modelById("anything")).isEmpty();
    }

    @Test
    void modelIdsFollowsProviderThenModelOrder() {
        assertThat(twoProviderCatalog().modelIds())
                .containsExactly("deepseek-v4-flash", "deepseek-reasoner", "o1");
    }

    @Test
    void modelIdsIsEmptyForEmptyCatalog() {
        assertThat(new ModelCatalog(List.of()).modelIds()).isEmpty();
    }

    @Test
    void modelIdsSkipsProviderWithNullModels() {
        ModelCatalog catalog = new ModelCatalog(List.of(
                new ProviderGroup("broken", "Broken", null),
                new ProviderGroup("deepseek", "DeepSeek", List.of(entry("deepseek-v4-flash", false)))));
        assertThat(catalog.modelIds()).containsExactly("deepseek-v4-flash");
    }
}

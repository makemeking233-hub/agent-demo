package com.example.agent.web.api.catalog;

import com.example.agent.web.api.dto.ModelEntry;
import com.example.agent.web.api.dto.ProviderGroup;
import com.example.agent.web.api.dto.ReasoningEffort;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 不可变 provider/model 目录（add-provider-catalog-abstract）。
 *
 * <p>由 {@link ProviderCatalogService} 在 Spring 启动时从
 * {@link ProviderCatalogProperties} 构造,作为单例 bean 暴露给 {@link ModelsController}
 * 与 {@link com.example.agent.web.api.ChatController}。
 *
 * <p>对外提供四个查询方法,不允许运行时写入 — 整个 catalog 在应用生命周期内不变,
 * 修改需重启进程。
 */
public final class ModelCatalog {
    private final List<ProviderGroup> providers;

    public ModelCatalog(List<ProviderGroup> providers) {
        this.providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
    }

    /** 全部 provider 列表(嵌套结构顶层) */
    public List<ProviderGroup> providers() {
        return providers;
    }

    /** 按 provider id 查找单个 provider */
    public Optional<ProviderGroup> provider(String id) {
        if (id == null) return Optional.empty();
        return providers.stream().filter(p -> id.equals(p.id())).findFirst();
    }

    /** 按 {@code providerId} + {@code modelId} 查找单个 model */
    public Optional<ModelEntry> model(String providerId, String modelId) {
        if (providerId == null || modelId == null) return Optional.empty();
        return provider(providerId).flatMap(p -> p.models().stream()
                .filter(m -> modelId.equals(m.id())).findFirst());
    }

    /** 查询某 provider 下某 model 支持的 effort 列表(空列表表示不支持 reasoning) */
    public List<ReasoningEffort> supportedEfforts(String providerId, String modelId) {
        return model(providerId, modelId)
                .map(ModelEntry::reasoningEfforts)
                .orElse(List.of());
    }
}
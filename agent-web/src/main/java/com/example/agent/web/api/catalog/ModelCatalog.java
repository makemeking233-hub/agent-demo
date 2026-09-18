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
 * <p>对外提供查询方法,不允许运行时写入 — 整个 catalog 在应用生命周期内不变,
 * 修改需重启进程。
 *
 * <p>本类是「哪些模型合法」的**唯一真源**（fix-stale-model-fallback）：服务端校验、
 * 默认值、列表端点都从这里读，不再有第二个来源。
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

    /**
     * 按 model id **跨 provider** 查找单个 model（fix-stale-model-fallback）。
     *
     * <p>{@code POST /api/chat/send} 只带 {@code model} 不带 {@code provider}，服务端无从先定位
     * provider，所以合法性校验必须能跨 provider 按 id 查。provider 的 {@code models} 为
     * {@code null} 时跳过（YAML 里漏写 models 字段不应导致 NPE）。
     *
     * @param modelId 待查的 model id；{@code null} 返回 {@link Optional#empty()}
     * @return 命中的 model 条目，未命中返回 {@link Optional#empty()}
     */
    public Optional<ModelEntry> modelById(String modelId) {
        if (modelId == null) return Optional.empty();
        return providers.stream()
                .filter(p -> p.models() != null)
                .flatMap(p -> p.models().stream())
                .filter(m -> modelId.equals(m.id()))
                .findFirst();
    }

    /**
     * 全部 model id（fix-stale-model-fallback），按 YAML 中 provider 顺序、provider 内 model 顺序展开。
     *
     * <p>用于非法模型的 400 响应体（告知调用方合法取值）与启动日志。
     *
     * @return 不可变 id 列表；空目录返回空列表
     */
    public List<String> modelIds() {
        return providers.stream()
                .filter(p -> p.models() != null)
                .flatMap(p -> p.models().stream())
                .map(ModelEntry::id)
                .toList();
    }
}
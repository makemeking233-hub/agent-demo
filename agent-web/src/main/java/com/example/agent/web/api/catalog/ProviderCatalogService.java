package com.example.agent.web.api.catalog;

import com.example.agent.web.api.dto.ModelEntry;
import com.example.agent.web.api.dto.ProviderGroup;
import com.example.agent.web.api.dto.ReasoningEffort;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provider/model 目录启动加载服务（add-provider-catalog-abstract）。
 *
 * <p>Spring 启动时从 {@link ProviderCatalogProperties} 构造不可变 {@link ModelCatalog}
 * 单例 bean。启动校验:
 *
 * <ul>
 *   <li>{@code supportsReasoning=false} 必须 {@code reasoningEfforts=[]}
 *   <li>{@code defaultProvider} 与 {@code defaultModel} 必须能匹配到 {@code ModelCatalog}
 * </ul>
 *
 * <p>校验失败抛 {@link IllegalStateException},Spring 启动失败(fail-fast)。
 */
@Configuration
@EnableConfigurationProperties(ProviderCatalogProperties.class)
public class ProviderCatalogService {
    private static final Logger log = LoggerFactory.getLogger(ProviderCatalogService.class);

    private final ProviderCatalogProperties props;
    private ModelCatalog catalog;

    public ProviderCatalogService(ProviderCatalogProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        List<ProviderGroup> providers = props.providers() != null ? props.providers() : List.of();
        validate(providers, props.defaultProvider(), props.defaultModel());
        this.catalog = new ModelCatalog(providers);
        log.info("ModelCatalog loaded: {} provider(s), {} model(s), default {}/{}",
                providers.size(), catalog.modelIds().size(), props.defaultProvider(), props.defaultModel());
    }

    private static void validate(
            List<ProviderGroup> providers, String defaultProvider, String defaultModel) {
        for (ProviderGroup p : providers) {
            if (p.models() == null) continue;
            for (ModelEntry m : p.models()) {
                List<ReasoningEffort> efforts =
                        m.reasoningEfforts() != null ? m.reasoningEfforts() : List.of();
                if (!m.supportsReasoning() && !efforts.isEmpty()) {
                    throw new IllegalStateException(String.format(
                            "provider[%s].models[%s].supports-reasoning=false 但 reasoning-efforts 非空(%s)",
                            p.id(), m.id(), efforts));
                }
                for (ReasoningEffort e : efforts) {
                    if (e.id() == null || e.id().isBlank()) {
                        throw new IllegalStateException(String.format(
                                "provider[%s].models[%s] 含 id 为空的 effort", p.id(), m.id()));
                    }
                }
            }
        }
        validateDefaults(providers, defaultProvider, defaultModel);
    }

    /**
     * 配置的 {@code default-provider} / {@code default-model} 必须存在于目录中（fix-stale-model-fallback）。
     *
     * <p>类注释早已声明这条约束，但此前代码从未检查。若默认 model 本身不在目录中，
     * {@code ChatController} 的「未指定 model」兜底路径就会把一个**非法 id 透传给上游**——
     * 正是本次修复的缺陷形态，只是把位置从 Controller 搬到了配置。故在启动期 fail-fast。
     *
     * @param providers       目录中的 provider 列表
     * @param defaultProvider 配置的默认 provider id
     * @param defaultModel    配置的默认 model id（必须属于 {@code defaultProvider}）
     * @throws IllegalStateException 任一项匹配不到
     */
    private static void validateDefaults(
            List<ProviderGroup> providers, String defaultProvider, String defaultModel) {
        ProviderGroup provider = null;
        for (ProviderGroup p : providers) {
            if (Objects.equals(p.id(), defaultProvider)) {
                provider = p;
                break;
            }
        }
        if (provider == null) {
            throw new IllegalStateException(String.format(
                    "agent.chat.default-provider=%s 不在 agent.chat.providers 中（现有 provider: %s）",
                    defaultProvider, providers.stream().map(ProviderGroup::id).toList()));
        }
        List<ModelEntry> models = provider.models() != null ? provider.models() : List.of();
        for (ModelEntry m : models) {
            if (Objects.equals(m.id(), defaultModel)) return;
        }
        throw new IllegalStateException(String.format(
                "agent.chat.default-model=%s 不在 provider[%s] 的 models 中（该 provider 现有 model: %s）",
                defaultModel, defaultProvider, models.stream().map(ModelEntry::id).toList()));
    }

    @Bean
    public ModelCatalog modelCatalog() {
        return catalog;
    }

    /** default provider/model（前端未选时兜底） */
    public String defaultProvider() {
        return props.defaultProvider();
    }

    public String defaultModel() {
        return props.defaultModel();
    }
}
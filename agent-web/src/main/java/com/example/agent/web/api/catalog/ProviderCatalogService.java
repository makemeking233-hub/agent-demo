package com.example.agent.web.api.catalog;

import com.example.agent.web.api.dto.ModelEntry;
import com.example.agent.web.api.dto.ProviderGroup;
import com.example.agent.web.api.dto.ReasoningEffort;
import jakarta.annotation.PostConstruct;
import java.util.List;
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
        validate(providers);
        this.catalog = new ModelCatalog(providers);
        log.info("ModelCatalog loaded: {} provider(s), default {}/{}",
                providers.size(), props.defaultProvider(), props.defaultModel());
    }

    private static void validate(List<ProviderGroup> providers) {
        for (ProviderGroup p : providers) {
            for (ModelEntry m : p.models()) {
                if (!m.supportsReasoning() && m.reasoningEfforts() != null
                        && !m.reasoningEfforts().isEmpty()) {
                    throw new IllegalStateException(String.format(
                            "provider[%s].models[%s].supports-reasoning=false 但 reasoning-efforts 非空(%s)",
                            p.id(), m.id(), m.reasoningEfforts()));
                }
                for (ReasoningEffort e : m.reasoningEfforts()) {
                    if (e.id() == null || e.id().isBlank()) {
                        throw new IllegalStateException(String.format(
                                "provider[%s].models[%s] 含 id 为空的 effort", p.id(), m.id()));
                    }
                }
            }
        }
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
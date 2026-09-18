package com.example.agent.web.api.catalog;

import com.example.agent.web.api.dto.ProviderGroup;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Provider/model 目录 yaml 配置（add-provider-catalog-abstract）。
 *
 * <p>从 {@code agent.chat} 配置块绑定。yaml 结构示例见
 * {@code docs/provider-catalog.md}(task 13.1 交付)。
 *
 * @param providers        嵌套 provider 列表
 * @param defaultProvider  前端未选时 fallback 用的 provider id（如 "deepseek"）
 * @param defaultModel     前端未选时 fallback 用的 model id（如 "deepseek-v4-flash"）；
 *                         必须属于 {@code defaultProvider}，由 {@link ProviderCatalogService}
 *                         启动校验（fix-stale-model-fallback）。示例原先写作 "deepseek-chat"，
 *                         那是个已被上游停用、也不在目录中的 id，照抄会复现同类缺陷。
 */
@ConfigurationProperties(prefix = "agent.chat")
public record ProviderCatalogProperties(
        List<ProviderGroup> providers,
        String defaultProvider,
        String defaultModel) {
}
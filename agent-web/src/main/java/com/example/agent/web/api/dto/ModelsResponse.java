package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code GET /api/chat/models} 响应（add-provider-catalog-abstract + hotfix 兼容期）。
 *
 * <p>v0.1 (add-models-dropdown-v0) 是平铺 {@code models[]};v0.2 (本 change) 升级为嵌套
 * {@code providers[]},对齐 dsh web {@code ModelProviderGroup[]} 形态。
 *
 * <p>本响应**同时输出两套字段**(过渡期兼容 add-models-dropdown-v0 阶段的前端 chat.ts
 * 还在读 {@code models[]}):
 *
 * <ul>
 *   <li>{@code providers}: 新嵌套结构,add-provider-catalog-abstract task 9 前端两层菜单消费</li>
 *   <li>{@code models}: 平铺结构(从 providers 扁平化),前端 chat.ts 当前仍读此字段</li>
 * </ul>
 *
 * <p>task 8 (前端 chat.ts 类型升级 + ModelSelect 两层菜单) 完成后会移除 {@code models}
 * 平铺字段,正式 BREAKING。当前为过渡态。
 *
 * <p>fix-stale-model-fallback 增 {@code defaultProvider} / {@code defaultModel}:前端据此兜底，
 * 不必再硬编码任何模型 id（此前硬编码 {@code deepseek-chat} 已是被上游停用的 id）。
 *
 * @param providers       嵌套 provider 目录（真源：{@code agent.chat.providers}）
 * @param defaultProvider 配置的默认 provider id（{@code agent.chat.default-provider}）
 * @param defaultModel    配置的默认 model id（{@code agent.chat.default-model}，必属于 defaultProvider）
 */
public record ModelsResponse(
        @JsonProperty("providers") List<ProviderGroup> providers,
        @JsonProperty("defaultProvider") String defaultProvider,
        @JsonProperty("defaultModel") String defaultModel) {

    /** 平铺 {@code models} 字段(过渡期兼容)。{@code @JsonInclude.ALWAYS} 强制输出,即便 providers 非空。 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonProperty("models")
    public List<LegacyModel> models() {
        List<LegacyModel> flat = new ArrayList<>();
        if (providers != null) {
            for (ProviderGroup p : providers) {
                if (p.models() == null) continue;
                for (ModelEntry m : p.models()) {
                    flat.add(new LegacyModel(m.id(), m.id(), m.supportsReasoning(),
                            m.reasoningEfforts().stream().map(ReasoningEffort::id).toList()));
                }
            }
        }
        return flat;
    }

    /**
     * 平铺模型条目(add-models-dropdown-v0 形态,过渡期用)。
     * 字段顺序与原 ModelsResponse.Model 保持一致(id / name / supportsReasoning / reasoningEfforts)。
     */
    public record LegacyModel(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("supportsReasoning") boolean supportsReasoning,
            @JsonProperty("reasoningEfforts") List<String> reasoningEfforts) {}
}
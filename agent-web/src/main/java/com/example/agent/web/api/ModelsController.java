package com.example.agent.web.api;

import com.example.agent.web.api.catalog.ModelCatalog;
import com.example.agent.web.api.dto.ModelsResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 模型列表端点（add-provider-catalog-abstract）。
 *
 * <p>{@code GET /api/chat/models} 返回 {@link ModelCatalog} 启动加载的嵌套目录
 * ({@code providers[]} 结构,每个 provider 含其 models[] 与每个 model 的 reasoningEfforts[])。
 * 前端 ModelSelect 两层菜单(外层 provider / 内层 model + effort 联动)消费此结构。
 */
@RestController
@RequestMapping("/api/chat")
@Profile("web")
public class ModelsController {
    private final ModelCatalog catalog;

    public ModelsController(ModelCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/models")
    public Mono<ModelsResponse> list() {
        return Mono.just(new ModelsResponse(catalog.providers()));
    }
}
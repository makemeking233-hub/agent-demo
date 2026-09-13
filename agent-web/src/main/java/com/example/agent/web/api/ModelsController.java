package com.example.agent.web.api;

import com.example.agent.web.api.dto.ModelsResponse;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 模型列表端点（add-reasoning-thinking-streaming）。
 *
 * <p>{@code GET /api/chat/models} 返回当前 supported-models 列表（含 supportsReasoning 标记）。
 * 前端 ModelsDropdown 组件用此列表填充下拉。
 */
@RestController
@RequestMapping("/api/chat")
@Profile("web")
public class ModelsController {
    private final Environment env;

    public ModelsController(Environment env) {
        this.env = env;
    }

    @GetMapping("/models")
    public Mono<ModelsResponse> list() {
        List<String> ids = ModelRegistry.supportedModels(env);
        var models =
                ids.stream()
                        .map(
                                id -> {
                                    boolean supportsReasoning = id.contains("reasoner")
                                            || id.contains("o1")
                                            || id.contains("o3")
                                            || id.contains("thinking")
                                            || id.contains("opus-4")
                                            || id.contains("sonnet-4");
                                    // add-models-dropdown-v0：supportsReasoning=true 时返回三档固定 effort；
                                    // 后续 change add-provider-catalog-abstract 升级为按 provider 动态化。
                                    List<String> efforts =
                                            supportsReasoning
                                                    ? List.of("low", "medium", "high")
                                                    : List.of();
                                    return new ModelsResponse.Model(id, id, supportsReasoning, efforts);
                                })
                        .toList();
        return Mono.just(new ModelsResponse(models));
    }
}
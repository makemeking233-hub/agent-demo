package com.example.agent.web.api;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.core.env.Environment;

/**
 * 模型注册表（add-reasoning-thinking-streaming）。
 *
 * <p>从 {@code agent.chat.supported-models} 配置读支持的模型列表（逗号分隔），用于：
 *
 * <ul>
 *   <li>{@link ChatController#resolveModel(String)} 校验前端传参合法性
 *   <li>{@link ModelsController#list()} 返回 /api/chat/models 列表
 *   <li>{@code /model} slash 命令切模型时校验
 * </ul>
 *
 * <p>默认列表：{@code deepseek-chat, deepseek-reasoner}。
 */
public final class ModelRegistry {
    /** 默认 supported-models 列表（兜底用） */
    public static final String DEFAULT_MODELS = "deepseek-chat,deepseek-reasoner";

    private ModelRegistry() {}

    /** 从 env 读 supported-models 列表，逗号分隔。空时回退到默认列表。 */
    public static List<String> supportedModels(Environment env) {
        String prop = env.getProperty("agent.chat.supported-models", DEFAULT_MODELS);
        if (prop == null || prop.isBlank()) {
            return Arrays.asList(DEFAULT_MODELS.split(","));
        }
        return Stream.of(prop.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** 检查 model 是否在 supported-models 列表中。 */
    public static boolean isSupported(String model, Environment env) {
        if (model == null) return false;
        return supportedModels(env).contains(model);
    }
}
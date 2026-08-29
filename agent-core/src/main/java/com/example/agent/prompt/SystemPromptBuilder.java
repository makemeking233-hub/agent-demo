package com.example.agent.prompt;

import com.example.agent.util.PromptLoader;

import java.util.List;

/**
 * 默认系统提示词组装器（模型无关，适配多 provider 扩展）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>从 classpath {@code /prompts/system.txt} 加载默认模板（身份声明不绑定任何模型厂商）
 *   <li>注入运行时元数据：{@code {providerName}} / {@code {modelName}}
 *   <li>注入可选段：附加指引（{@code {extraBlock}}）与长期记忆（{@code {memoryBlock}}），为空时整段省略
 *   <li>用户自定义覆盖优先：{@code --system-prompt} 非空白时直接返回，跳过模板
 * </ul>
 *
 * <p>扩展点：后续新增 provider（OpenAI / Anthropic 等）无需改本类；如需 provider 特有提示词段，
 * 在 {@code build} 的调用方按 provider 类型拼接 {@code extraGuidelines} 传入即可。
 */
public class SystemPromptBuilder {

    /**
     * 默认模板资源路径（classpath）
     */
    private static final String TEMPLATE_PATH = "/prompts/system.txt";

    /**
     * 模板缺失/加载失败时的最小兜底模板（保证身份声明始终存在）
     */
    private static final String FALLBACK_TEMPLATE =
            "你是 agent-demo，一个通过工具调用完成任务的终端 AI 助手。"
                    + "当前由 {providerName} 平台的 {modelName} 模型驱动。\n";

    /**
     * 组装完整系统提示词。
     *
     * @param providerName    provider 类型名（deepseek / minimax / openai / anthropic ...）
     * @param modelName       当前模型名（如 deepseek-chat / MiniMax-Text-01）
     * @param memorySection   长期记忆段（MemoryPromptBuilder 产物；为空则整段省略）
     * @param storageSection  运行时存储位置段（工作目录 / 日志 / 会话存档；为空则整段省略）
     * @param extraGuidelines 附加指引列表（来自 config memoryInject 等；为空则整段省略）
     * @param userOverride    用户自定义 system prompt（--system-prompt；非空白时直接返回）
     * @return 完整系统提示词文本
     */
    public String build(
            String providerName,
            String modelName,
            String memorySection,
            String storageSection,
            List<String> extraGuidelines,
            String userOverride) {
        if (userOverride != null && !userOverride.isBlank()) {
            return userOverride;
        }
        String template =
                PromptLoader.loadOrFallback(TEMPLATE_PATH, FALLBACK_TEMPLATE);
        return template
                .replace("{providerName}", providerName == null ? "" : providerName)
                .replace("{modelName}", modelName == null ? "" : modelName)
                .replace("{storageBlock}", buildStorageBlock(storageSection))
                .replace("{extraBlock}", buildExtraBlock(extraGuidelines))
                .replace("{memoryBlock}", buildMemoryBlock(memorySection));
    }

    /**
     * 组装运行时存储位置段（含标题；为空时返回空串，整段省略）。
     *
     * @param storageSection 存储位置说明文本
     * @return 存储位置段文本（可为空串）
     */
    private static String buildStorageBlock(String storageSection) {
        if (storageSection == null || storageSection.isBlank()) return "";
        return "# Runtime Storage / 运行时存储\n\n" + storageSection.trim() + "\n\n";
    }

    /**
     * 组装附加指引段（含标题；为空时返回空串，整段省略）。
     *
     * @param extraGuidelines 附加指引列表
     * @return 附加指引段文本（可为空串）
     */
    private static String buildExtraBlock(List<String> extraGuidelines) {
        if (extraGuidelines == null || extraGuidelines.isEmpty()) return "";
        String joined = String.join("\n", extraGuidelines);
        return "# Extra Guidelines / 附加指引\n\n" + joined + "\n\n";
    }

    /**
     * 组装长期记忆段（MemoryPromptBuilder 产物自带标题；为空时返回空串，整段省略）。
     *
     * @param memorySection 长期记忆段文本
     * @return 记忆段文本（可为空串）
     */
    private static String buildMemoryBlock(String memorySection) {
        if (memorySection == null || memorySection.isBlank()) return "";
        return memorySection.trim() + "\n";
    }
}

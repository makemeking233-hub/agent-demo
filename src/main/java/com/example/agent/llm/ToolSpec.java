package com.example.agent.llm;

import java.util.Map;

/**
 * 工具 schema 描述（发给 LLM 看的 JSON Schema 格式）。
 *
 * @param name        工具名（与 {@link Tool#name()} 对应）
 * @param description 工具用途描述（LLM 用来判断何时调用）
 * @param inputSchema JSON Schema 对象（type=object + properties + required）
 */
public record ToolSpec(String name, String description, Map<String, Object> inputSchema) {
}

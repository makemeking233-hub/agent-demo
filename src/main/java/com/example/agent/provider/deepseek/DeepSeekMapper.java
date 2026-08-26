package com.example.agent.provider.deepseek;

import com.example.agent.provider.ChatRequest;

import java.util.Map;

/**
 * DeepSeek 映射器门面：聚合 {@link DeepSeekRequestMapper} + {@link DeepSeekResponseParser}。
 *
 * <p>v0.2 拆分为两个独立类后保留此门面以兼容既有调用（{@code DeepSeekProvider} 仍引用它）。
 * 新代码请直接引用 RequestMapper / ResponseParser。
 */
public class DeepSeekMapper {
    private final DeepSeekRequestMapper requestMapper = new DeepSeekRequestMapper();
    private final DeepSeekResponseParser responseParser = new DeepSeekResponseParser();

    public Map<String, Object> toRequestBody(ChatRequest req) {
        return requestMapper.toRequestBody(req);
    }

    public java.util.Optional<com.example.agent.provider.StreamChunk> parseSseLine(String line) {
        return responseParser.parseSseLine(line);
    }
}
package com.example.agent.provider.deepseek;

import com.example.agent.provider.ChatRequest;

import java.util.Map;
import java.util.Optional;

/**
 * DeepSeek 映射器门面：聚合 {@link DeepSeekRequestMapper} + {@link DeepSeekResponseParser}。
 *
 * <p>v0.2 拆分为两个独立类后保留此门面以兼容既有调用（{@code DeepSeekProvider} 仍引用它）。
 * 新代码请直接引用 RequestMapper / ResponseParser。
 */
public class DeepSeekMapper {
    private final DeepSeekRequestMapper requestMapper = new DeepSeekRequestMapper();
    private final DeepSeekResponseParser responseParser = new DeepSeekResponseParser();

    /**
     * 构造请求体（委托给 {@link DeepSeekRequestMapper}）。
     * @param req 聊天请求
     * @return DeepSeek API 请求体 Map
     */
    public Map<String, Object> toRequestBody(ChatRequest req) {
        return requestMapper.toRequestBody(req);
    }

    /**
     * 解析单行 SSE（委托给 {@link DeepSeekResponseParser}）。
     * @param line SSE data 行
     * @return 解析出的 chunk（[DONE] / 空行 / 非 data 返回 empty）
     */
    public Optional<com.example.agent.provider.StreamChunk> parseSseLine(String line) {
        return responseParser.parseSseLine(line);
    }
}
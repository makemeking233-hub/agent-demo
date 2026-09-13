package com.example.agent.stats;

/**
 * 单轮统计增量（add-session-stats-bar）：由 {@code AgentLoop} 在完成一个回合时产出，供
 * {@link SessionStats#plus(TurnDelta)} 累加。
 *
 * <p>{@code cacheHitTokens}/{@code cacheMissTokens} 可空（provider 未返回该字段时为 {@code null}，
 * 用于区分"未返回"与"返回 0"）。
 *
 * @param steps 本轮工具调用次数
 * @param tokensIn 本轮 prompt token
 * @param tokensOut 本轮 completion token
 * @param reasoningTokens 本轮 reasoning token
 * @param llmMillis 本轮 LLM 耗时（毫秒）
 * @param toolMillis 本轮工具耗时（毫秒）
 * @param ttftMillis 本轮首 token 延迟（毫秒）
 * @param ttftSamples 本轮首 token 样本数（0 或 1）
 * @param cacheHitTokens 本轮缓存命中 token（可空）
 * @param cacheMissTokens 本轮缓存未命中 token（可空）
 */
public record TurnDelta(
        long steps,
        long tokensIn,
        long tokensOut,
        long reasoningTokens,
        long llmMillis,
        long toolMillis,
        long ttftMillis,
        long ttftSamples,
        Integer cacheHitTokens,
        Integer cacheMissTokens) {

    /** 全零增量（无工具、无用量）。 */
    public static TurnDelta empty() {
        return new TurnDelta(0, 0, 0, 0, 0, 0, 0, 0, null, null);
    }
}

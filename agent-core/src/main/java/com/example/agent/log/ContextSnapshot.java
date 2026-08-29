package com.example.agent.log;

import java.util.List;

/**
 * 每轮请求前的上下文快照（observability 事件 {@code context/snapshot} 的载荷）。
 *
 * <p>回答"模型这一轮看到了什么"：只记录元数据 + 截断后的 system prompt，不重复转储
 * 消息正文（正文由 user/message 与 assistant/message 事件覆盖），避免 session.jsonl 膨胀。
 *
 * @param turn 轮次序号（0 起）
 * @param systemPrompt 截断后的 system prompt（超 {@code snapshotMaxChars} 截断并带标记）
 * @param memoryInjected 本轮是否注入了长期记忆
 * @param compacted 上一轮之后是否发生过上下文压缩
 * @param recentFiles 压缩后重注入的最近文件路径列表
 * @param toolNames 本轮暴露给模型的工具名列表
 * @param messageCount 本轮 history 消息总数
 * @param estTokens 本轮 history 估算 token 数
 */
public record ContextSnapshot(
        int turn,
        String systemPrompt,
        boolean memoryInjected,
        boolean compacted,
        List<String> recentFiles,
        List<String> toolNames,
        int messageCount,
        int estTokens) {}

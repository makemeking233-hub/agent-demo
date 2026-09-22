package com.example.agent.memory;

/**
 * 记忆段来源（fix-memory-recall-wiring D2）。
 *
 * <p>抽象「按当前用户提问产出 system prompt 记忆段」这一职责，使 {@code AgentLoop} 只依赖该接口
 * 而不依赖具体的召回实现——便于测试注入，也便于后续把召回后端从「LLM 选择式」替换为
 * 「embedding 向量检索」时无需改动 {@code AgentLoop}。
 *
 * <p><b>契约</b>：
 *
 * <ul>
 *   <li>返回值为可直接追加到 system prompt 末尾的文本（自带 {@code # Persistent Agent Memory} 标题）
 *   <li>无命中、无可用记忆目录、查询为空、或任何内部失败时，返回**空串**而非抛异常
 *   <li>实现方不应假设调用次数：同一轮内可能被调用多次（调用方负责缓存）
 * </ul>
 */
@FunctionalInterface
public interface MemorySectionSource {

    /**
     * 按查询产出记忆段。
     *
     * @param query 当轮用户提问
     * @return 记忆段文本；无内容时返回空串（调用方据此跳过拼接）
     */
    String sectionFor(String query);
}

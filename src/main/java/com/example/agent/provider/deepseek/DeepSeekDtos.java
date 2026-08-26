package com.example.agent.provider.deepseek;

/**
 * 包级 marker 类（详见 design.md §6.1）。
 *
 * <p>DeepSeek wire format 通过 Jackson Map&lt;String,Object&gt; 处理，无独立 DTO。
 * 保留此文件作为包级 marker，方便后续扩展（如分页、tools 详细 schema 等）。
 */
public final class DeepSeekDtos {
    private DeepSeekDtos() {}
}
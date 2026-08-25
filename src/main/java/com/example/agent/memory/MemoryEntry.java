package com.example.agent.memory;

/** 单条记忆 record（标题 / 描述 / 文件名）。 */
public record MemoryEntry(String title, String description, String filename) {}
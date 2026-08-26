package com.example.agent.session;

import java.nio.file.Path;

/**
 * 会话元数据（ID + JSONL 文件路径）。
 *
 * @param id 会话 ID（UUID 或时间戳字符串）
 * @param file 对应 JSONL 文件路径（v0.1 写入位置）
 */
public record Session(String id, Path file) {}

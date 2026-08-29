package com.example.agent.testutil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话事件断言工具（日志驱动测试，observability 设计 D5）。
 *
 * <p>读取 {@code session.jsonl} 后支持：按 type 过滤、取 type 序列、断言字段；
 * 易变字段（timestamp / seq / callId / turn）在归一化时替换为占位符，保证
 * 两次运行的 E2E 断言稳定。
 */
public final class SessionEventAssertions {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 归一化占位符 */
    private static final String NORM = "<n>";

    /**
     * 读取事件流文件为 Map 列表（跳过空行/坏行）。
     *
     * @param sessionJsonl session.jsonl 路径
     * @return 事件列表（保持文件顺序）
     */
    public static List<Map<String, Object>> readEvents(Path sessionJsonl) {
        try {
            List<Map<String, Object>> events = new ArrayList<>();
            for (String line : Files.readAllLines(sessionJsonl)) {
                if (line.isBlank()) continue;
                try {
                    events.add(JSON.readValue(line, new TypeReference<Map<String, Object>>() {}));
                } catch (IOException ignored) {
                    // 跳过坏行（append-only 尾部容忍）
                }
            }
            return events;
        } catch (IOException e) {
            throw new IllegalStateException("读取会话事件流失败: " + sessionJsonl, e);
        }
    }

    /**
     * 按事件类型过滤。
     *
     * @param events 事件列表
     * @param type   目标 type（如 {@code tool/call}）
     * @return 该类型的事件子列表
     */
    public static List<Map<String, Object>> byType(List<Map<String, Object>> events, String type) {
        return events.stream().filter(e -> type.equals(e.get("type"))).toList();
    }

    /**
     * 提取事件类型序列（含全部事件）。
     *
     * @param events 事件列表
     * @return type 顺序列表
     */
    public static List<String> typeSequence(List<Map<String, Object>> events) {
        return events.stream().map(e -> String.valueOf(e.get("type"))).toList();
    }

    /**
     * 归一化副本：把 timestamp / seq / callId / turn 替换为 {@code <n>}，便于跨运行对比。
     *
     * @param events 事件列表
     * @return 归一化后的新列表（不改原列表）
     */
    public static List<Map<String, Object>> normalized(List<Map<String, Object>> events) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> e : events) {
            Map<String, Object> copy = new LinkedHashMap<>(e);
            copy.put("seq", NORM);
            if (copy.containsKey("timestamp")) copy.put("timestamp", NORM);
            if (copy.containsKey("callId")) copy.put("callId", NORM);
            if (copy.containsKey("turn")) copy.put("turn", NORM);
            out.add(copy);
        }
        return out;
    }

    private SessionEventAssertions() {}
}

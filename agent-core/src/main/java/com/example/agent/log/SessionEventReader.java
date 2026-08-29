package com.example.agent.log;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 会话事件流只读读取器（session.jsonl → 事件 Map 列表）。
 *
 * <p>供 Web UI 日志查看（LogController）与外部工具消费；与测试工具
 * {@code SessionEventAssertions} 职责区分：本类在 main 侧，坏行跳过、保持顺序。
 */
public final class SessionEventReader {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 读取事件流（保持文件顺序；坏行/空行跳过，append-only 尾部容忍）。
     *
     * @param sessionJsonl session.jsonl 路径
     * @return 事件列表
     */
    public static List<Map<String, Object>> readEvents(Path sessionJsonl) {
        List<Map<String, Object>> events = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(sessionJsonl)) {
                if (line.isBlank()) continue;
                try {
                    events.add(JSON.readValue(line, new TypeReference<Map<String, Object>>() {}));
                } catch (IOException ignored) {
                    // 跳过坏行
                }
            }
            return events;
        } catch (IOException e) {
            throw new IllegalStateException("读取会话事件流失败: " + sessionJsonl, e);
        }
    }

    private SessionEventReader() {}
}

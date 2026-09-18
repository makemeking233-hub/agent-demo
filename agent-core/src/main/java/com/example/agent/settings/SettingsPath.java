package com.example.agent.settings;

import com.example.agent.settings.exception.SettingsNotFoundException;
import com.example.agent.settings.exception.SettingsValidationException;

import java.util.Map;

/**
 * dot-notation PATCH 路径解析与读写（add-settings-foundation M1）。
 *
 * <p>支持 {@code general.appearance.preference} 形式；不识别的路径抛 404，值非法抛 400。
 */
public final class SettingsPath {

    private SettingsPath() {}

    /** 解析 {@code a.b.c} → {@code [a, b, c]}；空/空白/null → 抛 400 */
    public static String[] parse(String path) {
        if (path == null || path.isBlank()) {
            throw new SettingsValidationException("path_empty");
        }
        String trimmed = path.trim();
        if (trimmed.startsWith(".") || trimmed.endsWith(".")) {
            throw new SettingsValidationException("path_invalid: " + path);
        }
        return trimmed.split("\\.");
    }

    /** 在 root 中按 segments 取值；中途任一 key 不存在抛 404 */
    @SuppressWarnings("unchecked")
    public static Object get(Map<String, Object> root, String[] segments) {
        Object current = root;
        for (int i = 0; i < segments.length; i++) {
            String key = segments[i];
            if (!(current instanceof Map<?, ?> map)) {
                throw new SettingsNotFoundException("path_not_found");
            }
            Map<String, Object> m = (Map<String, Object>) map;
            if (!m.containsKey(key)) {
                throw new SettingsNotFoundException("path_not_found: " + key);
            }
            current = m.get(key);
        }
        return current;
    }

    /** 在 root 中按 segments 写入；不存在路径抛 404 */
    @SuppressWarnings("unchecked")
    public static void set(Map<String, Object> root, String[] segments, Object value) {
        Map<String, Object> current = root;
        for (int i = 0; i < segments.length - 1; i++) {
            String key = segments[i];
            if (!current.containsKey(key)) {
                throw new SettingsNotFoundException("path_not_found: " + key);
            }
            Object next = current.get(key);
            if (!(next instanceof Map<?, ?>)) {
                throw new SettingsNotFoundException("path_not_a_map: " + key);
            }
            current = (Map<String, Object>) next;
        }
        String lastKey = segments[segments.length - 1];
        if (!current.containsKey(lastKey)) {
            throw new SettingsNotFoundException("path_not_found: " + lastKey);
        }
        current.put(lastKey, value);
    }
}

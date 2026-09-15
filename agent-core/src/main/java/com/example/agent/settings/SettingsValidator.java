package com.example.agent.settings;

import com.example.agent.settings.exception.SettingsValidationException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * settings 值校验（add-settings-foundation M1 + add-settings-general-items M2）。
 *
 * <p>M2 增加字段枚举校验：appearance.preference / permission.mode / enterBehavior.mode。
 */
public final class SettingsValidator {

    public static final Set<String> ALLOWED_TOP_KEYS = Set.of("appearance", "permission", "enterBehavior");

    public static final Set<String> ALLOWED_GENERAL_SECTIONS = ALLOWED_TOP_KEYS;

    /** appearance.preference 枚举 */
    public static final Set<String> ALLOWED_APPEARANCE = Set.of("light", "dark", "system");

    /** permission.mode 枚举 */
    public static final Set<String> ALLOWED_PERMISSION = Set.of("plan", "ask", "danger-full", "dontAsk");

    /** enterBehavior.mode 枚举 */
    public static final Set<String> ALLOWED_ENTER_BEHAVIOR = Set.of("send", "queue", "newSession");

    private SettingsValidator() {}

    /**
     * 校验整个 settings view 的 schema。
     */
    public static void validateSchema(SettingsView view) {
        Map<String, Object> general = view.getGeneral();
        if (general == null) {
            throw new SettingsValidationException("general_missing");
        }
        for (Map.Entry<String, Object> e : general.entrySet()) {
            if (!ALLOWED_GENERAL_SECTIONS.contains(e.getKey())) {
                throw new SettingsValidationException("unknown_section: " + e.getKey());
            }
            if (!(e.getValue() instanceof Map<?, ?>)) {
                throw new SettingsValidationException("section_not_map: " + e.getKey());
            }
        }
    }

    /**
     * 校验单个字段值：含类型校验 + 字段枚举校验。
     */
    public static void validateField(String path, Object value) {
        if (value == null) {
            throw new SettingsValidationException("value_null: " + path);
        }
        if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
            throw new SettingsValidationException("value_unsupported_type: " + path);
        }
        if (value instanceof String s) {
            switch (path) {
                case "general.appearance.preference" -> requireInSet(path, s, ALLOWED_APPEARANCE);
                case "general.permission.mode" -> requireInSet(path, s, ALLOWED_PERMISSION);
                case "general.enterBehavior.mode" -> requireInSet(path, s, ALLOWED_ENTER_BEHAVIOR);
                default -> { /* unknown path is OK; 路径合法性由 SettingsPath.set 校验 */ }
            }
        }
    }

    private static void requireInSet(String path, String value, Set<String> allowed) {
        if (!allowed.contains(value)) {
            throw new SettingsValidationException(
                    "value_not_allowed: " + path + "=" + value + " (allowed: " + allowed + ")");
        }
    }

    public static Map<String, Object> emptyDefaults() {
        return new LinkedHashMap<>();
    }
}

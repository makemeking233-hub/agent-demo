package com.example.agent.settings;

import com.example.agent.settings.exception.SettingsValidationException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * settings 值校验（add-settings-foundation M1）。
 *
 * <p>M1 仅校验 schema 合法性（顶层有 general 字段、字段类型为字符串）。M2 会扩展为各字段枚举校验。
 */
public final class SettingsValidator {

    /** M1 允许的顶层 section 集合；M2 扩展。 */
    public static final Set<String> ALLOWED_TOP_KEYS = Set.of("appearance", "permission", "enterBehavior");

    /** general 下允许的 section 集合。 */
    public static final Set<String> ALLOWED_GENERAL_SECTIONS = ALLOWED_TOP_KEYS;

    private SettingsValidator() {}

    /**
     * 校验整个 settings view 的 schema。M1 阶段只校验结构；M2 接入具体枚举。
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
     * 校验单个字段值（M2 扩展）。M1 阶段作为占位，只校验值非 null。
     */
    public static void validateField(String path, Object value) {
        if (value == null) {
            throw new SettingsValidationException("value_null: " + path);
        }
        if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
            throw new SettingsValidationException("value_unsupported_type: " + path);
        }
    }

    /** M2 用：构造只含 default 值（无字段填充） */
    public static Map<String, Object> emptyDefaults() {
        return new LinkedHashMap<>();
    }
}

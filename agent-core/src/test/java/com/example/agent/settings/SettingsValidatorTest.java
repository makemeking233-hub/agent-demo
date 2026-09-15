package com.example.agent.settings;

import com.example.agent.settings.exception.SettingsValidationException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettingsValidatorTest {

    @Test
    void validateSchema_valid() {
        SettingsView view = new SettingsView(1, SettingsView.defaultGeneral(), 0);
        assertDoesNotThrow(() -> SettingsValidator.validateSchema(view));
    }

    @Test
    void validateSchema_missingGeneral_returnsDefaults() {
        // SettingsView.getGeneral() 在 null 时回退到 defaultGeneral()，所以 validateSchema 不会抛
        SettingsView view = new SettingsView(1, null, 0);
        assertDoesNotThrow(() -> SettingsValidator.validateSchema(view));
        assertNotNull(view.getGeneral());
    }

    @Test
    void validateSchema_unknownSection_throws() {
        Map<String, Object> g = new LinkedHashMap<>(SettingsView.defaultGeneral());
        g.put("unknown", "value");
        SettingsView view = new SettingsView(1, g, 0);
        assertThrows(SettingsValidationException.class, () -> SettingsValidator.validateSchema(view));
    }

    @Test
    void validateSchema_nonMapSection_throws() {
        Map<String, Object> g = new LinkedHashMap<>(SettingsView.defaultGeneral());
        g.put("appearance", "string-value");
        SettingsView view = new SettingsView(1, g, 0);
        assertThrows(SettingsValidationException.class, () -> SettingsValidator.validateSchema(view));
    }

    @Test
    void validateField_null_throws() {
        assertThrows(SettingsValidationException.class,
                () -> SettingsValidator.validateField("general.foo", null));
    }

    @Test
    void validateField_complexType_throws() {
        assertThrows(SettingsValidationException.class,
                () -> SettingsValidator.validateField("general.foo", java.util.List.of("x")));
    }

    @Test
    void validateField_stringOk() {
        assertDoesNotThrow(() -> SettingsValidator.validateField("general.foo", "bar"));
    }

    // ---------- M2 枚举校验 ----------

    @Test
    void validateField_appearanceValid() {
        assertDoesNotThrow(() -> SettingsValidator.validateField("general.appearance.preference", "light"));
        assertDoesNotThrow(() -> SettingsValidator.validateField("general.appearance.preference", "dark"));
        assertDoesNotThrow(() -> SettingsValidator.validateField("general.appearance.preference", "system"));
    }

    @Test
    void validateField_appearanceInvalid_throws() {
        assertThrows(SettingsValidationException.class,
                () -> SettingsValidator.validateField("general.appearance.preference", "neon"));
    }

    @Test
    void validateField_permissionValid() {
        for (String mode : new String[]{"plan", "ask", "danger-full", "dontAsk"}) {
            assertDoesNotThrow(() -> SettingsValidator.validateField("general.permission.mode", mode));
        }
    }

    @Test
    void validateField_permissionInvalid_throws() {
        assertThrows(SettingsValidationException.class,
                () -> SettingsValidator.validateField("general.permission.mode", "super-admin"));
    }

    @Test
    void validateField_enterBehaviorValid() {
        for (String mode : new String[]{"send", "queue", "newSession"}) {
            assertDoesNotThrow(() -> SettingsValidator.validateField("general.enterBehavior.mode", mode));
        }
    }

    @Test
    void validateField_enterBehaviorInvalid_throws() {
        assertThrows(SettingsValidationException.class,
                () -> SettingsValidator.validateField("general.enterBehavior.mode", "ignore"));
    }
}

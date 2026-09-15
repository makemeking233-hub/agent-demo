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
}

package com.example.agent.settings;

import com.example.agent.settings.exception.SettingsNotFoundException;
import com.example.agent.settings.exception.SettingsValidationException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsPathTest {

    @Test
    void parse_validPath() {
        assertEquals(3, SettingsPath.parse("general.appearance.preference").length);
        assertEquals("general", SettingsPath.parse("general")[0]);
    }

    @Test
    void parse_empty_throws() {
        assertThrows(SettingsValidationException.class, () -> SettingsPath.parse(""));
        assertThrows(SettingsValidationException.class, () -> SettingsPath.parse("   "));
        assertThrows(SettingsValidationException.class, () -> SettingsPath.parse(null));
    }

    @Test
    void parse_leadingOrTrailingDot_throws() {
        assertThrows(SettingsValidationException.class, () -> SettingsPath.parse(".foo"));
        assertThrows(SettingsValidationException.class, () -> SettingsPath.parse("foo."));
    }

    @Test
    void get_existingPath() {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> general = new LinkedHashMap<>();
        Map<String, Object> appearance = new LinkedHashMap<>();
        appearance.put("preference", "dark");
        general.put("appearance", appearance);
        root.put("general", general);

        Object value = SettingsPath.get(root, new String[]{"general", "appearance", "preference"});
        assertEquals("dark", value);
    }

    @Test
    void get_missingKey_throwsNotFound() {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> general = new LinkedHashMap<>();
        general.put("appearance", new LinkedHashMap<>());
        root.put("general", general);

        assertThrows(SettingsNotFoundException.class,
                () -> SettingsPath.get(root, new String[]{"general", "appearance", "preference"}));
    }

    @Test
    void set_updatesValue() {
        Map<String, Object> root = SettingsView.defaultGeneral();
        SettingsPath.set(root, new String[]{"appearance", "preference"}, "dark");
        assertEquals("dark", SettingsPath.get(root, new String[]{"appearance", "preference"}));
    }

    @Test
    void set_unknownPath_throwsNotFound() {
        Map<String, Object> root = SettingsView.defaultGeneral();
        assertThrows(SettingsNotFoundException.class,
                () -> SettingsPath.set(root, new String[]{"unknown"}, "x"));
        assertThrows(SettingsNotFoundException.class,
                () -> SettingsPath.set(root, new String[]{"appearance", "unknown"}, "x"));
    }

    @Test
    void set_viaRootWrapper_throughGeneral() {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> general = SettingsView.defaultGeneral();
        root.put("general", general);

        SettingsPath.set(general, new String[]{"appearance", "preference"}, "light");
        assertTrue(SettingsPath.get(general, new String[]{"appearance", "preference"}).equals("light"));
    }
}

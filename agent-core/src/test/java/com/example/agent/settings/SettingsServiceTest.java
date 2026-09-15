package com.example.agent.settings;

import com.example.agent.settings.exception.SettingsConflictException;
import com.example.agent.settings.exception.SettingsNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsServiceTest {

    @TempDir
    Path tmp;

    SettingsFile file;
    SettingsChangeBroadcaster broadcaster;
    SettingsService service;

    @BeforeEach
    void setUp() throws Exception {
        file = new SettingsFile(tmp);
        broadcaster = new SettingsChangeBroadcaster();
        service = new SettingsService(file, broadcaster);
    }

    @Test
    void read_emptyFile_returnsDefaults() throws Exception {
        file.ensureFile();
        SettingsView view = service.read();
        assertNotNull(view.getGeneral());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> appearance = (java.util.Map<String, Object>) view.getGeneral().get("appearance");
        assertEquals("system", appearance.get("preference"));
    }

    @Test
    void read_noFile_returnsDefaults() throws Exception {
        SettingsView view = service.read();
        assertNotNull(view.getGeneral());
    }

    @Test
    void writeAtomic_incrementsRevisionAndBroadcasts() throws Exception {
        AtomicInteger broadcastCount = new AtomicInteger();
        SettingsView[] capturedView = new SettingsView[1];
        broadcaster.register(v -> {
            broadcastCount.incrementAndGet();
            capturedView[0] = v;
        });

        SettingsView view = new SettingsView(1, SettingsView.defaultGeneral(), 0);
        view.getGeneral().put("appearance", new java.util.LinkedHashMap<>(java.util.Map.of("preference", "dark")));
        SettingsView written = service.writeAtomic(view);

        assertEquals(1L, written.getRevision());
        assertEquals(1, broadcastCount.get());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> appearance = (java.util.Map<String, Object>) capturedView[0].getGeneral().get("appearance");
        assertEquals("dark", appearance.get("preference"));
    }

    @Test
    void writeAtomic_preservesUnknownFields() throws Exception {
        file.ensureFile();
        // Write raw YAML with unknown field
        Files.writeString(file.file(), "version: 1\ngeneral:\n  appearance:\n    preference: dark\n  unknown_section: hello\n");
        service.resetForTest();
        SettingsView view = service.read();
        assertTrue(view.getGeneral().containsKey("unknown_section"));
    }

    @Test
    void writeAtomic_replacesExistingFile() throws Exception {
        SettingsView view1 = new SettingsView(1, SettingsView.defaultGeneral(), 0);
        service.writeAtomic(view1);
        long firstRevision = service.read().getRevision();

        SettingsView view2 = service.read();
        service.writeAtomic(view2);
        long secondRevision = service.read().getRevision();

        assertNotEquals(firstRevision, secondRevision);
        assertEquals(firstRevision + 1, secondRevision);
    }

    @Test
    void patch_updatesField() throws Exception {
        SettingsView view = service.patch("general.appearance.preference", "dark", null);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> appearance = (java.util.Map<String, Object>) view.getGeneral().get("appearance");
        assertEquals("dark", appearance.get("preference"));
    }

    @Test
    void patch_incrementsRevision() throws Exception {
        long r1 = service.read().getRevision();
        SettingsView v = service.patch("general.appearance.preference", "dark", null);
        assertEquals(r1 + 1, v.getRevision());
    }

    @Test
    void patch_unknownPath_throws404() {
        assertThrows(SettingsNotFoundException.class,
                () -> service.patch("general.unknown", "x", null));
    }

    @Test
    void patch_staleRevision_throws409() throws Exception {
        service.patch("general.appearance.preference", "dark", null);
        // Now current revision is 1; send 0 → conflict
        assertThrows(SettingsConflictException.class,
                () -> service.patch("general.appearance.preference", "light", 0L));
    }

    @Test
    void patch_validRevision_succeeds() throws Exception {
        service.patch("general.appearance.preference", "dark", null);
        SettingsView v = service.patch("general.appearance.preference", "light", 1L);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> appearance = (java.util.Map<String, Object>) v.getGeneral().get("appearance");
        assertEquals("light", appearance.get("preference"));
    }

    @Test
    void concurrentWrites_serializedByLock() throws Exception {
        // Smoke: 10 个并发 patch 不丢失
        int n = 10;
        Thread[] threads = new Thread[n];
        AtomicInteger errors = new AtomicInteger();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> {
                try {
                    service.patch("general.permission.mode", idx % 2 == 0 ? "ask" : "plan", null);
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();
        assertEquals(0, errors.get());
        SettingsView v = service.read();
        long rev = v.getRevision();
        assertEquals(n, rev);
        assertNotNull(v.getGeneral().get("permission"));
    }
}

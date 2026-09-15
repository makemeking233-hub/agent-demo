package com.example.agent.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsFileWatcherTest {

    @Test
    void watcher_lifecycleStartsAndStopsCleanly(@TempDir Path tmp) throws Exception {
        SettingsFile file = new SettingsFile(tmp);
        SettingsFileWatcher watcher = new SettingsFileWatcher(file, p -> {}, 50);
        watcher.start();
        Thread.sleep(100);
        watcher.close();
        // no exception = success
    }

    @Test
    void watcher_detectsExternalModification(@TempDir Path tmp) throws Exception {
        SettingsFile file = new SettingsFile(tmp);
        file.ensureFile();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Path> captured = new AtomicReference<>();
        SettingsFileWatcher watcher = new SettingsFileWatcher(file, p -> {
            captured.set(p);
            latch.countDown();
        }, 50);
        watcher.start();

        try {
            // Wait for watcher thread to start
            Thread.sleep(150);
            // Modify the file
            Files.writeString(file.file(), "version: 1\ngeneral:\n  appearance:\n    preference: dark\n");
            assertTrue(latch.await(3, TimeUnit.SECONDS), "watcher should fire within 3s");
            assertNotNull(captured.get());
            assertEquals("settings.yaml", captured.get().getFileName().toString());
        } finally {
            watcher.close();
        }
    }
}

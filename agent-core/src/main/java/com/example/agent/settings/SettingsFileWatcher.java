package com.example.agent.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * settings.yaml 文件监听（add-settings-foundation M1）。
 *
 * <p>JDK WatchService + 50ms debounce；外部编辑器保存时也能触发回调。
 */
public class SettingsFileWatcher implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SettingsFileWatcher.class);

    private final SettingsFile file;
    private final Consumer<Path> onChange;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ScheduledExecutorService debouncer;
    private final long debounceMs;
    private WatchService watchService;
    private Thread watcherThread;
    private volatile ScheduledFuture<?> pending;

    public SettingsFileWatcher(SettingsFile file, Consumer<Path> onChange) {
        this(file, onChange, 50);
    }

    public SettingsFileWatcher(SettingsFile file, Consumer<Path> onChange, long debounceMs) {
        this.file = file;
        this.onChange = onChange;
        this.debounceMs = debounceMs;
        this.debouncer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "settings-file-watcher-debouncer");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) return;
        Path home = file.home();
        Files.createDirectories(home);
        watchService = home.getFileSystem().newWatchService();
        home.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);
        watcherThread = new Thread(this::loop, "settings-file-watcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
        log.info("[settings] 文件监听启动: {}", home);
    }

    private void loop() {
        while (running.get()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (ClosedWatchServiceException | InterruptedException e) {
                return;
            }
            boolean changed = false;
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;
                Path changedPath = (Path) event.context();
                if (file.file().getFileName().equals(changedPath)) {
                    changed = true;
                }
            }
            if (changed) scheduleCallback();
            if (!key.reset()) return;
        }
    }

    private void scheduleCallback() {
        ScheduledFuture<?> prev = pending;
        if (prev != null) prev.cancel(false);
        pending = debouncer.schedule(() -> {
            try {
                onChange.accept(file.file());
            } catch (Exception e) {
                log.warn("[settings] 监听回调异常: {}", e.getMessage());
            }
        }, debounceMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) return;
        try {
            if (watchService != null) watchService.close();
        } catch (IOException ignored) {
        }
        if (watcherThread != null) watcherThread.interrupt();
        debouncer.shutdownNow();
    }
}

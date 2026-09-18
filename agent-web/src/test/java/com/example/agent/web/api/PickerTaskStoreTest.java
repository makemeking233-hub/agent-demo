package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** PickerTaskStore 单元测试 (picker-async). */
class PickerTaskStoreTest {

    private final PickerTaskStore store = new PickerTaskStore();

    @AfterEach
    void tearDown() {
        store.clearForTest();
    }

    private static ProcessBuilder baseBuilder(Path outFile) {
        ProcessBuilder pb = new ProcessBuilder();
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            pb.command("cmd.exe", "/c", "ping 127.0.0.1 -n 60");
        } else {
            pb.command("sh", "-c", "sleep 60");
        }
        pb.redirectOutput(outFile.toFile());
        return pb;
    }

    @Test
    void submit_startsProcessAndReturnsTask() throws Exception {
        Path markerDir = Files.createTempDirectory("picker-marker-");
        try {
            PickerTaskStore.Task task = store.submitWithProcess(outFile -> {
                ProcessBuilder pb = new ProcessBuilder();
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    pb.command("cmd.exe", "/c", "echo " + markerDir.toAbsolutePath());
                } else {
                    pb.command("sh", "-c", "echo " + markerDir.toAbsolutePath());
                }
                pb.redirectOutput(outFile.toFile());
                try {
                    return pb.start();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            assertThat(task.id()).isNotBlank();
            assertThat(task.process().isAlive()).isTrue();
            String path = task.future().get(10, TimeUnit.SECONDS);
            assertThat(path).isEqualTo(markerDir.toAbsolutePath().toString());
            assertFalse(task.process().isAlive());
        } finally {
            Files.deleteIfExists(markerDir);
        }
    }

    @Test
    void submit_cancelledProcess_returnsNull() throws Exception {
        PickerTaskStore.Task task = store.submitWithProcess(outFile -> {
            ProcessBuilder pb = new ProcessBuilder();
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                pb.command("cmd.exe", "/c", "exit 1");
            } else {
                pb.command("sh", "-c", "exit 1");
            }
            pb.redirectOutput(outFile.toFile());
            try {
                return pb.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        String path = task.future().get(10, TimeUnit.SECONDS);
        assertThat(path).isNull();
    }

    @Test
    void submit_invalidPath_fails() throws Exception {
        PickerTaskStore.Task task = store.submitWithProcess(outFile -> {
            ProcessBuilder pb = new ProcessBuilder();
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                pb.command("cmd.exe", "/c", "echo C:\\nonexistent\\path\\abc");
            } else {
                pb.command("sh", "-c", "echo /nonexistent/path/abc");
            }
            pb.redirectOutput(outFile.toFile());
            try {
                return pb.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> task.future().get(10, TimeUnit.SECONDS));
        assertThat(ex.getCause().getMessage()).contains("选定路径无效");
    }

    @Test
    void submit_startFailure_throws() {
        assertThrows(IOException.class, () -> store.submitWithProcess(outFile -> {
            throw new RuntimeException("mock failure");
        }));
    }

    @Test
    void cancel_killsProcess() throws Exception {
        PickerTaskStore.Task task = store.submitWithProcess(outFile -> {
            ProcessBuilder pb = new ProcessBuilder();
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                pb.command("cmd.exe", "/c", "ping 127.0.0.1 -n 60");
            } else {
                pb.command("sh", "-c", "sleep 60");
            }
            pb.redirectOutput(outFile.toFile());
            try {
                return pb.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertThat(task.process().isAlive()).isTrue();
        boolean cancelled = store.cancel(task.id());
        assertThat(cancelled).isTrue();
        Thread.sleep(500);
        assertFalse(task.process().isAlive());
    }

    @Test
    void cancel_unknownTask_returnsFalse() {
        assertThat(store.cancel("nonexistent")).isFalse();
    }

    @Test
    void get_unknownTask_returnsEmpty() {
        assertThat(store.get("nonexistent")).isEmpty();
    }
}

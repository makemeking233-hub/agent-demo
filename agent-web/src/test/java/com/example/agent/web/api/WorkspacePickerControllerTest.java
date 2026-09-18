package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** WorkspacePickerController 单元测试 (picker-dsh-flow). */
class WorkspacePickerControllerTest {

    @Test
    void buildCommand_windows_legacy() throws Exception {
        var pb = WorkspacePickerController.buildCommand("Windows 10", "", "legacy");
        assertThat(pb.command().get(0)).isEqualTo("powershell");
        assertThat(pb.command()).contains("-NoProfile", "-Command");
        assertThat(pb.command().get(3)).contains("FolderBrowserDialog");
    }

    @Test
    void buildCommand_windows_modern_usesWpf() throws Exception {
        var pb = WorkspacePickerController.buildCommand("Windows 10", "", "modern");
        assertThat(pb.command().get(0)).isEqualTo("powershell");
        assertThat(pb.command().get(3)).contains("OpenFolderDialog");
        assertThat(pb.command().get(3)).contains("PresentationFramework");
    }

    @Test
    void buildCommand_defaultIsModern() throws Exception {
        // 不指定 kind → 默认 modern
        var pb = WorkspacePickerController.buildCommand("Windows 10", "", null);
        assertThat(pb.command().get(3)).contains("OpenFolderDialog");
    }

    @Test
    void buildCommand_macos_osascript() throws Exception {
        var pb = WorkspacePickerController.buildCommand("Mac OS X", "", "modern");
        assertThat(pb.command().get(0)).isEqualTo("osascript");
        assertThat(pb.command().get(2)).contains("choose folder");
    }

    @Test
    void buildCommand_linux_zenity() throws Exception {
        var pb = WorkspacePickerController.buildCommand("Linux", ":0", "modern");
        assertThat(pb.command().get(0)).isEqualTo("zenity");
        assertThat(pb.command()).contains("--file-selection", "--directory");
    }

    @Test
    void buildCommand_linux_noDisplay_throws() {
        assertThrows(IOException.class,
                () -> WorkspacePickerController.buildCommand("Linux", "", "modern"));
    }

    @Test
    void buildCommand_linux_nullDisplay_throws() {
        assertThrows(IOException.class,
                () -> WorkspacePickerController.buildCommand("Linux", null, "modern"));
    }

    @Test
    void smoke_instantiate() {
        PickerTaskStore store = new PickerTaskStore();
        var ctrl = new WorkspacePickerController(store);
        assertThat(ctrl).isNotNull();
    }

    @Test
    void pickFolder_returns202WithTaskId() throws Exception {
        PickerTaskStore store = new PickerTaskStore();
        var ctrl = new WorkspacePickerController(store);
        ResponseEntity<Map<String, Object>> resp = ctrl.pickFolder(null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resp.getBody()).containsKey("task_id");
        assertThat(resp.getBody()).containsKey("timeout_seconds");
        assertThat(resp.getBody().get("kind")).isEqualTo("modern");
        store.cancel((String) resp.getBody().get("task_id"));
    }

    @Test
    void pickFolder_withKind_returnsCorrectCommand() throws Exception {
        PickerTaskStore store = new PickerTaskStore();
        var ctrl = new WorkspacePickerController(store);
        // 用现代 kind 时 buildCommand 应该选 WPF；这里只验证 controller 不抛
        ResponseEntity<Map<String, Object>> resp = ctrl.pickFolder("modern");
        assertThat(resp.getBody().get("kind")).isEqualTo("modern");
        store.cancel((String) resp.getBody().get("task_id"));
    }

    @Test
    void poll_unknownTask_returnsUnknown() {
        PickerTaskStore store = new PickerTaskStore();
        var ctrl = new WorkspacePickerController(store);
        ResponseEntity<Map<String, Object>> resp = ctrl.poll("nonexistent");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("status")).isEqualTo("unknown");
    }

    @Test
    void poll_runningTask_returnsRunning() throws Exception {
        PickerTaskStore store = new PickerTaskStore();
        var ctrl = new WorkspacePickerController(store);
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
        ResponseEntity<Map<String, Object>> resp = ctrl.poll(task.id());
        assertThat(resp.getBody().get("status")).isEqualTo("running");
        store.cancel(task.id());
    }

    @Test
    void poll_doneTask_returnsDone() throws Exception {
        PickerTaskStore store = new PickerTaskStore();
        var ctrl = new WorkspacePickerController(store);
        Path dir = Files.createTempDirectory("picker-test-");
        try {
            PickerTaskStore.Task task = store.submitWithProcess(outFile -> {
                ProcessBuilder pb = new ProcessBuilder();
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    pb.command("cmd.exe", "/c", "echo " + dir.toAbsolutePath());
                } else {
                    pb.command("sh", "-c", "echo " + dir.toAbsolutePath());
                }
                pb.redirectOutput(outFile.toFile());
                try {
                    return pb.start();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            String id = task.id();
            task.future().get(10, java.util.concurrent.TimeUnit.SECONDS);
            ResponseEntity<Map<String, Object>> resp = ctrl.poll(id);
            assertThat(resp.getBody().get("status")).isEqualTo("done");
            assertThat(resp.getBody().get("path")).isEqualTo(dir.toAbsolutePath().toString());
        } finally {
            Files.deleteIfExists(dir);
        }
    }

    @Test
    void poll_cancelledTask_returnsCancelled() throws Exception {
        PickerTaskStore store = new PickerTaskStore();
        var ctrl = new WorkspacePickerController(store);
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
        task.future().get(10, java.util.concurrent.TimeUnit.SECONDS);
        ResponseEntity<Map<String, Object>> resp = ctrl.poll(task.id());
        assertThat(resp.getBody().get("status")).isEqualTo("cancelled");
    }

    @Test
    void cancel_knownTask_returns204() throws Exception {
        PickerTaskStore store = new PickerTaskStore();
        var ctrl = new WorkspacePickerController(store);
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
        ResponseEntity<Void> resp = ctrl.cancel(task.id());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void cancel_unknownTask_returns404() {
        PickerTaskStore store = new PickerTaskStore();
        var ctrl = new WorkspacePickerController(store);
        ResponseEntity<Void> resp = ctrl.cancel("nonexistent");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

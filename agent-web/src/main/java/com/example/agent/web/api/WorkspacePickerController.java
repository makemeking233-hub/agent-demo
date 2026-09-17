package com.example.agent.web.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作区文件夹选择器（picker-dsh-flow）。
 *
 * <p>异步流程：
 * <ul>
 *   <li>POST /api/workspaces/pick-folder 立即返回 202 + task_id + timeout
 *   <li>GET /api/workspaces/pick-folder/{id} 轮询返回状态
 *   <li>DELETE /api/workspaces/pick-folder/{id} 中止进程
 * </ul>
 *
 * <p>支持两种 picker kind：
 * <ul>
 *   <li>modern（默认）：WPF + Microsoft.Win32.OpenFolderDialog（Vista+ 风格，DPI aware）
 *   <li>legacy：WinForms + System.Windows.Forms.FolderBrowserDialog（XP 风格，兜底）
 * </ul>
 */
@RestController
@RequestMapping("/api/workspaces")
@Profile("web")
public class WorkspacePickerController {

    private static final Logger log = LoggerFactory.getLogger(WorkspacePickerController.class);

    private final PickerTaskStore tasks;

    public WorkspacePickerController(PickerTaskStore tasks) {
        this.tasks = tasks;
    }

    @PostMapping("/pick-folder")
    public ResponseEntity<Map<String, Object>> pickFolder(
            @RequestParam(value = "kind", required = false) String kind) throws IOException {
        String resolvedKind = (kind == null || kind.isBlank()) ? "modern" : kind;
        PickerTaskStore.Task task = tasks.submitWithProcess(outFile -> {
            try {
                return startDialogProcess(outFile, resolvedKind);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("task_id", task.id());
        body.put("timeout_seconds", 300);
        body.put("kind", resolvedKind);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @GetMapping("/pick-folder/{taskId}")
    public ResponseEntity<Map<String, Object>> poll(@PathVariable String taskId) {
        Optional<PickerTaskStore.Task> opt = tasks.get(taskId);
        if (opt.isEmpty()) {
            return ResponseEntity.ok(Map.of("status", "unknown"));
        }
        PickerTaskStore.Task task = opt.get();
        CompletableFuture<String> future = task.future();
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        if (!future.isDone()) {
            body.put("status", "running");
            return ResponseEntity.ok(body);
        }
        try {
            String path = future.get(0, TimeUnit.SECONDS);
            if (path == null) {
                body.put("status", "cancelled");
            } else {
                body.put("status", "done");
                body.put("path", path);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            body.put("status", "interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            String msg = cause != null ? cause.getMessage() : e.getMessage();
            if (msg != null && msg.contains("超时")) {
                body.put("status", "timeout");
            } else if (msg != null && msg.contains("选定路径无效")) {
                body.put("status", "invalid_path");
            } else {
                body.put("status", "error");
                body.put("reason", msg);
            }
        } catch (TimeoutException e) {
            body.put("status", "running");
        }
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/pick-folder/{taskId}")
    public ResponseEntity<Void> cancel(@PathVariable String taskId) {
        boolean ok = tasks.cancel(taskId);
        return ok ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * 启动 OS 文件夹选择对话框 process；stdout 重定向到 outFile（避免 reader 阻塞）。
     */
    static Process startDialogProcess(Path outFile, String kind) throws IOException {
        ProcessBuilder pb = buildCommand(System.getProperty("os.name", ""), System.getenv("DISPLAY"), kind);
        pb.redirectOutput(outFile.toFile());
        return pb.start();
    }

    /**
     * 构造 OS 文件夹选择命令。
     *
     * @param osName       System.getProperty("os.name")
     * @param displayEnv   DISPLAY env（Linux 桌面是否启动）
     * @param kind         "modern"（默认，WPF） 或 "legacy"（WinForms）
     */
    static ProcessBuilder buildCommand(String osName, String displayEnv, String kind) throws IOException {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return new ProcessBuilder("powershell", "-NoProfile", "-Command",
                    windowsCommand(kind == null ? "modern" : kind));
        } else if (os.contains("mac") || os.contains("darwin")) {
            return new ProcessBuilder(
                    "osascript", "-e", "set f to choose folder with prompt \"Select Workspace Directory\"; return POSIX path of f");
        } else {
            if (displayEnv == null || displayEnv.isBlank()) {
                throw new IOException("无 DISPLAY 环境变量，无法打开 GUI 对话框");
            }
            return new ProcessBuilder(
                    "zenity", "--file-selection", "--directory",
                    "--title=Select Workspace Directory");
        }
    }

    /**
     * Windows picker 命令：modern = WPF OpenFolderDialog；legacy = WinForms FolderBrowserDialog。
     *
     * <p>WPF 比 WinForms 启动快 ~50%（Add-Type PresentationFramework 首次 JIT ~0.5s vs WinForms ~2s）；
     * OpenFolderDialog 是 Vista+ 风格，DPI aware，体验明显好。
     */
    static String windowsCommand(String kind) {
        if ("legacy".equalsIgnoreCase(kind)) {
            return "Add-Type -AssemblyName System.Windows.Forms; "
                    + "$f = New-Object System.Windows.Forms.FolderBrowserDialog; "
                    + "$f.Description = 'Select Workspace Directory'; "
                    + "if ($f.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) "
                    + "{ Write-Output $f.SelectedPath }";
        }
        // default: modern (WPF)
        return "Add-Type -AssemblyName PresentationFramework; "
                + "$dlg = New-Object Microsoft.Win32.OpenFolderDialog; "
                + "$dlg.Title = 'Select Workspace Directory'; "
                + "if ($dlg.ShowDialog() -eq $true) { Write-Output $dlg.FolderName }";
    }
}

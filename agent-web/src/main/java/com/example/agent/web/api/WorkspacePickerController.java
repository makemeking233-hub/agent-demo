package com.example.agent.web.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作区文件夹选择器（native-folder-picker）。
 *
 * <p>调起 OS 原生文件夹选择对话框：
 * <ul>
 *   <li>Windows: PowerShell + System.Windows.Forms.FolderBrowserDialog
 *   <li>macOS: osascript "choose folder"
 *   <li>Linux: zenity --file-selection --directory
 * </ul>
 *
 * <p>阻塞等待用户操作（最多 5 分钟）；用户取消或失败返回 200 + path=""。
 */
@RestController
@RequestMapping("/api/workspaces")
@Profile("web")
public class WorkspacePickerController {

    private static final Logger log = LoggerFactory.getLogger(WorkspacePickerController.class);
    private static final long TIMEOUT_MINUTES = 5;

    @PostMapping("/pick-folder")
    public ResponseEntity<Map<String, String>> pickFolder() throws IOException {
        ProcessBuilder pb = buildCommand();
        Process process = pb.start();
        boolean finished;
        try {
            finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.ok(Map.of("path", "", "reason", "interrupted"));
        }
        if (!finished) {
            log.warn("[picker] 5 分钟超时，强制结束进程");
            process.destroyForcibly();
            return ResponseEntity.ok(Map.of("path", "", "reason", "timeout"));
        }
        int code = process.exitValue();
        if (code != 0) {
            return ResponseEntity.ok(Map.of("path", "", "reason", "cancelled"));
        }
        String out = readOutput(process.getInputStream()).trim();
        if (out.isEmpty()) {
            return ResponseEntity.ok(Map.of("path", "", "reason", "cancelled"));
        }
        Path p = Paths.get(out);
        if (!p.isAbsolute() || !Files.exists(p) || !Files.isDirectory(p)) {
            log.warn("[picker] 选定路径无效: {}", out);
            return ResponseEntity.ok(Map.of("path", "", "reason", "invalid_path"));
        }
        return ResponseEntity.ok(Map.of("path", p.toAbsolutePath().toString()));
    }

    /**
     * 构造 OS 文件夹选择命令。
     */
    static ProcessBuilder buildCommand() throws IOException {
        return buildCommand(System.getProperty("os.name", ""), System.getenv("DISPLAY"));
    }

    /**
     * 可注入 OS 名的 buildCommand（测试用）。
     *
     * @param osName System.getProperty("os.name")
     * @param displayEnv DISPLAY env（Linux 桌面是否启动）
     */
    static ProcessBuilder buildCommand(String osName, String displayEnv) throws IOException {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return new ProcessBuilder(
                    "powershell",
                    "-NoProfile",
                    "-Command",
                    "Add-Type -AssemblyName System.Windows.Forms; "
                            + "$f = New-Object System.Windows.Forms.FolderBrowserDialog; "
                            + "$f.Description = 'Select Workspace Directory'; "
                            + "if ($f.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) "
                            + "{ Write-Output $f.SelectedPath }");
        } else if (os.contains("mac") || os.contains("darwin")) {
            return new ProcessBuilder(
                    "osascript", "-e", "set f to choose folder with prompt \"Select Workspace Directory\"; return POSIX path of f");
        } else {
            // Linux: zenity 优先；kdialog 备选
            if (displayEnv == null || displayEnv.isBlank()) {
                throw new IOException("无 DISPLAY 环境变量，无法打开 GUI 对话框");
            }
            return new ProcessBuilder(
                    "zenity", "--file-selection", "--directory",
                    "--title=Select Workspace Directory");
        }
    }

    private static String readOutput(InputStream in) throws IOException {
        byte[] buf = in.readAllBytes();
        return new String(buf, StandardCharsets.UTF_8);
    }
}

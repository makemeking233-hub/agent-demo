# 设计：native-folder-picker

## Context

WorkspacePickerModal 当前是浏览器内嵌的目录树，调用后端 listDir/mkdir API。改造为：调 OS 原生文件选择对话框，体验与系统一致。

## Goals / Non-Goals

**Goals**:
- 点击"+"按钮 → 后端调 OS 文件选择 → 返回绝对路径 → 前端展示路径 + 自动填 basename 作为 name → 用户可改名 → 确认
- 跨平台：Windows（PowerShell + FolderBrowserDialog）、macOS（osascript）、Linux（zenity/kdialog）
- 5 分钟超时（用户操作时间）
- 取消时返回 200 + null

**Non-Goals**:
- 不删除 FsController 的 listDir/mkdir（其他功能可能用到；标记 deprecated 留待观察）
- 不在前端模拟 OS 选择器（不实际弹浏览器 dialog）
- 不引入新的 npm 依赖
- 不做异步 + SSE（同步阻塞 + timeout 5 分钟已够用；用户取消立即返回）

## Decisions

### D1. 后端实现：ProcessBuilder 阻塞调用 + timeout

```java
@PostMapping("/pick-folder")
public ResponseEntity<Map<String, String>> pickFolder() throws IOException, InterruptedException {
    ProcessBuilder pb = switch (os) {
        case "win" -> new ProcessBuilder("powershell", "-NoProfile", "-Command",
            "Add-Type -AssemblyName System.Windows.Forms; " +
            "$f = New-Object System.Windows.Forms.FolderBrowserDialog; " +
            "if ($f.ShowDialog() -eq 'OK') { $f.SelectedPath }");
        case "mac" -> new ProcessBuilder("osascript", "-e",
            "set f to choose folder; return POSIX path of f");
        default -> new ProcessBuilder("zenity", "--file-selection", "--directory",
            "--title=Select Workspace Directory");
    };
    Process p = pb.start();
    if (!p.waitFor(5, TimeUnit.MINUTES)) {
        p.destroyForcibly();
        return ResponseEntity.ok(Map.of("path", ""));
    }
    String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    int code = p.exitValue();
    return code == 0 ? ResponseEntity.ok(Map.of("path", out)) : ResponseEntity.ok(Map.of("path", ""));
}
```

**为什么阻塞 + timeout**：用户操作是同步的；ProcessBuilder.waitFor 带 timeout 即可。如果用户取消（按 Cancel），进程 exit code 非 0，返回 null。

### D2. 安全性

- ProcessBuilder 不走 shell，直接 exec 命令（不需要 shell 黑名单）
- 参数只有固定字符串，无用户输入注入
- 返回路径需校验：
  - 必须是绝对路径
  - 必须存在
  - 必须是目录
- 超时后强制 destroy

### D3. 前端 WorkspacePickerModal 简化

新结构（参考 DSH）：
```
┌─────────────────────────────────────┐
│ Select Workspace Directory      [X]│
├─────────────────────────────────────┤
│ 路径:                                │
│ ┌─────────────────────────────────┐ │
│ │ [未选择文件夹]    [选择文件夹...] │ │
│ └─────────────────────────────────┘ │
│                                     │
│ 工作区名称:                          │
│ ┌─────────────────────────────────┐ │
│ │ md-main                        │ │
│ └─────────────────────────────────┘ │
│                                     │
│              [取消] [选择此目录]    │
└─────────────────────────────────────┘
```

### D4. 错误处理

- 后端超时 → 返回 null → 前端提示"操作超时，请重试"
- OS 工具缺失（Linux 无 zenity）→ 后端 500 → 前端提示"系统未安装 zenity"
- 用户取消 → 返回 null → 前端忽略

## Risks / Trade-offs

| # | 风险 | 缓解 |
|---|------|------|
| R1 | 阻塞 5 分钟占用 Spring 线程 | v0.1 简化版接受；后续可改为 SSE |
| R2 | Linux 无 zenity | 提示用户安装；fallback 到 kdialog |
| R3 | Windows PowerShell 启动慢（3-5s） | 接受；用户量少 |
| R4 | macOS osascript 需要辅助功能权限（首次） | 系统弹窗提示用户授权 |

## Migration Plan

无（仅 UI 改造）

## Open Questions

1. 是否删除 listDir/mkdir API？→ **保留**（其他功能可能用到）
2. Linux 优先用 zenity 还是 kdialog？→ **zenity 优先**（更通用）
3. 超时是 5 分钟还是更长？→ **5 分钟**（用户交互合理时间）

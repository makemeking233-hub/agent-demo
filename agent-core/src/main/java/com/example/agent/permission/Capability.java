package com.example.agent.permission;

/**
 * 能力分类（rewrite-permission-mode-dsh T1.2 引入）。
 *
 * <p>对齐 dsh web 的 capability seam 概念：fs / bash / terminal 三类能力共享一个
 * {@link SandboxPolicyService}，但每次工具调用解析 policy 时必须显式声明所属能力，
 * 防止 fs 的 policy 被误用到 bash 上（writableRoots 在 DANGER_FULL 之外的语义不同）。
 */
public enum Capability {
    /** 文件系统读写（ReadFile / WriteFile / EditFile / Ls）。 */
    FS,
    /** Shell 命令执行（Bash / Shell）。 */
    BASH,
    /** 终端 PTY（terminal-bash；v2 引入，本 change 暂不挂工具）。 */
    TERMINAL
}

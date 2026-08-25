package com.example.agent.render;

/**
 * v0.1 简化版流式打印机：把流式 chunk 直接打到 stdout。
 * v0.2 升级为 INLINE / CODE_FENCE 两态状态机（详见 design.md §2.3）。
 */
public class StreamingPrinter {
    public void onTextDelta(String text) {
        System.out.print(text);
    }

    public void onToolCallStart(String id, String name) {
        System.out.println();
        System.out.print("[tool] " + name + " ");
    }

    public void onToolCallArgs(String id, String argsDelta) {
        System.out.print(argsDelta);
    }

    public void onToolCallEnd(String id, String name, String args) {
        System.out.println();
        System.out.println("  args: " + args);
    }

    public void onFinished() {
        System.out.println();
    }

    public void onError(String message) {
        System.err.println("\n[error] " + message);
    }
}
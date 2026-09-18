package com.example.agent.permission;

import com.example.agent.tools.Tool;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sandbox policy 服务（rewrite-permission-mode-dsh T1.5 引入）。
 *
 * <p>对齐 dsh web 的 {@code ctx.sandboxPolicy}：每个工具调用通过 {@link #resolve} 解析当前完整
 * sandbox policy，包含 mode + workspaceRoot + tempRoots + capability。fs / bash / terminal 三个
 * capability 共享同一 service 实例，writableRoots 不 drift（dsh one-home 原则）。
 *
 * <p>状态：
 *
 * <ul>
 *   <li>{@link #mode} — 当前会话权限模式（{@link AtomicReference}，线程安全）
 *   <li>{@link #workspaceRoot} — 工作目录（构造期注入）
 *   <li>{@link #escalations} — escalate 临时升级表 ({@code streamId -> originalMode})
 * </ul>
 *
 * <p>使用：
 *
 * <ul>
 *   <li>普通 Java 代码：{@code new SandboxPolicyService(workingDir)} 后注入到 tool
 *   <li>Spring 注入：{@code @Bean} 方法创建；{@link #createForTest} 工厂方法供测试绕过 Spring
 * </ul>
 */
public class SandboxPolicyService {

    private final AtomicReference<SandboxMode> mode = new AtomicReference<>(SandboxMode.DEFAULT);
    private final Path workspaceRoot;
    private final Map<String, SandboxMode> escalations = new ConcurrentHashMap<>();

    /**
     * 构造（默认 mode = {@link SandboxMode#DEFAULT}，workspaceRoot 用 user.dir）。
     */
    public SandboxPolicyService() {
        this(Path.of(System.getProperty("user.dir")));
    }

    /**
     * 构造（指定 workspaceRoot）。
     *
     * @param workspaceRoot 工作目录（不可空）
     */
    public SandboxPolicyService(Path workspaceRoot) {
        if (workspaceRoot == null) {
            throw new IllegalArgumentException("workspaceRoot 不可空");
        }
        this.workspaceRoot = workspaceRoot;
    }

    /**
     * 解析当前 sandbox policy（每次工具调用都应调用以获取最新状态）。
     *
     * @param ctx        工具上下文（取 workingDirectory）
     * @param capability 调用所属能力（不可空）
     * @return 完整 sandbox policy
     * @throws IllegalArgumentException capability 为 null
     * @throws IllegalStateException    ctx.workingDirectory() 为 null
     */
    public SandboxPolicy resolve(Tool.ToolContext ctx, Capability capability) {
        if (capability == null) {
            throw new IllegalArgumentException("capability 不可空");
        }
        Path workingDir = (ctx != null) ? ctx.workingDirectory() : null;
        if (workingDir == null) {
            throw new IllegalStateException("ToolContext.workingDirectory 不可空（session 未绑定工作目录）");
        }
        SandboxPolicy policy = new SandboxPolicy(
                mode.get(),
                workingDir,
                WritableRoots.writableRoots(
                        new SandboxPolicy(mode.get(), workingDir, List.of(), capability)),
                capability);
        return policy;
    }

    /**
     * 设置当前权限模式（会话内实时切换）。
     *
     * @param newMode 新模式（null 视为 {@link SandboxMode#DEFAULT}）
     */
    public void setMode(SandboxMode newMode) {
        mode.set(newMode != null ? newMode : SandboxMode.DEFAULT);
    }

    /**
     * 当前权限模式（只读快照）。
     */
    public SandboxMode currentMode() {
        return mode.get();
    }

    /**
     * escalate 临时升级到 {@code targetMode}，保留原 mode 到 escalate 表；
     * turn 结束时调 {@link #restoreOnTurnEnd} 恢复。
     *
     * <p>同 stream 重复 escalate 覆盖前一次；不影响其他 stream。
     *
     * @param streamId   流 id（不可空）
     * @param targetMode 升级目标（不可空）
     */
    public void escalate(String streamId, SandboxMode targetMode) {
        if (streamId == null || streamId.isBlank()) {
            throw new IllegalArgumentException("streamId 不可空");
        }
        if (targetMode == null) {
            throw new IllegalArgumentException("targetMode 不可空");
        }
        // putIfAbsent: 第一次 escalate 记录原 mode, 后续同 stream escalate 不覆盖原记录
        // (turn 内可多次 escalate, 但 restore 总回到最初的 session mode)
        escalations.putIfAbsent(streamId, mode.getAndSet(targetMode));
    }

    /**
     * turn 结束时恢复 escalate 前的 mode。
     *
     * @param streamId 流 id
     * @return true 表示有 escalate 记录并恢复；false 表示无记录
     */
    public boolean restoreOnTurnEnd(String streamId) {
        if (streamId == null || streamId.isBlank()) return false;
        SandboxMode original = escalations.remove(streamId);
        if (original != null) {
            mode.set(original);
            return true;
        }
        return false;
    }

    /**
     * 测试工厂：绕开 Spring 直接构造（mode 与 workspaceRoot 全指定）。
     *
     * @param mode          初始 mode
     * @param workspaceRoot 工作目录
     * @return 配置好的 service
     */
    public static SandboxPolicyService createForTest(SandboxMode mode, Path workspaceRoot) {
        SandboxPolicyService svc = new SandboxPolicyService(workspaceRoot);
        svc.setMode(mode != null ? mode : SandboxMode.DEFAULT);
        return svc;
    }

    /**
     * 测试 / 调试：当前 escalate 表快照（不可变）。
     */
    public Map<String, SandboxMode> escalationsSnapshot() {
        return Map.copyOf(escalations);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SandboxPolicyService that)) return false;
        return Objects.equals(mode.get(), that.mode.get())
                && Objects.equals(workspaceRoot, that.workspaceRoot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode.get(), workspaceRoot);
    }
}

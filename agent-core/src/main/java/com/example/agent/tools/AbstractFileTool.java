package com.example.agent.tools;

import com.example.agent.permission.Capability;
import com.example.agent.permission.FsDenialKind;
import com.example.agent.permission.SandboxMode;
import com.example.agent.permission.SandboxPolicy;
import com.example.agent.permission.SandboxPolicyService;
import com.example.agent.permission.WritableRoots;
import com.example.agent.tools.file.ToolInput;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * 文件路径类工具的模板方法基类（v0.1 + rewrite-permission-mode-dsh T3 改造）。
 *
 * <p>统一处理：
 *
 * <ul>
 *   <li>{@link org.slf4j.Logger} 实例化（基于子类 {@code getClass()}）
 *   <li>路径 sandbox policy 检查（{@link WritableRoots} 派生 + per-call resolve）
 *   <li>返回 {@link ToolResult#error(String)} 的短路执行（用于越界等预执行错误）
 *   <li>写入路径 TOCTOU 防护（{@link #writeWithCas}：re-canonicalize + tmp 文件 + atomic move + 1 次 retry）
 * </ul>
 *
 * <p>子类只需实现 {@link #doExecute(ToolInput, java.nio.file.Path, Tool.ToolContext)}；写入型工具
 * 推荐调 {@link #writeWithCas} 完成 fs 调用（防 symlink swap）。
 *
 * @param <I> 输入类型（必须实现 {@link ToolInput}）
 */
public abstract class AbstractFileTool<I extends ToolInput> implements Tool<I, String> {
    /** 子类日志（按具体类名生成 logger） */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** JSON 反序列化器（record Input 由 Jackson 2.15+ 原生支持） */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 默认 sandbox policy（ctx.sandboxPolicy 为 null 时使用，全放行保留旧行为）。 */
    private static final SandboxPolicyService DEFAULT_POLICY =
            SandboxPolicyService.createForTest(SandboxMode.DANGER_FULL, Path.of(System.getProperty("user.dir")));

    /** 执行入口：模板方法（resolve → bounds 检查 → doExecute） */
    @Override
    public final Mono<ToolResult<String>> execute(I input, Tool.ToolContext ctx) {
        PathResult r = resolve(input, ctx);
        if (r.error() != null) return Mono.just(r.error());
        return doExecute(input, r.path(), ctx);
    }

    /**
     * 把模型参数 JSON 反序列化为类型化输入（子类声明 {@link #inputClass()}）。
     *
     * @param argumentsJson 模型生成的参数 JSON
     * @return 反序列化后的输入对象
     * @throws IllegalArgumentException JSON 格式错误时抛出（AgentLoop 转成错误 ToolResult）
     */
    @Override
    public I parseArguments(String argumentsJson) {
        try {
            return JSON.readValue(argumentsJson, inputClass());
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "参数 JSON 解析失败 (" + argumentsJson + "): " + e.getMessage(), e);
        }
    }

    /**
     * 输入类型（供 {@link #parseArguments} 反序列化）。
     *
     * @return 输入 record 的 Class
     */
    protected abstract Class<I> inputClass();

    /**
     * 子类实现的实际执行逻辑（在路径已校验通过后调用）。
     *
     * @param input 工具输入（已校验）
     * @param target 已 normalize 的绝对路径（已通过 sandbox policy 检查）
     * @param ctx 工具执行上下文
     * @return 异步执行结果
     */
    protected abstract Mono<ToolResult<String>> doExecute(
            I input, Path target, Tool.ToolContext ctx);

    /**
     * 解析 + sandbox policy 检查（一次返回路径或结构化拒绝结果）。
     *
     * <p>行为（rewrite-permission-mode-dsh T3.1 改造，按 spec §"mode × capability 默认裁决表"）：
     *
     * <ul>
     *   <li>DANGER_FULL → 不 fence：任何 path 放行（实际写入由 PermissionManager 决定）
     *   <li>PLAN / ASK → workspace 内放行；workspace 外 denied(WRITE_OUT_OF_BOUNDS / READ_OUT_OF_BOUNDS)
     *   <li>DONT_ASK → writableRoots（workspace + /tmp + tmpdir）内放行；外拒绝
     * </ul>
     *
     * <p>ctx.sandboxPolicy 为 null 时降级到 DEFAULT_POLICY（DANGER_FULL，保留 v0.1 行为）。
     *
     * @param input 工具输入
     * @param ctx 工具上下文
     * @return {@link PathResult}（错误时 error 非空 + denial 非空）
     */
    private PathResult resolve(I input, Tool.ToolContext ctx) {
        String raw = input.path() == null ? "" : input.path();
        SandboxPolicyService svc = (ctx != null && ctx.sandboxPolicy() != null)
                ? ctx.sandboxPolicy() : DEFAULT_POLICY;
        Capability cap = capability();
        Path base = ctx.workingDirectory();
        Path resolved;
        try {
            SandboxPolicy policy = svc.resolve(ctx, cap);
            resolved = base.resolve(raw).normalize();
            SandboxMode mode = policy.mode();

            if (mode == SandboxMode.DANGER_FULL) {
                // 不 fence: 任何 path 放行 (PermissionManager 决定 ask/deny)
                return PathResult.ok(resolved);
            }

            if (mode == SandboxMode.PLAN || mode == SandboxMode.ASK) {
                // workspace 内放行; agentDataDir 内放行 (memory/logs/sessions 兼容 v0.1); 外拒绝
                if (resolved.startsWith(base)) {
                    return PathResult.ok(resolved);
                }
                Path data = ctx.agentDataDir();
                if (data != null && resolved.startsWith(data.toAbsolutePath().normalize())) {
                    return PathResult.ok(resolved);
                }
                FsDenialKind kind = isWriteCap(cap)
                        ? FsDenialKind.WRITE_OUT_OF_BOUNDS
                        : FsDenialKind.READ_OUT_OF_BOUNDS;
                return PathResult.denied(kind, mode, kind.suggestedMode(),
                        "路径越界（mode=" + mode.wireValue() + "）: " + raw);
            }

            if (mode == SandboxMode.DONT_ASK) {
                List<Path> writableRoots = policy.tempRoots();
                if (WritableRoots.contains(writableRoots, resolved)) {
                    return PathResult.ok(resolved);
                }
                return PathResult.denied(
                        FsDenialKind.WRITE_OUT_OF_BOUNDS,
                        mode,
                        FsDenialKind.WRITE_OUT_OF_BOUNDS.suggestedMode(),
                        "路径越界（mode=" + mode.wireValue()
                                + ", writableRoots=" + writableRoots + "）: " + raw);
            }
            // 不可达: 4 档 mode 穷尽
            return PathResult.ok(resolved);
        } catch (IllegalStateException e) {
            return PathResult.error(ToolResult.<String>error("SandboxPolicy 解析失败: " + e.getMessage()));
        }
    }

    /**
     * 子类声明 capability（默认 FS）。Bash / Terminal 类工具不继承本类，不需 override。
     */
    protected Capability capability() {
        return Capability.FS;
    }

    /** 该 capability 是否为写入类（决定 READ_OUT_OF_BOUNDS vs WRITE_OUT_OF_BOUNDS）。 */
    private boolean isWriteCap(Capability cap) {
        // AbstractFileTool 子类均为 FS read/write; 保守按 FS 写入处理
        // (具体 read vs write 由 Tool.category() 决定; 此处仅为路径层分类)
        return cap == Capability.FS;
    }

    /**
     * 乐观 CAS 写入（rewrite-permission-mode-dsh T3.5.1，Q3 决策）。
     *
     * <p>步骤：
     *
     * <ol>
     *   <li>写入前 {@code target.toRealPath()} 重新解析（捕获自 resolve 以来发生的 symlink swap）
     *   <li>canonicalize 失败时回退原路径 + WARN 日志
     *   <li>写 tmp 文件（{@code target.getParent()/.tmp-<random>.cas}）
     *   <li>原子 move（{@link StandardCopyOption#ATOMIC_MOVE}），跨 fs 退化为非原子 move
     *   <li>冲突 retry 一次（最多 1 次 re-canonicalize + 重写）
     * </ol>
     *
     * @param target  写入目标（已 normalize）
     * @param content 写入内容（UTF-8 字符串）
     * @return 写入成功时的目标路径
     * @throws IOException 写入失败（已 retry 后仍失败）
     */
    protected Path writeWithCas(Path target, String content) throws IOException {
        // 1. TOCTOU re-canonicalize
        Path fresh;
        try {
            fresh = target.toRealPath();
        } catch (IOException e) {
            log.warn("writeWithCas: canonicalize 失败, 回退原路径: {} ({})", target, e.getMessage());
            fresh = target;
        }
        // 2. 写 tmp
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("target 没有父目录: " + target);
        }
        Path tmp = Files.createTempFile(parent, ".tmp-", ".cas");
        try {
            Files.writeString(tmp, content);
            // 3. atomic move
            try {
                Files.move(tmp, fresh, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException crossFs) {
                // 跨 fs 退化 (Windows 不同盘 / Docker volume)
                Files.move(tmp, fresh, StandardCopyOption.REPLACE_EXISTING);
            } catch (AccessDeniedException retry) {
                // 4. 冲突 retry 一次: re-canonicalize + 重写
                Path reFresh;
                try {
                    reFresh = target.toRealPath();
                } catch (IOException e) {
                    throw retry;
                }
                if (reFresh.equals(fresh)) {
                    throw retry;
                }
                log.warn("writeWithCas: 冲突 retry, target={} fresh={} reFresh={}", target, fresh, reFresh);
                Files.writeString(tmp, content);
                try {
                    Files.move(tmp, reFresh, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, reFresh, StandardCopyOption.REPLACE_EXISTING);
                }
                fresh = reFresh;
            }
            return fresh;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}

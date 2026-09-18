package com.example.agent.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.permission.FsDenialKind;
import com.example.agent.permission.PermissionManager;
import com.example.agent.permission.SandboxMode;
import com.example.agent.permission.SandboxPolicyService;
import com.example.agent.signal.AbortSignal;
import com.example.agent.tools.file.ToolInput;
import com.example.agent.tools.file.WriteFileTool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AbstractFileTool sandbox policy 行为测试（rewrite-permission-mode-dsh T4）。
 *
 * <p>覆盖：
 *
 * <ul>
 *   <li>workspace 内 write 放行（ASK / DANGER_FULL）
 *   <li>workspace 外 write 拒绝（WRITE_OUT_OF_BOUNDS + suggestedMode=DANGER_FULL）
 *   <li>PLAN 模式下 workspace 外拒绝
 *   <li>DANGER_FULL 下不 fence
 *   <li>DONT_ASK 下 workspace + tmpdir 允许，其他拒绝
 *   <li>ctx.sandboxPolicy 为 null 降级到 DEFAULT_POLICY
 *   <li>writeWithCas 正常写 / 覆盖 / 嵌套目录 / 清理 tmp / 失败保留
 * </ul>
 *
 * <p>使用现有 {@link WriteFileTool} 作为 AbstractFileTool 子类入口（避免 ToolInput sealed 接口限制）。
 * 通过反射调 {@code AbstractFileTool.resolve} 验证 sandbox policy 行为。
 */
class AbstractFileToolTest {

    private static final AbortSignal NEVER = () -> false;

    private static Tool.ToolContext ctx(Path workingDir, SandboxPolicyService svc) {
        return new Tool.ToolContext(workingDir, new PermissionManager(), NEVER, null, svc);
    }

    /** 反射调 AbstractFileTool.resolve（private 验证 sandbox policy 行为） */
    private static PathResult invokeResolve(Tool<?, ?> tool, ToolInput input, Tool.ToolContext ctx) {
        try {
            var m = AbstractFileTool.class.getDeclaredMethod("resolve", ToolInput.class, Tool.ToolContext.class);
            m.setAccessible(true);
            return (PathResult) m.invoke(tool, input, ctx);
        } catch (Exception e) {
            throw new RuntimeException("reflect invoke resolve failed", e);
        }
    }

    @Test
    void resolvesInsideWorkspaceAsOk(@TempDir Path tmp) {
        var svc = SandboxPolicyService.createForTest(SandboxMode.ASK, tmp);
        var tool = new WriteFileTool();
        var in = new WriteFileTool.Input("foo.txt", "content");
        PathResult r = invokeResolve(tool, in, ctx(tmp, svc));
        assertNotNull(r.path(), "workspace 内应放行");
        assertNull(r.error());
        assertNull(r.denial());
    }

    @Test
    void rejectsOutsideWorkspaceAsDenied(@TempDir Path tmp) {
        var svc = SandboxPolicyService.createForTest(SandboxMode.ASK, tmp);
        var tool = new WriteFileTool();
        var in = new WriteFileTool.Input("../outside.txt", "content");
        PathResult r = invokeResolve(tool, in, ctx(tmp, svc));
        assertNull(r.path());
        assertNotNull(r.error());
        assertNotNull(r.denial());
        assertEquals(FsDenialKind.WRITE_OUT_OF_BOUNDS, r.denial().kind());
        assertEquals(SandboxMode.ASK, r.denial().currentMode());
        assertEquals(SandboxMode.DANGER_FULL, r.denial().suggestedMode());
    }

    @Test
    void planModeRejectsOutsideWorkspace(@TempDir Path tmp) {
        var svc = SandboxPolicyService.createForTest(SandboxMode.PLAN, tmp);
        var tool = new WriteFileTool();
        // workspace 内 PLAN 仍放行 (写入裁决交给 PermissionManager / Tool.checkPermissions)
        PathResult inside = invokeResolve(tool, new WriteFileTool.Input("a.txt", "x"), ctx(tmp, svc));
        assertNotNull(inside.path(), "PLAN workspace 内 resolve 放行");
        // workspace 外拒绝
        PathResult outside = invokeResolve(tool, new WriteFileTool.Input("../b.txt", "x"), ctx(tmp, svc));
        assertNotNull(outside.denial(), "PLAN workspace 外拒绝");
        assertEquals(SandboxMode.PLAN, outside.denial().currentMode());
    }

    @Test
    void dangerFullModeAllowsAnyPath(@TempDir Path tmp) {
        var svc = SandboxPolicyService.createForTest(SandboxMode.DANGER_FULL, tmp);
        var tool = new WriteFileTool();
        // DANGER_FULL: writableRoots 空 -> 不 fence -> 任何 path resolve 放行
        PathResult r = invokeResolve(tool, new WriteFileTool.Input("../outside.txt", "x"), ctx(tmp, svc));
        assertNotNull(r.path(), "DANGER_FULL 不 fence");
    }

    @Test
    void dontAskModeRestrictsToWritableRoots(@TempDir Path tmp) {
        var svc = SandboxPolicyService.createForTest(SandboxMode.DONT_ASK, tmp);
        var tool = new WriteFileTool();
        // workspace 内放行
        PathResult inside = invokeResolve(tool, new WriteFileTool.Input("inside.txt", "x"), ctx(tmp, svc));
        assertNotNull(inside.path());
        // workspace 外 + 绝对路径 (DONT_ASK writableRoots 不含 /usr/bin 等系统路径) -> denied
        PathResult outside = invokeResolve(tool, new WriteFileTool.Input("/usr/bin/ls", "x"), ctx(tmp, svc));
        assertNotNull(outside.denial(), "DONT_ASK workspace 外 + 非 tmpdir 拒绝");
    }

    @Test
    void nullSandboxPolicyFallsBackToDefault(@TempDir Path tmp) {
        var tool = new WriteFileTool();
        var ctxNullSvc = new Tool.ToolContext(tmp, new PermissionManager(), NEVER, null, null);
        // DEFAULT_POLICY 是 DANGER_FULL, 不 fence
        PathResult r = invokeResolve(tool, new WriteFileTool.Input("../a.txt", "x"), ctxNullSvc);
        assertNotNull(r.path(), "降级到 DANGER_FULL, 不 fence");
    }

    // ---- writeWithCas（Q3 决策 / T3.5.1）----

    @Test
    void writeWithCasCreatesFile(@TempDir Path tmp) throws Exception {
        var tool = new WriteFileTool();
        Path target = tmp.resolve("file.txt");
        Path written = invokeWriteWithCas(tool, target, "hello");
        assertEquals(target.toRealPath(), written);
        assertEquals("hello", Files.readString(written));
    }

    @Test
    void writeWithCasOverwritesExisting(@TempDir Path tmp) throws Exception {
        var tool = new WriteFileTool();
        Path target = tmp.resolve("file.txt");
        Files.writeString(target, "original");
        invokeWriteWithCas(tool, target, "updated");
        assertEquals("updated", Files.readString(target));
    }

    @Test
    void writeWithCasHandlesNestedDir(@TempDir Path tmp) throws Exception {
        var tool = new WriteFileTool();
        Path nested = Files.createDirectory(tmp.resolve("nested"));
        Path target = nested.resolve("file.txt");
        invokeWriteWithCas(tool, target, "data");
        assertEquals("data", Files.readString(target));
    }

    @Test
    void writeWithCasCleansUpTmpOnSuccess(@TempDir Path tmp) throws Exception {
        var tool = new WriteFileTool();
        Path target = tmp.resolve("file.txt");
        invokeWriteWithCas(tool, target, "data");
        long tmpCount = Files.list(tmp).filter(p -> p.getFileName().toString().startsWith(".tmp-")).count();
        assertEquals(0L, tmpCount, "无残留 .tmp-*.cas 文件");
    }

    @Test
    void writeWithCasRetainsTargetAfterFailure(@TempDir Path tmp) throws Exception {
        var tool = new WriteFileTool();
        Path target = tmp.resolve("file.txt");
        Files.writeString(target, "keep-me");
        // 写 target.parent() 不存在会失败 -> tmp 文件应清理
        Path ghost = tmp.resolve("ghost/file.txt");
        assertThrows(IOException.class, () -> invokeWriteWithCas(tool, ghost, "data"));
        assertEquals("keep-me", Files.readString(target), "失败时不影响已存在文件");
    }

    /** 反射调 AbstractFileTool.writeWithCas（protected 验证 Q3 决策） */
    private static Path invokeWriteWithCas(AbstractFileTool<?> tool, Path target, String content) throws Exception {
        var m = AbstractFileTool.class.getDeclaredMethod("writeWithCas", Path.class, String.class);
        m.setAccessible(true);
        try {
            return (Path) m.invoke(tool, target, content);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                if (cause instanceof IOException io) throw io;
                throw cause;
            }
            throw e;
        }
    }

    // ---- FsDenialKind 集成（与 sandbox-policy spec §"DenialKind 与 escalation 推荐映射" 对齐）----

    @Test
    void denialKindSuggestedModeForWriteOutOfBounds() {
        assertEquals(SandboxMode.DANGER_FULL, FsDenialKind.WRITE_OUT_OF_BOUNDS.suggestedMode());
    }

    @Test
    void denialKindSuggestedModeForModeRejected() {
        assertEquals(SandboxMode.ASK, FsDenialKind.MODE_REJECTED.suggestedMode());
    }

    @Test
    void denialKindSuggestedModeForSensitivePathIsNull() {
        assertNull(FsDenialKind.SENSITIVE_PATH.suggestedMode());
    }

    @Test
    void denialMarkerRendersMode() {
        // marker 文本应含当前 mode 名称, 便于模型识别
        PathResult r = PathResult.denied(
                FsDenialKind.WRITE_OUT_OF_BOUNDS,
                SandboxMode.ASK,
                SandboxMode.DANGER_FULL,
                "test reason");
        String content = r.error().toModelContent();
        assertTrue(content.contains("ask"), "marker 含 mode 名: " + content);
        assertTrue(content.contains("write-out-of-bounds"), "marker 含 kind 名: " + content);
    }
}

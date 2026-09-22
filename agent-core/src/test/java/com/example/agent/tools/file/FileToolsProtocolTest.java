package com.example.agent.tools.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.permission.PermissionDecision;
import com.example.agent.tools.Tool.ToolContext;
import com.example.agent.tools.ToolCategory;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 四个文件工具的**协议面**覆盖（fix-jacoco-rule）。
 *
 * <p>{@code com.example.agent.tools.file} 此前 LINE 0.62 / BRANCH 0.50：既有的
 * {@code *ToolTest} 只调 {@code execute}，于是 {@code name} / {@code description} /
 * {@code inputSchema} / {@code category} / {@code isReadOnly} / {@code isDestructive} /
 * {@code checkPermissions} / {@code renderUse} / {@code renderResult} 这些「给模型和权限层看的」
 * 方法从未被执行——它们恰恰是工具契约的一部分，漏测会让 schema 或权限默认值被误改而无人发现。
 *
 * <p>本类不重复 {@code execute} 的行为测试（已有专门测试），只补协议面。
 */
class FileToolsProtocolTest {

    private final ToolContext ctx = new ToolContext(Path.of("."), null, () -> false);

    @SuppressWarnings("unchecked")
    private static List<String> required(Map<String, Object> schema) {
        return (List<String>) schema.get("required");
    }

    private static Map<String, Object> properties(Map<String, Object> schema) {
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        return props;
    }

    // ---------- ReadFile ----------

    @Test
    void readFileProtocolSurface() {
        var t = new ReadFileTool();

        assertEquals("ReadFile", t.name());
        assertFalse(t.description().isBlank());
        assertEquals(ToolCategory.READ, t.category());
        assertTrue(t.isReadOnly(new ReadFileTool.Input("a.txt")));
        assertEquals(List.of("path"), required(t.inputSchema()));
        assertTrue(properties(t.inputSchema()).containsKey("path"));

        // 安全路径 → allow；含 .. → deny
        assertEquals(
                PermissionDecision.allow(),
                t.checkPermissions(new ReadFileTool.Input("a/b.txt"), ctx));
        assertEquals(
                PermissionDecision.deny(),
                t.checkPermissions(new ReadFileTool.Input("../escape.txt"), ctx));

        assertTrue(t.renderUse(new ReadFileTool.Input("a/b.txt")).contains("a/b.txt"));

        // renderResult 超 100 字符截断
        assertEquals("short", t.renderResult("short"));
        String longOut = t.renderResult("x".repeat(200));
        assertEquals(103, longOut.length());
        assertTrue(longOut.endsWith("..."));
        // 恰好 100 字符不截断（边界）
        assertEquals(100, t.renderResult("y".repeat(100)).length());
    }

    // ---------- Ls ----------

    @Test
    void lsProtocolSurface() {
        var t = new LsTool();

        assertEquals("Ls", t.name());
        assertFalse(t.description().isBlank());
        assertEquals(ToolCategory.READ, t.category());
        assertTrue(t.isReadOnly(new LsTool.Input("sub")));
        assertEquals(List.of("path"), required(t.inputSchema()));

        assertEquals(PermissionDecision.allow(), t.checkPermissions(new LsTool.Input("sub"), ctx));
        assertEquals(
                PermissionDecision.deny(),
                t.checkPermissions(new LsTool.Input("../up"), ctx));

        assertTrue(t.renderUse(new LsTool.Input("sub")).contains("sub"));
        assertEquals("原样", t.renderResult("原样"));
    }

    // ---------- WriteFile ----------

    @Test
    void writeFileProtocolSurface() {
        var t = new WriteFileTool();

        assertEquals("WriteFile", t.name());
        assertFalse(t.description().isBlank());
        assertEquals(ToolCategory.WRITE, t.category());
        assertTrue(t.isDestructive(new WriteFileTool.Input("a.txt", "x")));
        assertEquals(List.of("path", "content"), required(t.inputSchema()));
        assertTrue(properties(t.inputSchema()).containsKey("content"));

        // 安全路径 → ask（写操作需确认）；含 .. → deny（路径守卫优先于 ask）
        assertEquals(
                PermissionDecision.ask(),
                t.checkPermissions(new WriteFileTool.Input("a.txt", "x"), ctx));
        assertEquals(
                PermissionDecision.deny(),
                t.checkPermissions(new WriteFileTool.Input("../escape.txt", "x"), ctx));

        assertTrue(t.renderUse(new WriteFileTool.Input("a.txt", "x")).contains("a.txt"));
        assertEquals("原样", t.renderResult("原样"));
    }

    // ---------- EditFile ----------

    @Test
    void editFileProtocolSurface() {
        var t = new EditFileTool();

        assertEquals("EditFile", t.name());
        assertFalse(t.description().isBlank());
        assertEquals(ToolCategory.WRITE, t.category());
        assertTrue(t.isDestructive(new EditFileTool.Input("a.txt", "o", "n")));
        assertEquals(List.of("path", "oldText", "newText"), required(t.inputSchema()));

        // EditFile 不走 PathGuard：一律 ask
        assertEquals(
                PermissionDecision.ask(),
                t.checkPermissions(new EditFileTool.Input("a.txt", "o", "n"), ctx));
        assertEquals(
                PermissionDecision.ask(),
                t.checkPermissions(new EditFileTool.Input("../x", "o", "n"), ctx));

        // renderUse 含 oldText 长度；oldText 为 null 时按 0 计（三元假分支）
        assertTrue(
                t.renderUse(new EditFileTool.Input("a.txt", "abcd", "n"))
                        .contains("oldLen=4"));
        assertTrue(
                t.renderUse(new EditFileTool.Input("a.txt", null, "n")).contains("oldLen=0"));

        assertEquals("原样", t.renderResult("原样"));
    }
}

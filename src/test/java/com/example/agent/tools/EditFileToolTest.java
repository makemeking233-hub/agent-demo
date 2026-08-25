package com.example.agent.tools;

import com.example.agent.permission.PermissionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditFileToolTest {
    @TempDir
    Path tmp;

    private Tool.ToolContext ctx() {
        return new Tool.ToolContext(tmp, new PermissionManager(), () -> false);
    }

    @Test
    void replacesExactText() throws Exception {
        Files.writeString(tmp.resolve("a.txt"), "hello world");
        var tool = new EditFileTool();
        StepVerifier.create(tool.execute(new EditFileTool.Input("a.txt", "world", "Java"), ctx()))
            .assertNext(r -> assertFalse(r.isError())).verifyComplete();
        assertEquals("hello Java", Files.readString(tmp.resolve("a.txt")));
    }

    @Test
    void reportsMissingText() throws Exception {
        Files.writeString(tmp.resolve("a.txt"), "hello");
        var tool = new EditFileTool();
        StepVerifier.create(tool.execute(new EditFileTool.Input("a.txt", "world", "Java"), ctx()))
            .assertNext(r -> assertTrue(r.isError()))
            .verifyComplete();
    }

    @Test
    void rejectsMultipleMatches() throws Exception {
        Files.writeString(tmp.resolve("a.txt"), "foo foo foo");
        var tool = new EditFileTool();
        StepVerifier.create(tool.execute(new EditFileTool.Input("a.txt", "foo", "bar"), ctx()))
            .assertNext(r -> {
                assertTrue(r.isError());
                assertTrue(r.toModelContent().contains("matches") || r.toModelContent().contains("found"));
            })
            .verifyComplete();
    }
}
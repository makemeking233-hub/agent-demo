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

class WriteFileToolTest {
    @TempDir
    Path tmp;

    private Tool.ToolContext ctx() {
        return new Tool.ToolContext(tmp, new PermissionManager(), () -> false);
    }

    @Test
    void writesFile() {
        var tool = new WriteFileTool();
        StepVerifier.create(tool.execute(new WriteFileTool.Input("a.txt", "hi"), ctx()))
            .assertNext(r -> assertFalse(r.isError())).verifyComplete();
        try {
            assertEquals("hi", Files.readString(tmp.resolve("a.txt")));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void overwritesExisting() throws Exception {
        Files.writeString(tmp.resolve("a.txt"), "old");
        var tool = new WriteFileTool();
        StepVerifier.create(tool.execute(new WriteFileTool.Input("a.txt", "new"), ctx()))
            .assertNext(r -> assertFalse(r.isError())).verifyComplete();
        assertEquals("new", Files.readString(tmp.resolve("a.txt")));
    }

    @Test
    void rejectsPathTraversal() {
        var tool = new WriteFileTool();
        StepVerifier.create(tool.execute(new WriteFileTool.Input("../escape.txt", "x"), ctx()))
            .assertNext(r -> assertTrue(r.isError()))
            .verifyComplete();
    }
}
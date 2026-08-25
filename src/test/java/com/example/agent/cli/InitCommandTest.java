package com.example.agent.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InitCommandTest {
    @TempDir
    Path tmp;

    @Test
    void createsConfigFile(@TempDir Path dir) throws Exception {
        var cmd = new InitCommand();
        Path cfg = cmd.runForTest(dir);
        assertTrue(Files.exists(cfg));
        String content = Files.readString(cfg);
        assertTrue(content.contains("deepseek"));
        assertTrue(content.contains("deepseek-chat"));
    }

    @Test
    void createsSubdirectories(@TempDir Path dir) throws Exception {
        var cmd = new InitCommand();
        cmd.runForTest(dir);
        for (String sub : new String[]{"memory", "sessions", "cache", "logs"}) {
            assertTrue(Files.isDirectory(dir.resolve(sub)), "应创建 " + sub);
        }
    }
}
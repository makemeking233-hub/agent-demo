package com.example.agent.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class InitCommandTest {
    @TempDir Path tmp;

    @Test
    void createsConfigFile(@TempDir Path dir) throws Exception {
        var cmd = new InitCommand();
        Path cfg = cmd.runForTest(dir);
        assertTrue(Files.exists(cfg));
        String content = Files.readString(cfg);
        assertTrue(content.contains("deepseek"));
        // fix-cli-residue：默认 model 改为 deepseek-v4-flash（与 web profile 对齐）
        assertTrue(content.contains("deepseek-v4-flash"));
    }

    @Test
    void createsSubdirectories(@TempDir Path dir) throws Exception {
        var cmd = new InitCommand();
        cmd.runForTest(dir);
        for (String sub : new String[] {"memory", "sessions", "cache", "logs"}) {
            assertTrue(Files.isDirectory(dir.resolve(sub)), "应创建 " + sub);
        }
    }
}


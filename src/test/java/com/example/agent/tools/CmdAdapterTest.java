package com.example.agent.tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CmdAdapterTest {
    @Test
    void commandLineFormat() {
        assertEquals(List.of("cmd.exe", "/c", "dir"), new CmdAdapter().commandLine("dir"));
    }

    @Test
    void denylistIncludesFormat() {
        assertTrue(new CmdAdapter().isDenylisted("format C:"));
    }

    @Test
    void denylistIncludesRmdir() {
        assertTrue(new CmdAdapter().isDenylisted("rmdir /s /q C:\\foo"));
    }
}
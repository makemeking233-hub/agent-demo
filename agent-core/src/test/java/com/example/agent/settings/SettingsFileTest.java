package com.example.agent.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsFileTest {

    @Test
    void resolveDefaultHome_respectsCliFlag(@TempDir Path tmp) {
        Path home = SettingsFile.resolveDefaultHome(tmp.toString(), "/from/env");
        assertEquals(tmp, home);
    }

    @Test
    void resolveDefaultHome_respectsEnv(@TempDir Path tmp) {
        Path home = SettingsFile.resolveDefaultHome(null, tmp.toString());
        assertEquals(tmp, home);
    }

    @Test
    void resolveDefaultHome_fallsBackToUserHome() {
        Path home = SettingsFile.resolveDefaultHome("", "");
        assertTrue(home.toString().contains(".agent-demo"));
    }

    @Test
    void ensureFile_createsDirAndFile(@TempDir Path tmp) throws Exception {
        SettingsFile f = new SettingsFile(tmp);
        Path file = f.ensureFile();
        assertTrue(Files.exists(file));
        assertTrue(Files.isDirectory(tmp));
    }

    @Test
    void ensureFile_setsPosixPermissions(@TempDir Path tmp) throws Exception {
        SettingsFile f = new SettingsFile(tmp);
        Path file = f.ensureFile();
        try {
            Set<PosixFilePermission> dirPerms = Files.getPosixFilePermissions(tmp);
            Set<PosixFilePermission> filePerms = Files.getPosixFilePermissions(file);
            // 0700 / 0600
            assertEquals(7, dirPerms.size(), "dir should have 3 perms");
            assertEquals(6, filePerms.size(), "file should have 2 perms");
            assertTrue(dirPerms.contains(PosixFilePermission.OWNER_READ));
            assertTrue(dirPerms.contains(PosixFilePermission.OWNER_WRITE));
            assertTrue(dirPerms.contains(PosixFilePermission.OWNER_EXECUTE));
            assertTrue(filePerms.contains(PosixFilePermission.OWNER_READ));
            assertTrue(filePerms.contains(PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows: skip
        }
    }

    @Test
    void file_returnsCorrectPath(@TempDir Path tmp) {
        SettingsFile f = new SettingsFile(tmp);
        assertNotNull(f.file());
        assertEquals("settings.yaml", f.file().getFileName().toString());
    }
}

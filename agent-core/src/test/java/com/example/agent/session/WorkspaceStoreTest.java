package com.example.agent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

/** WorkspaceStore（add-workspaces-and-rename）：创建工作区 + 列表 + lookup + 校验。 */
class WorkspaceStoreTest {
    @TempDir Path tmp;

    private Path agentDataDir() {
        return tmp.resolve("agent-data");
    }

    @Test
    void defaultWorkspaceMapsToTopLevelSessions() {
        var ws = WorkspaceStore.defaultWorkspace(agentDataDir());
        assertEquals(WorkspaceStore.DEFAULT_WORKSPACE, ws.name());
        assertEquals(agentDataDir().resolve("sessions"), ws.sessionsDir());
    }

    @Test
    void createWritesMetaAndSessionsDir() throws Exception {
        Path workDir = tmp.resolve("project-md-main");
        Files.createDirectories(workDir);
        Path ad = agentDataDir();
        var r = WorkspaceStore.create(ad, "md-main", workDir.toString());

        assertTrue(r.ok());
        assertEquals("md-main", r.workspace().name());
        assertEquals(workDir, r.workspace().dir());
        assertEquals(ad.resolve("workspaces").resolve("md-main").resolve("sessions"), r.workspace().sessionsDir());
        assertTrue(Files.exists(ad.resolve("workspaces/md-main/meta.json")));
        assertTrue(Files.exists(ad.resolve("workspaces/md-main/sessions")));
    }

    @Test
    void listIncludesDefaultAndCreated() throws Exception {
        Path workDir = tmp.resolve("p");
        Files.createDirectories(workDir);
        Path ad = agentDataDir();
        WorkspaceStore.create(ad, "md-main", workDir.toString());

        var list = WorkspaceStore.list(ad);
        assertEquals(2, list.size());
        assertEquals(WorkspaceStore.DEFAULT_WORKSPACE, list.get(0).name());
        assertEquals("md-main", list.get(1).name());
    }

    @Test
    void getResolvesByName() throws Exception {
        Path workDir = tmp.resolve("p");
        Files.createDirectories(workDir);
        WorkspaceStore.create(agentDataDir(), "md-main", workDir.toString());

        var ws = WorkspaceStore.get(agentDataDir(), "md-main");
        assertNotNull(ws);
        assertEquals("md-main", ws.name());
        assertEquals(null, WorkspaceStore.get(agentDataDir(), "nope"));
    }

    @Test
    void sessionsDirForRoutesPerWorkspace() throws Exception {
        Path workDir = tmp.resolve("p");
        Files.createDirectories(workDir);
        Path ad = agentDataDir();
        WorkspaceStore.create(ad, "md-main", workDir.toString());

        assertEquals(ad.resolve("sessions"), WorkspaceStore.sessionsDirFor(ad, "agent-demo"));
        assertEquals(
                ad.resolve("workspaces/md-main/sessions"), WorkspaceStore.sessionsDirFor(ad, "md-main"));
        assertEquals(ad.resolve("sessions"), WorkspaceStore.sessionsDirFor(ad, "unknown"));
    }

    @Test
    void createRejectsNonExistentDir() {
        var r = WorkspaceStore.create(agentDataDir(), "ws", tmp.resolve("nope").toString());
        assertFalse(r.ok());
        assertEquals("dir_not_found", r.error());
    }

    @Test
    void createRejectsRelativeDir() {
        var r = WorkspaceStore.create(agentDataDir(), "ws", "relative/path");
        assertFalse(r.ok());
        assertEquals("dir_not_absolute", r.error());
    }

    @Test
    void createRejectsDuplicateName() throws Exception {
        Path workDir = tmp.resolve("p");
        Files.createDirectories(workDir);
        Path ad = agentDataDir();
        assertTrue(WorkspaceStore.create(ad, "md-main", workDir.toString()).ok());
        var r = WorkspaceStore.create(ad, "md-main", workDir.toString());
        assertFalse(r.ok());
        assertEquals("workspace_exists", r.error());
    }

    @Test
    void createRejectsInvalidName() throws Exception {
        Path workDir = tmp.resolve("p");
        Files.createDirectories(workDir);
        var r = WorkspaceStore.create(agentDataDir(), "bad/name", workDir.toString());
        assertFalse(r.ok());
        assertEquals("name_invalid", r.error());
        assertEquals("name_invalid", WorkspaceStore.create(agentDataDir(), "", workDir.toString()).error());
        assertEquals(
                "workspace_exists",
                WorkspaceStore.create(agentDataDir(), "agent-demo", workDir.toString()).error());
    }

    @Test
    void existsChecksByName() throws Exception {
        Path workDir = tmp.resolve("p");
        Files.createDirectories(workDir);
        WorkspaceStore.create(agentDataDir(), "md-main", workDir.toString());
        assertTrue(WorkspaceStore.exists(agentDataDir(), "agent-demo"));
        assertTrue(WorkspaceStore.exists(agentDataDir(), "md-main"));
        assertFalse(WorkspaceStore.exists(agentDataDir(), "nope"));
    }
}

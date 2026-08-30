package com.example.agent.worktree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class WorktreeManagerTest {
    @TempDir Path tmp;
    private Path repo;
    private Path baseDir;
    private WorktreeManager mgr;

    @BeforeEach
    void setUp() throws Exception {
        repo = tmp.resolve("repo");
        Files.createDirectories(repo);
        // 初始化 git 仓库 + 首个提交
        exec("git -C " + q(repo) + " init -b main");
        Files.writeString(repo.resolve("a.txt"), "hello");
        exec("git -C " + q(repo) + " add .");
        exec("git -C " + q(repo) + " -c user.email=t@t -c user.name=t commit -m init");
        baseDir = tmp.resolve("worktrees");
        mgr = new WorktreeManager(repo, baseDir);
    }

    private void exec(String cmd) throws Exception {
        Process p = new ProcessBuilder("cmd", "/c", cmd)
                .directory(repo.toFile()).redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        p.waitFor();
    }

    private static String q(Path p) {
        return "\"" + p.toString() + "\"";
    }

    @Test
    void createReturnsWorktreePath() {
        Path path = mgr.create("wt-test", "feature");
        assertNotNull(path, "应返回创建的 worktree 路径");
        assertTrue(Files.isDirectory(path));
        assertTrue(Files.exists(path.resolve("a.txt")), "worktree 应含仓库文件");
    }

    @Test
    void createWithoutBranchUsesDefault() {
        Path path = mgr.create("wt-default", null);
        assertNotNull(path);
        assertTrue(Files.isDirectory(path));
    }

    @Test
    void listShowsCreatedWorktree() {
        mgr.create("wt-list", "branch-l");
        var list = mgr.list();
        assertTrue(list.stream().anyMatch(i -> i.path().getFileName().toString().equals("wt-list")));
    }

    @Test
    void removeDeletesWorktree() {
        Path path = mgr.create("wt-rm", "branch-r");
        assertNotNull(path);
        assertTrue(mgr.remove("wt-rm"));
        assertFalse(Files.exists(path), "移除后目录应删除");
    }

    @Test
    void removeNonExistentReturnsFalse() {
        assertFalse(mgr.remove("no-such"));
    }

    @Test
    void createInNonGitRepoReturnsNull() throws Exception {
        Path nonGit = tmp.resolve("not-git");
        Files.createDirectories(nonGit);
        WorktreeManager bad = new WorktreeManager(nonGit, tmp.resolve("wt2"));
        assertNull(bad.create("x", null));
    }
}

package com.example.agent.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * WorkspaceStore v2（align-dsh-workspace）测试：
 * realpath 规范化 / id+title 与 path 解耦 / durable order / delete 保留 dir / missing-dir 容忍 / v1→v2 迁移。
 *
 * <p>v2 行为对齐 DSH Web workspace 模型（{@code packages/workspace/workspace/README.md}）。
 */
class WorkspaceStoreTest {
    @TempDir Path tmp;

    private Path agentDataDir() {
        return tmp.resolve("agent-data");
    }

    private Path makeDir(String name) throws Exception {
        Path p = tmp.resolve(name);
        Files.createDirectories(p);
        return p;
    }

    // ---------- 默认工作区 ----------

    @Test
    void defaultWorkspaceMapsToTopLevelSessions() {
        WorkspaceStore.Workspace ws = WorkspaceStore.defaultWorkspace(agentDataDir());
        assertThat(ws.name()).isEqualTo(WorkspaceStore.DEFAULT_WORKSPACE);
        assertThat(ws.sessionsDir()).isEqualTo(agentDataDir().resolve("sessions"));
        assertThat(ws.status()).isEqualTo(WorkspaceStore.Status.OK);
    }

    // ---------- create: v2 单参签名 + 自动派生 name/title ----------

    @Test
    void createDerivesNameAndTitleFromBasename() throws Exception {
        Path workDir = makeDir("project-md-main");
        var r = WorkspaceStore.create(agentDataDir(), workDir);

        assertTrue(r.ok());
        assertThat(r.workspace().name()).isEqualTo("project-md-main");
        assertThat(r.workspace().title()).isEqualTo("project-md-main");
        assertThat(r.workspace().path()).isEqualTo(workDir.toRealPath());
        assertThat(r.workspace().id()).isNotBlank();
        assertThat(r.workspace().createdAt()).isPositive();
    }

    @Test
    void createWritesV2MetaAndSessionsDir() throws Exception {
        Path workDir = makeDir("project-md-main");
        WorkspaceStore.create(agentDataDir(), workDir);

        Path wsRoot = agentDataDir().resolve("workspaces/project-md-main");
        assertTrue(Files.exists(wsRoot.resolve("meta.json")));
        assertTrue(Files.exists(wsRoot.resolve("sessions")));
        // meta.json v2 含 id + updatedAt + sessionIds
        String meta = Files.readString(wsRoot.resolve("meta.json"));
        assertThat(meta).contains("\"version\":2");
        assertThat(meta).contains("\"id\":");
        assertThat(meta).contains("\"session_ids\":[]");
    }

    @Test
    void listIncludesDefaultAndCreated() throws Exception {
        Path p = makeDir("p");
        WorkspaceStore.create(agentDataDir(), p);
        var list = WorkspaceStore.list(agentDataDir());
        assertThat(list).hasSize(2);
        assertThat(list.get(0).name()).isEqualTo(WorkspaceStore.DEFAULT_WORKSPACE);
        assertThat(list.get(1).name()).isEqualTo("p");
    }

    // ---------- realpath 规范化（DSH #1）----------

    @Test
    void sameCanonicalPathIsReusedAcrossSymlinkAndDotDot() throws Exception {
        Path real = makeDir("project-md-main");
        Path symlink = tmp.resolve("link");
        Files.createSymbolicLink(symlink, real);

        // 两个"不同路径字符串" → 同一 canonical
        WorkspaceStore.Workspace first = WorkspaceStore.create(agentDataDir(), real).workspace();
        WorkspaceStore.Workspace second = WorkspaceStore.create(agentDataDir(), symlink).workspace();
        // 复用同一 record：id 相同
        assertThat(second.id()).isEqualTo(first.id());
        // DSH 语义：复用时不改 title（保留首次的 basename-derived title）
        assertThat(second.title()).isEqualTo("project-md-main");
        assertThat(second.path()).isEqualTo(first.path());
    }

    @Test
    void differentPathsWithSameBasenameAreDistinct() throws Exception {
        Path a = makeDir("repo-md-main");
        Path b = makeDir("work-md-main");
        Path aSubdir = a.resolve("md-main");
        Path bSubdir = b.resolve("md-main");
        Files.createDirectories(aSubdir);
        Files.createDirectories(bSubdir);

        // 模拟不同项目根（basename 都是 md-main）
        var w1 = WorkspaceStore.create(agentDataDir(), aSubdir).workspace();
        var w2 = WorkspaceStore.create(agentDataDir(), bSubdir).workspace();
        assertThat(w1).isNotNull();
        assertThat(w2).isNotNull();
        assertThat(w2.id()).isNotEqualTo(w1.id());
        // title 都派生 basename
        assertThat(w1.title()).isEqualTo("md-main");
        assertThat(w2.title()).isEqualTo("md-main");
    }

    @Test
    void createRejectsNonExistentDir() {
        var r = WorkspaceStore.create(agentDataDir(), tmp.resolve("nope"));
        assertThat(r.ok()).isFalse();
        assertThat(r.error()).isEqualTo("dir_not_found");
    }

    @Test
    void createRejectsRelativePath() {
        var r = WorkspaceStore.create(agentDataDir(), Path.of("relative/path"));
        assertThat(r.ok()).isFalse();
        assertThat(r.error()).isEqualTo("dir_not_absolute");
    }

    // ---------- delete（DSH #2）：仅删 record，dir/sessions 不动 ----------

    @Test
    void deleteRemovesRecordButPreservesDirAndSessions() throws Exception {
        Path workDir = makeDir("project-md-main");
        // 模拟用户在 dir 里放一些文件 + 一个 sessions 子目录
        Path sessionFile = workDir.resolve("session.jsonl");
        Files.writeString(sessionFile, "{}");
        WorkspaceStore.create(agentDataDir(), workDir);
        String id = WorkspaceStore.get(agentDataDir(), "project-md-main").id();

        WorkspaceStore.delete(agentDataDir(), "project-md-main");

        // record 消失
        assertThat(WorkspaceStore.get(agentDataDir(), "project-md-main")).isNull();
        // dir 完整
        assertTrue(Files.isDirectory(workDir));
        assertTrue(Files.exists(sessionFile));
        // order 也从 order.json 中移除
        List<String> orderAfter = WorkspaceStore.durableOrder(agentDataDir());
        assertThat(orderAfter).doesNotContain("project-md-main");
        // id 不能再用
        assertThat(WorkspaceStore.findById(agentDataDir(), id)).isEmpty();
    }

    @Test
    void deleteUnknownReturnsFalse() {
        assertThat(WorkspaceStore.delete(agentDataDir(), "nope")).isFalse();
    }

    // ---------- rename（DSH #3）：title 与 path 解耦 ----------

    @Test
    void renameChangesTitleButNotPath() throws Exception {
        Path workDir = makeDir("project-md-main");
        WorkspaceStore.create(agentDataDir(), workDir);

        WorkspaceStore.rename(agentDataDir(), "project-md-main", "My Main Project");

        WorkspaceStore.Workspace ws = WorkspaceStore.get(agentDataDir(), "project-md-main");
        assertThat(ws.title()).isEqualTo("My Main Project");
        assertThat(ws.name()).isEqualTo("project-md-main");
        assertThat(ws.path()).isEqualTo(workDir.toRealPath());
        assertThat(ws.updatedAt()).isGreaterThanOrEqualTo(ws.createdAt());
    }

    @Test
    void renameUnknownReturnsFalse() {
        assertThat(WorkspaceStore.rename(agentDataDir(), "nope", "X")).isFalse();
    }

    @Test
    void renameRejectsBlankTitle() throws Exception {
        Path workDir = makeDir("project-md-main");
        WorkspaceStore.create(agentDataDir(), workDir);

        assertThat(WorkspaceStore.rename(agentDataDir(), "project-md-main", "")).isFalse();
        assertThat(WorkspaceStore.rename(agentDataDir(), "project-md-main", "   ")).isFalse();
        assertThat(WorkspaceStore.rename(agentDataDir(), "project-md-main", "  \n  ")).isFalse();
    }

    // ---------- insertBefore（DSH #4）：durable order 持久化 ----------

    @Test
    void insertBeforeReordersAndPersists() throws Exception {
        Path a = makeDir("a");
        Path b = makeDir("b");
        Path c = makeDir("c");
        WorkspaceStore.create(agentDataDir(), a);
        WorkspaceStore.create(agentDataDir(), b);
        WorkspaceStore.create(agentDataDir(), c);
        // 默认顺序：agent-demo, c, b, a（最新在前）
        List<WorkspaceStore.Workspace> before = WorkspaceStore.list(agentDataDir());
        assertThat(before).extracting(WorkspaceStore.Workspace::name)
                .containsExactly(WorkspaceStore.DEFAULT_WORKSPACE, "c", "b", "a");

        // 把 a 移到 c 前面（beforeName=c）
        WorkspaceStore.insertBefore(agentDataDir(), "a", "c");
        List<WorkspaceStore.Workspace> after = WorkspaceStore.list(agentDataDir());
        assertThat(after).extracting(WorkspaceStore.Workspace::name)
                .containsExactly(WorkspaceStore.DEFAULT_WORKSPACE, "a", "c", "b");

        // 重启后仍持久化（再 list 一次）
        List<WorkspaceStore.Workspace> afterReload = WorkspaceStore.list(agentDataDir());
        assertThat(afterReload).extracting(WorkspaceStore.Workspace::name)
                .containsExactly(WorkspaceStore.DEFAULT_WORKSPACE, "a", "c", "b");
    }

    @Test
    void insertBeforeWithNullAnchorAppendsToEnd() throws Exception {
        Path a = makeDir("a");
        Path b = makeDir("b");
        WorkspaceStore.create(agentDataDir(), a);
        WorkspaceStore.create(agentDataDir(), b);
        // 默认：agent-demo, b, a
        WorkspaceStore.insertBefore(agentDataDir(), "a", null);
        List<WorkspaceStore.Workspace> after = WorkspaceStore.list(agentDataDir());
        // a 移到末尾
        assertThat(after).extracting(WorkspaceStore.Workspace::name)
                .containsExactly(WorkspaceStore.DEFAULT_WORKSPACE, "b", "a");
    }

    @Test
    void insertBeforeUnknownReturnsFalse() throws Exception {
        Path a = makeDir("a");
        WorkspaceStore.create(agentDataDir(), a);
        assertThat(WorkspaceStore.insertBefore(agentDataDir(), "nope", null)).isFalse();
        assertThat(WorkspaceStore.insertBefore(agentDataDir(), "a", "nope")).isFalse();
    }

    // ---------- missing-dir 容忍（DSH #5）----------

    @Test
    void missingDirReturnsMissingDirStatus() throws Exception {
        Path workDir = makeDir("project-md-main");
        WorkspaceStore.create(agentDataDir(), workDir);
        // 把 workDir 删了/移走
        Files.delete(workDir);

        WorkspaceStore.Workspace ws = WorkspaceStore.get(agentDataDir(), "project-md-main");
        assertNotNull(ws);
        assertThat(ws.status()).isEqualTo(WorkspaceStore.Status.MISSING_DIR);
        // record 仍存在（list 仍返回）
        assertThat(WorkspaceStore.list(agentDataDir()))
                .anyMatch(w -> "project-md-main".equals(w.name()));
    }

    // ---------- v1 → v2 迁移（DSH migration）----------

    @Test
    void v1MetaIsMigratedToV2OnList() throws Exception {
        Path workDir = makeDir("project-md-main");
        Path wsRoot = agentDataDir().resolve("workspaces/project-md-main");
        // v1 schema 没创建 sessions/；但写 meta.json 前需要 wsRoot 存在
        Files.createDirectories(wsRoot);
        // 手写 v1 schema（缺 id/title/updatedAt/sessionIds）
        Files.writeString(
                wsRoot.resolve("meta.json"),
                "{\"name\":\"project-md-main\","
                        + "\"dir\":\"" + workDir.toRealPath().toString().replace("\\", "\\\\") + "\","
                        + "\"created_at\":1736700000000}");
        // 顺便把 dir 写进 order
        Files.createDirectories(agentDataDir().resolve("workspaces"));
        Files.writeString(
                agentDataDir().resolve("workspaces/order.json"),
                "{\"version\":1,\"order\":[\"project-md-main\"]}");

        WorkspaceStore.Workspace ws = WorkspaceStore.list(agentDataDir(), "project-md-main");
        assertNotNull(ws);
        // 迁移：缺字段补默认值
        assertThat(ws.title()).isEqualTo("project-md-main"); // basename
        assertThat(ws.id()).isNotBlank();
        assertThat(ws.sessionIds()).isEmpty();
        assertThat(ws.status()).isEqualTo(WorkspaceStore.Status.OK);
    }

    // ---------- lookup ----------

    @Test
    void getResolvesByName() throws Exception {
        Path workDir = makeDir("project-md-main");
        WorkspaceStore.create(agentDataDir(), workDir);
        assertNotNull(WorkspaceStore.get(agentDataDir(), "project-md-main"));
        assertThat(WorkspaceStore.get(agentDataDir(), "nope")).isNull();
    }

    @Test
    void findByIdResolvesAfterRename() throws Exception {
        Path workDir = makeDir("project-md-main");
        WorkspaceStore.create(agentDataDir(), workDir);
        WorkspaceStore.Workspace ws = WorkspaceStore.get(agentDataDir(), "project-md-main");
        WorkspaceStore.rename(agentDataDir(), "project-md-main", "New Title");

        // id 仍稳定
        assertThat(WorkspaceStore.findById(agentDataDir(), ws.id())).isPresent();
        assertThat(WorkspaceStore.findById(agentDataDir(), ws.id()).get().title())
                .isEqualTo("New Title");
    }
}
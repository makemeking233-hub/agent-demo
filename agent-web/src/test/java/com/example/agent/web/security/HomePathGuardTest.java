package com.example.agent.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * HomePathGuard 单测（add-workspace-picker-modal）。
 *
 * <p>用 {@code @TempDir} 构造"伪 home 目录"：把 {@link HomePathGuard} 指向该临时目录，再验证正常路径 /
 * {@code ..} 越界 / 符号链接越界 / 路径不存在 / 路径非绝对 五种行为。
 *
 * <p>符号链接用例在 Windows 上需要开发者模式或管理员权限创建符号链接；无权限时用 {@code assumeTrue} 跳过，
 * 不让单测卡住 CI。
 */
class HomePathGuardTest {

    @Test
    void resolveWithinHome_acceptsPathInsideHome(@TempDir Path home) throws IOException {
        // given: home 下有 projects 子目录
        Path projects = Files.createDirectory(home.resolve("projects"));
        Files.createDirectory(projects.resolve("agent-demo"));
        HomePathGuard guard = new HomePathGuard(home);

        // when: 解析 home 内路径
        HomePathGuard.ResolvedPath r =
                guard.resolveWithinHome(projects.resolve("agent-demo").toString(), true);

        // then: 路径被规范化并通过校验
        assertNotNull(r.realPath());
        assertTrue(r.realPath().startsWith(home.toRealPath()));
        assertTrue(r.existed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"relative/path", "./foo", "foo/bar", "C:foo"})
    void resolveWithinHome_rejectsNonAbsolutePath(String relPath, @TempDir Path home) {
        HomePathGuard guard = new HomePathGuard(home);

        HomePathException ex =
                assertThrows(
                        HomePathException.class,
                        () -> guard.resolveWithinHome(relPath, true));
        assertEquals("path_not_absolute", ex.code());
    }

    @Test
    void resolveWithinHome_rejectsNullOrBlank(@TempDir Path home) {
        HomePathGuard guard = new HomePathGuard(home);

        assertThrows(HomePathException.class, () -> guard.resolveWithinHome(null, true));
        assertThrows(HomePathException.class, () -> guard.resolveWithinHome("", true));
        assertThrows(HomePathException.class, () -> guard.resolveWithinHome("   ", true));
    }

    @Test
    void resolveWithinHome_rejectsParentEscape(@TempDir Path home) throws IOException {
        // given: home 之外有一个 secret 目录
        Path secret = Files.createDirectory(home.resolveSibling("secret-" + System.nanoTime()));
        HomePathGuard guard = new HomePathGuard(home);

        // when/then: 通过 .. 逃逸的路径被挡
        String escape = secret.toString();
        HomePathException ex =
                assertThrows(HomePathException.class, () -> guard.resolveWithinHome(escape, true));
        assertEquals("path_outside_home", ex.code());

        // cleanup
        Files.delete(secret);
    }

    @Test
    void resolveWithinHome_rejectsPathOutsideHome(@TempDir Path home) throws IOException {
        // given: 在 home 旁边创建一个外部目录
        Path outside = Files.createDirectory(home.resolveSibling("outside-" + System.nanoTime()));
        HomePathGuard guard = new HomePathGuard(home);

        // when/then
        HomePathException ex =
                assertThrows(
                        HomePathException.class,
                        () -> guard.resolveWithinHome(outside.resolve("sub").toString(), true));
        assertEquals("path_outside_home", ex.code());

        // cleanup
        Files.delete(outside);
    }

    @Test
    void resolveWithinHome_returnsNotFoundForMissingRequireExists(@TempDir Path home) {
        HomePathGuard guard = new HomePathGuard(home);

        HomePathException ex =
                assertThrows(
                        HomePathException.class,
                        () ->
                                guard.resolveWithinHome(
                                        home.resolve("does-not-exist").toString(), true));
        assertEquals("path_not_found", ex.code());
    }

    @Test
    void resolveWithinHome_mkdirModeAllowsMissingPathWhenParentInHome(@TempDir Path home)
            throws IOException {
        // given: home/projects 已存在（mkdir 时是父目录）
        Path projects = Files.createDirectory(home.resolve("projects"));
        HomePathGuard guard = new HomePathGuard(home);

        // when: 用 requireExists=false 校验一个不存在的子路径
        HomePathGuard.ResolvedPath r =
                guard.resolveWithinHome(projects.resolve("new-thing").toString(), false);

        // then: 返回 existed=false，且父目录在 home 内
        assertFalse(r.existed());
        assertNotNull(r.parentReal());
        assertTrue(r.parentReal().startsWith(home.toRealPath()));
    }

    @Test
    void resolveWithinHome_mkdirModeRejectsExistingPath(@TempDir Path home) throws IOException {
        // given: home/projects/existing 已存在
        Path projects = Files.createDirectory(home.resolve("projects"));
        Files.createDirectory(projects.resolve("existing"));
        HomePathGuard guard = new HomePathGuard(home);

        // when/then: mkdir 模式遇到已存在路径
        HomePathException ex =
                assertThrows(
                        HomePathException.class,
                        () ->
                                guard.resolveWithinHome(
                                        projects.resolve("existing").toString(), false));
        assertEquals("dir_exists", ex.code());
    }

    @Test
    void resolveWithinHome_mkdirModeRejectsParentOutsideHome(@TempDir Path home)
            throws IOException {
        // given: home 之外的目录作为 parent
        Path outside = Files.createDirectory(home.resolveSibling("outside-" + System.nanoTime()));
        HomePathGuard guard = new HomePathGuard(home);

        // when/then: 要创建的子路径 parent 在 home 外
        HomePathException ex =
                assertThrows(
                        HomePathException.class,
                        () ->
                                guard.resolveWithinHome(
                                        outside.resolve("new-thing").toString(), false));
        assertEquals("path_outside_home", ex.code());

        // cleanup
        Files.delete(outside);
    }

    @Test
    void resolveWithinHome_rejectsSymlinkEscape(@TempDir Path home) throws IOException {
        // given: home 内有一个符号链接，指向 home 外
        Path outside = Files.createDirectory(home.resolveSibling("outside-" + System.nanoTime()));
        Path link;
        try {
            link = Files.createSymbolicLink(home.resolve("escape-link"), outside);
        } catch (UnsupportedOperationException | IOException e) {
            // Windows 非开发者模式 / 无权限 → 跳过
            assumeTrue(false, "Symlink not supported on this OS/permission: " + e.getMessage());
            return;
        }
        HomePathGuard guard = new HomePathGuard(home);

        // when/then: toRealPath 解链接后判断越界
        HomePathException ex =
                assertThrows(HomePathException.class, () -> guard.resolveWithinHome(link.toString(), true));
        assertEquals("path_outside_home", ex.code());

        // cleanup
        Files.delete(link);
        Files.delete(outside);
    }
}

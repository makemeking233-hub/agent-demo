package com.example.agent.permission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 单一可写根派生函数（rewrite-permission-mode-dsh T1.4 引入）。
 *
 * <p>对齐 dsh web 的 {@code sandbox/src/roots.ts} 中 {@code writableRoots(policy)} 的 one-home 语义：
 * fs / bash / terminal 三个 capability 都从本类派生写入根，永不 drift。
 *
 * <p>派生规则（按 spec §"writableRoots 单一派生函数"）：
 *
 * <ul>
 *   <li>PLAN / ASK → 返回空列表（不写）
 *   <li>DANGER_FULL → 返回空列表（调用方据此判断不 fence）
 *   <li>DONT_ASK → 返回 [workspaceRoot.toRealPath, /tmp.toRealPath, tmpdir.toRealPath] 去重
 * </ul>
 *
 * <p>{@code canonicalize(Path)} 私有方法把 {@code Path.toRealPath()} 的 IOException 捕获并回退原拼写
 * （路径不存在时合理回退；与 dsh 的 fallback-to-spelling 行为对齐）。
 */
public final class WritableRoots {

    private WritableRoots() {
    }

    /**
     * 按 policy 派生可写根列表（已 canonicalize 去重）。
     *
     * @param policy 当前 sandbox policy
     * @return 可写根列表（不可变）；空列表表示不 fence 或不允许写入
     */
    public static List<Path> writableRoots(SandboxPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("SandboxPolicy 不可空");
        }
        return switch (policy.mode()) {
            case PLAN, ASK, DANGER_FULL -> List.of();
            case DONT_ASK -> dontAskRoots(policy);
        };
    }

    private static List<Path> dontAskRoots(SandboxPolicy policy) {
        Set<Path> roots = new LinkedHashSet<>();
        roots.add(canonicalize(policy.workspaceRoot()));
        // /tmp 与 tmpdir 在 Linux/macOS 是同一目录，在 Windows 是不同盘
        roots.add(canonicalize(Path.of("/tmp")));
        roots.add(canonicalize(Path.of(System.getProperty("java.io.tmpdir"))));
        return List.copyOf(roots);
    }

    /**
     * 解析路径为 canonical（symlink resolved）形式。失败时回退原拼写。
     *
     * <p>行为对齐 dsh 的 {@code realpathSync.native}：Node 实现的 native realpath
     * 逐段跟随文件系统，与 chdir/spawn 一致；JDK 的 {@code Path.toRealPath()} 默认
     * {@code FOLLOW_LINKS}，行为相同。
     *
     * @param p 待解析路径
     * @return canonical 路径；解析失败回退原拼写
     */
    static Path canonicalize(Path p) {
        if (p == null) return null;
        try {
            return p.toRealPath();
        } catch (IOException e) {
            return p;
        }
    }

    /**
     * 仅供测试：检查 {@code path} 是否落在 {@code writableRoots} 任何一个根之下（canonical 后比较）。
     *
     * @param writableRoots 派生根列表
     * @param path          待检查路径
     * @return true 表示可写
     */
    public static boolean contains(List<Path> writableRoots, Path path) {
        if (path == null || writableRoots == null || writableRoots.isEmpty()) return false;
        Path real = canonicalize(path);
        // 路径不存在时 (新文件), canonicalize 回退原拼写, 仍然 startsWith 判断
        for (Path root : writableRoots) {
            if (real.startsWith(root)) return true;
        }
        return false;
    }

    /**
     * 测试用：避免与 {@link Files#exists} 等 API 冲突的辅助 marker。
     */
    static boolean isExisting(Path p) {
        return p != null && Files.exists(p);
    }
}

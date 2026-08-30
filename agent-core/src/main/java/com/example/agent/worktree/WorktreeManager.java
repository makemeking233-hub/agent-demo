package com.example.agent.worktree;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Worktree 管理器（add-worktree-mode change，git CLI）。
 *
 * <p>用系统 {@code git} CLI 封装 {@code git worktree add/list/remove}。每个操作在仓库根
 * ({@code repoDir}) 执行，输出在 {@code baseDir} 下创建隔离工作区；失败记录 WARN 并返回空/标记，
 * 不抛未捕获异常（主流程继续）。
 */
public class WorktreeManager {

    private static final Logger log = LoggerFactory.getLogger(WorktreeManager.class);

    private final Path repoDir;
    private final Path baseDir;

    /**
     * 构造管理器。
     *
     * @param repoDir 仓库根目录（执行 git 的 cwd）
     * @param baseDir worktree 放置目录（如 {@code ~/.agent-demo/worktrees}）
     */
    public WorktreeManager(Path repoDir, Path baseDir) {
        this.repoDir = repoDir;
        this.baseDir = baseDir;
    }

    /**
     * 创建 worktree。
     *
     * @param name   worktree 目录名（{@code baseDir/name}）
     * @param branch 新分支名（{@code null} 用默认分支；非 null 时 {@code -b <branch>}）
     * @return 创建的 worktree 路径；创建失败（非 git 仓库/命令失败）返回 {@code null}
     */
    public Path create(String name, String branch) {
        try {
            Files.createDirectories(baseDir);
            List<String> args = new ArrayList<>(List.of("worktree", "add", baseDir.resolve(name).toString()));
            if (branch != null && !branch.isBlank()) args.addAll(List.of("-b", branch));
            CommandResult r = runGit(args);
            if (!r.success()) {
                log.warn("[worktree] create {} 失败: {}", name, r.output().strip());
                return null;
            }
            return baseDir.resolve(name);
        } catch (Exception e) {
            log.warn("[worktree] create {} 异常: {}", name, e.toString());
            return null;
        }
    }

    /**
     * 列出 {@code baseDir} 下的所有 worktree。
     *
     * @return worktree 信息列表（path + branch）；非 git 仓库/失败返回空列表
     */
    public List<WorktreeInfo> list() {
        List<WorktreeInfo> result = new ArrayList<>();
        try {
            CommandResult r = runGit(List.of("worktree", "list", "--porcelain"));
            if (!r.success()) {
                log.warn("[worktree] list 失败: {}", r.output().strip());
                return result;
            }
            String[] lines = r.output().split("\n");
            String path = null;
            for (String line : lines) {
                if (line.startsWith("worktree ")) {
                    path = line.substring("worktree ".length()).trim();
                } else if (line.startsWith("branch ")) {
                    String branch = line.substring("branch ".length()).trim();
                    if (path != null) {
                        result.add(new WorktreeInfo(Path.of(path), branch));
                        path = null;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[worktree] list 异常: {}", e.toString());
        }
        return result;
    }

    /**
     * 移除 worktree（带 {@code --force}，清理工作区）。
     *
     * @param name worktree 目录名（{@code baseDir/name}）
     * @return 成功返回 {@code true}；失败返回 {@code false}
     */
    public boolean remove(String name) {
        try {
            Path target = baseDir.resolve(name);
            CommandResult r = runGit(List.of("worktree", "remove", "--force", target.toString()));
            if (!r.success()) {
                log.warn("[worktree] remove {} 失败: {}", name, r.output().strip());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("[worktree] remove {} 异常: {}", name, e.toString());
            return false;
        }
    }

    /** 执行 git 命令（在 repoDir 作为 cwd）。 */
    private CommandResult runGit(List<String> args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(repoDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = readAll(p.getInputStream());
        int code = p.waitFor();
        return new CommandResult(code == 0, out);
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) != -1) buf.write(chunk, 0, n);
        return buf.toString(StandardCharsets.UTF_8);
    }

    /** git 命令结果。 */
    record CommandResult(boolean success, String output) {}

    /** worktree 信息。 */
    public record WorktreeInfo(Path path, String branch) {}
}

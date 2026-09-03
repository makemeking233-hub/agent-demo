package com.example.agent.web.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 家目录路径安全校验（add-workspace-picker-modal，D2）。
 *
 * <p>所有 {@code /api/fs/**} 端点都必须在执行任何文件系统 IO 前调用 {@link #resolveWithinHome}，确保
 * 传入路径在 {@code toRealPath()} 之后落在 homeDir 子树内。这样能挡掉：
 *
 * <ul>
 *   <li>相对路径 / {@code ../} 逃逸；
 *   <li>家目录内符号链接指向家目录外；
 *   <li>大小写变体（依赖 OS 文件系统语义）。
 * </ul>
 *
 * <p>错误统一以 {@link HomePathException} 抛出，code 为稳定的 {@code snake_case} 字符串，前端按 code
 * 渲染对应中文错误条。
 */
public final class HomePathGuard {

    /** 已 {@code toRealPath()} 解析的家目录根。 */
    private final Path homeRealPath;

    /** 构造：传入 home 目录（可为不存在的路径，构造时再做一次解析尝试）。 */
    public HomePathGuard(Path homeDir) {
        if (homeDir == null) {
            throw new IllegalArgumentException("homeDir must not be null");
        }
        Path resolved;
        try {
            resolved = homeDir.toRealPath();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot resolve home directory: " + homeDir, e);
        }
        this.homeRealPath = resolved;
    }

    /** 默认构造：用 {@code System.getProperty("user.home")}。 */
    public static HomePathGuard systemDefault() {
        return new HomePathGuard(Path.of(System.getProperty("user.home")));
    }

    /**
     * 解析用户输入路径并校验落在 homeDir 子树内。
     *
     * @param input         用户输入的路径字符串
     * @param requireExists {@code true} 用于 list（路径必须存在）；{@code false} 用于 mkdir（路径必须不存在，
     *                      仅校验父目录）
     * @return 解析结果（含 requested / realPath / parentReal / existed）
     * @throws HomePathException 路径非法或不在 homeDir 子树内
     */
    public ResolvedPath resolveWithinHome(String input, boolean requireExists) {
        if (input == null || input.isBlank()) {
            throw new HomePathException("path_not_absolute", "Path must not be empty");
        }
        Path raw;
        try {
            raw = Path.of(input);
        } catch (Exception e) {
            throw new HomePathException("path_not_absolute", "Invalid path: " + input);
        }
        // 必须先 isAbsolute() 检查 —— 否则 toAbsolutePath() 会用 user.dir 兜底，相对路径永远变成"绝对"
        if (!raw.isAbsolute()) {
            throw new HomePathException("path_not_absolute", "Path is not absolute: " + input);
        }
        Path requested = raw.toAbsolutePath().normalize();

        // 先做"名义前缀校验"（处理"路径在 home 外 + 不存在"的情况）。
        // 必须先于 toRealPath —— 否则路径不存在时会先被 catch 成 path_not_found，
        // 遮蔽了"路径在 home 外"这一更准确的诊断。
        if (!isUnderHome(requested)) {
            throw new HomePathException(
                    "path_outside_home", "Path is outside home: " + requested);
        }

        Path real;
        try {
            real = requested.toRealPath();
        } catch (NoSuchFileException e) {
            // 路径不存在
            if (requireExists) {
                throw new HomePathException("path_not_found", "Path not found: " + input);
            }
            // mkdir 模式：检查父目录
            return resolveMissingForMkdir(requested);
        } catch (IOException e) {
            throw new HomePathException(
                    "path_unresolvable",
                    "Cannot resolve path: " + input + " (" + e.getMessage() + ")");
        }

        // toRealPath 后再次前缀校验（处理符号链接越界）。
        if (!isUnderHome(real)) {
            throw new HomePathException("path_outside_home", "Path is outside home: " + real);
        }
        if (!requireExists) {
            throw new HomePathException("dir_exists", "Path already exists: " + real);
        }
        return new ResolvedPath(requested, real, real.getParent(), true);
    }

    /** mkdir 模式：路径不存在时，沿 parent 链向上找第一个 existing 祖先并校验它在 home 内。 */
    private ResolvedPath resolveMissingForMkdir(Path requested) {
        Path cur = requested;
        Path existingAncestor = null;
        while (cur != null) {
            if (Files.exists(cur)) {
                existingAncestor = cur;
                break;
            }
            cur = cur.getParent();
        }
        if (existingAncestor == null) {
            throw new HomePathException(
                    "path_not_found",
                    "No existing ancestor for: " + requested);
        }
        Path real;
        try {
            real = existingAncestor.toRealPath();
        } catch (IOException e) {
            throw new HomePathException(
                    "path_unresolvable",
                    "Cannot resolve ancestor: " + existingAncestor + " (" + e.getMessage() + ")");
        }
        if (!isUnderHome(real)) {
            throw new HomePathException(
                    "path_outside_home", "Ancestor outside home: " + real);
        }
        return new ResolvedPath(requested, null, real, false);
    }

    /** {@code toRealPath()} 后判前缀（注意 Windows 大小写不敏感，所以用小写比较）。 */
    private boolean isUnderHome(Path real) {
        if (real == null || homeRealPath == null) return false;
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return real.toString().toLowerCase(Locale.ROOT)
                    .startsWith(homeRealPath.toString().toLowerCase(Locale.ROOT));
        }
        return real.startsWith(homeRealPath);
    }

    public Path homeRealPath() {
        return homeRealPath;
    }

    /**
     * 解析结果。
     *
     * @param requested  规范化后的输入路径
     * @param realPath   {@code toRealPath()} 结果（不存在时为 null，仅 mkdir 模式）
     * @param parentReal 父目录的 {@code toRealPath()}（用于 mkdir 模式校验父目录是否在 home 内）
     * @param existed    路径是否存在（list=true 时恒 true；mkdir 模式 false 表示不存在）
     */
    public record ResolvedPath(Path requested, Path realPath, Path parentReal, boolean existed) {}
}

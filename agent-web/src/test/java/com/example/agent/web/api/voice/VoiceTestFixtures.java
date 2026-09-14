package com.example.agent.web.api.voice;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * voice 集成测试共享工具（improve-voice-accuracy T9.3）。
 *
 * <p>约定：
 * <ul>
 *   <li>集成测试的临时文件写到 {@code target/test-voice-tmp/}（在仓库根 + 在 {@code .gitignore}）
 *   <li>{@link #cleanupTargetTmp()} 在 {@code @AfterEach} 调用，清掉自己产生的子目录
 *   <li>单测（不写磁盘）不强制使用本工具
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * class VoiceIntegrationTest {
 *     @AfterEach
 *     void cleanup() throws IOException {
 *         VoiceTestFixtures.cleanupTargetTmp();
 *     }
 * }
 * }</pre>
 */
public final class VoiceTestFixtures {

    private VoiceTestFixtures() {}

    /**
     * 集成测试临时根目录（相对当前工作目录）。
     *
     * <p>返回 {@code target/test-voice-tmp/} —— 与 Maven 构建产物同根，{@code mvn clean} 会一并清掉。
     */
    public static Path targetTmpRoot() {
        return Paths.get("target", "test-voice-tmp");
    }

    /**
     * 删除 {@link #targetTmpRoot()} 下的所有内容（保留目录本身）。
     *
     * <p>仅删除该根下的文件，不会误删仓库其它位置的数据。
     *
     * @throws IOException 清理失败（罕见；通常意味着权限问题或文件被占用）
     */
    public static void cleanupTargetTmp() throws IOException {
        Path root = targetTmpRoot();
        if (!Files.exists(root)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    deleteRecursively(entry);
                } else {
                    Files.delete(entry);
                }
            }
        }
    }

    /**
     * 复制 fixture JSON 到临时目录，给集成测试的 mock web server 用。
     *
     * @param fixtureClasspathPath classpath 下的 fixture 路径（如 {@code "fixtures/voice/deepseek-correction-success.json"}）
     * @return 复制后的目标路径
     * @throws IOException 复制失败
     */
    public static Path copyFixture(String fixtureClasspathPath) throws IOException {
        Path root = targetTmpRoot();
        Files.createDirectories(root);
        Path target = root.resolve(Paths.get(fixtureClasspathPath).getFileName());
        try (var in = VoiceTestFixtures.class.getClassLoader()
                .getResourceAsStream(fixtureClasspathPath)) {
            if (in == null) {
                throw new IOException("fixture not found on classpath: " + fixtureClasspathPath);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    deleteRecursively(entry);
                } else {
                    Files.delete(entry);
                }
            }
        }
        Files.delete(dir);
    }
}
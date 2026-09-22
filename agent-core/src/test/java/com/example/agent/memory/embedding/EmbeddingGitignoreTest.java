package com.example.agent.memory.embedding;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * .gitignore 规则测试（add-embedding-rag T7）。
 *
 * <p>验证 spec {@code Requirement: 模型与索引文件 git 忽略}：embedding 模型文件（~95MB）与
 * 向量索引目录必须被 git 忽略，避免误提交大文件与本地状态。
 *
 * <p>用「定位仓库根 + 读 .gitignore 文本」的方式断言。测试工作目录由 surefire 设为模块目录
 * （{@code agent-core}），故仓库根是 {@code user.dir/..}。
 */
class EmbeddingGitignoreTest {

    /** 定位仓库根（含 .gitignore 的目录）。 */
    private static Path repoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 4 && dir != null; i++) {
            if (Files.exists(dir.resolve(".gitignore"))) return dir;
            dir = dir.getParent();
        }
        throw new IllegalStateException("cannot locate repo root from " + System.getProperty("user.dir"));
    }

    private static List<String> gitignoreLines() throws IOException {
        return Files.readAllLines(repoRoot().resolve(".gitignore"));
    }

    @Test
    void modelsDirectoryIsIgnored() throws IOException {
        List<String> lines = gitignoreLines();
        boolean ignored = lines.stream()
                .map(String::trim)
                .anyMatch(l -> l.equals("**/models/") || l.equals("/models/") || l.equals("models/"));
        assertTrue(ignored, ".gitignore 应忽略 models/ 目录（embedding 模型文件），实际规则: " + lines);
    }

    @Test
    void vectorsDirectoryIsIgnored() throws IOException {
        List<String> lines = gitignoreLines();
        boolean ignored = lines.stream()
                .map(String::trim)
                .anyMatch(l -> l.equals("**/.vectors/") || l.equals(".vectors/"));
        assertTrue(ignored, ".gitignore 应忽略 .vectors/ 目录（向量索引 + mtime 缓存），实际规则: " + lines);
    }
}

package com.example.agent.web.api.voice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * T9.3 演示测试：用 {@link VoiceTestFixtures#targetTmpRoot()} 写文件并在 tearDown 时清理，
 * 保证测试不污染用户真实数据（AGENTS.md §10）。
 */
class VoiceTestFixturesTest {

    @AfterEach
    void cleanup() throws IOException {
        VoiceTestFixtures.cleanupTargetTmp();
    }

    @Test
    void cleanupRemovesTempSubdirectories() throws IOException {
        Path root = VoiceTestFixtures.targetTmpRoot();
        Path sub = root.resolve("session-123");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve("recording.wav"), "fake wav bytes");

        assertThat(Files.exists(sub)).isTrue();

        VoiceTestFixtures.cleanupTargetTmp();

        assertThat(Files.exists(sub)).isFalse();
        // 根目录保留（让下一个测试继续复用）
        assertThat(Files.exists(root)).isTrue();
    }

    @Test
    void cleanupOnMissingRootIsNoOp() throws IOException {
        // root 不存在时不能抛
        VoiceTestFixtures.cleanupTargetTmp();
    }

    @Test
    void copyFixtureResolvesFromClasspath() throws IOException {
        Path target = VoiceTestFixtures.copyFixture("fixtures/voice/README.md");
        assertThat(target).exists();
        String content = Files.readString(target);
        assertThat(content).contains("voice fixtures");
    }
}
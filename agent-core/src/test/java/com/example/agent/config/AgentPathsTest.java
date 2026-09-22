package com.example.agent.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * {@link AgentPaths} 的优先级与派生路径（fix-agent-home-isolation T1）。
 *
 * <p>三种来源的优先级是本 change 的核心契约：唯一的测试隔离开关是**系统属性**
 * （{@code @SpringBootTest} 设不了环境变量），所以属性必须排在环境变量之前，
 * 否则测试隔离会被开发机上的 {@code AGENT_DEMO_HOME} 反盖。
 */
class AgentPathsTest {

    private static final String USER_HOME = "/home/tester";

    @Test
    void systemPropertyWinsOverEnvAndUserHome() {
        assertThat(AgentPaths.agentHome("/tmp/from-prop", "/tmp/from-env", USER_HOME))
                .isEqualTo(Path.of("/tmp/from-prop", ".agent-demo"));
    }

    @Test
    void envUsedWhenPropertyAbsent() {
        assertThat(AgentPaths.agentHome(null, "/tmp/from-env", USER_HOME))
                .isEqualTo(Path.of("/tmp/from-env", ".agent-demo"));
    }

    @Test
    void userHomeUsedWhenBothAbsent() {
        assertThat(AgentPaths.agentHome(null, null, USER_HOME))
                .isEqualTo(Path.of(USER_HOME, ".agent-demo"));
    }

    @Test
    void blankPropertyFallsThroughToEnv() {
        // 空白串视为未设置——与 WebAgentRuntimeDataDirTest 既有断言同语义
        assertThat(AgentPaths.agentHome("   ", "/tmp/from-env", USER_HOME))
                .isEqualTo(Path.of("/tmp/from-env", ".agent-demo"));
    }

    @Test
    void blankEnvFallsThroughToUserHome() {
        assertThat(AgentPaths.agentHome(" ", "  ", USER_HOME))
                .isEqualTo(Path.of(USER_HOME, ".agent-demo"));
    }

    @Test
    void derivedDirsHangOffTheSameHome() {
        Path home = AgentPaths.agentHome();
        assertThat(AgentPaths.logsDir()).isEqualTo(home.resolve("logs").toString());
        assertThat(AgentPaths.sessionsDir()).isEqualTo(home.resolve("sessions").toString());
        assertThat(AgentPaths.worktreesDir()).isEqualTo(home.resolve("worktrees").toString());
    }

    @Test
    void homePropertyNameMatchesTheDocumentedSwitch() {
        // 改名会让所有既有测试的隔离静默失效，故锁死字面量
        assertThat(AgentPaths.HOME_PROPERTY).isEqualTo("agent.demo.home");
        assertThat(AgentPaths.HOME_ENV).isEqualTo("AGENT_DEMO_HOME");
    }

    @Test
    void agentConfigLogsDefaultFollowsTheOverride() {
        // 改造前 AgentConfig 把日志根写死成 user.home，是「日志根盖不住」的直接原因之一。
        // 必须显式设属性再断言：不设属性时新旧实现结果相同，该断言会假绿。
        String original = System.getProperty(AgentPaths.HOME_PROPERTY);
        try {
            System.setProperty(AgentPaths.HOME_PROPERTY, "target/prop-home");
            assertThat(AgentConfig.defaults().logging().dir())
                    .isEqualTo(AgentPaths.logsDir())
                    // 用 contains 而非 startsWith("target/prop-home")：Path.toString() 在
                    // Windows 上用反斜杠，写死分隔符会让断言假失败
                    .contains("prop-home");
        } finally {
            if (original == null) {
                System.clearProperty(AgentPaths.HOME_PROPERTY);
            } else {
                System.setProperty(AgentPaths.HOME_PROPERTY, original);
            }
        }
    }

    @Test
    void agentConfigWorktreeDefaultFollowsTheOverride() {
        String original = System.getProperty(AgentPaths.HOME_PROPERTY);
        try {
            System.setProperty(AgentPaths.HOME_PROPERTY, "target/prop-home");
            assertThat(AgentConfig.defaults().worktree().baseDir())
                    .isEqualTo(AgentPaths.worktreesDir())
                    .contains("prop-home");
        } finally {
            if (original == null) {
                System.clearProperty(AgentPaths.HOME_PROPERTY);
            } else {
                System.setProperty(AgentPaths.HOME_PROPERTY, original);
            }
        }
    }
}

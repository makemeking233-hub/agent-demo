package com.example.agent.web.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 数据目录解析（全局规则 §10：测试不得污染真实数据）。
 *
 * <p>系统属性优先于环境变量，是为了让 `@SpringBootTest` 能隔离数据目录——它无法设置环境变量，
 * 而集成测试会真实写入会话存档。
 */
class WebAgentRuntimeDataDirTest {

    private final String original = System.getProperty(WebAgentRuntime.AGENT_DEMO_HOME_PROPERTY);

    @AfterEach
    void restore() {
        if (original == null) {
            System.clearProperty(WebAgentRuntime.AGENT_DEMO_HOME_PROPERTY);
        } else {
            System.setProperty(WebAgentRuntime.AGENT_DEMO_HOME_PROPERTY, original);
        }
    }

    @Test
    void systemPropertyWinsAndAppendsAgentDemo() {
        System.setProperty(WebAgentRuntime.AGENT_DEMO_HOME_PROPERTY, "target/test-data");

        assertThat(WebAgentRuntime.resolveDataDir())
                .isEqualTo(Path.of("target", "test-data", ".agent-demo"));
    }

    @Test
    void blankPropertyFallsBackToUserHome() {
        System.setProperty(WebAgentRuntime.AGENT_DEMO_HOME_PROPERTY, "   ");

        // 无覆盖时退回 <user.home>/.agent-demo（与环境变量分支同语义：其下拼 .agent-demo）
        assertThat(WebAgentRuntime.resolveDataDir())
                .endsWith(Path.of(".agent-demo"))
                .isEqualTo(
                        Path.of(System.getProperty("user.home"), ".agent-demo"));
    }

    @Test
    void missingPropertyFallsBackToUserHome() {
        System.clearProperty(WebAgentRuntime.AGENT_DEMO_HOME_PROPERTY);

        assertThat(WebAgentRuntime.resolveDataDir())
                .isEqualTo(Path.of(System.getProperty("user.home"), ".agent-demo"));
    }
}

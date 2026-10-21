package com.example.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * agent.skill.* 配置解析测试（add-skill-demand-loading T1.5）。
 *
 * <p>覆盖：六键解析、缺省默认值、非法 mode 回退 search、非法数值回退默认。
 */
class AgentConfigSkillTest {
    @TempDir Path tmp;

    private AgentConfig loadWith(String yaml) throws Exception {
        Path cfg = tmp.resolve("config.yaml");
        Files.writeString(cfg, yaml);
        return new ConfigLoader().load(cfg);
    }

    @Test
    void defaultsWhenNoSkillSection() {
        AgentConfig.Skill s = AgentConfig.defaults().skill();
        assertNotNull(s);
        assertEquals(AgentConfig.SkillMode.SEARCH, s.mode(), "缺省档位应为 search");
        assertEquals(10, s.hybridDirectLimit());
        assertEquals(6, s.hybridProjectQuota());
        assertEquals(20, s.recallK());
        assertEquals(0.05, s.decayLambda(), 1e-9);
        assertEquals(40, s.descriptionTruncateChars());
    }

    @Test
    void parsesAllSixKeys() throws Exception {
        AgentConfig cfg =
                loadWith(
                        "agent:\n"
                                + "  skill:\n"
                                + "    mode: hybrid\n"
                                + "    hybrid-direct-limit: 8\n"
                                + "    hybrid-project-quota: 4\n"
                                + "    recall-k: 15\n"
                                + "    decay-lambda: 0.02\n"
                                + "    description-truncate-chars: 30\n");
        AgentConfig.Skill s = cfg.skill();
        assertEquals(AgentConfig.SkillMode.HYBRID, s.mode());
        assertEquals(8, s.hybridDirectLimit());
        assertEquals(4, s.hybridProjectQuota());
        assertEquals(15, s.recallK());
        assertEquals(0.02, s.decayLambda(), 1e-9);
        assertEquals(30, s.descriptionTruncateChars());
    }

    @Test
    void parsesAllMode() throws Exception {
        AgentConfig cfg = loadWith("agent:\n  skill:\n    mode: all\n");
        assertEquals(AgentConfig.SkillMode.ALL, cfg.skill().mode());
    }

    @Test
    void parsesSearchMode() throws Exception {
        AgentConfig cfg = loadWith("agent:\n  skill:\n    mode: search\n");
        assertEquals(AgentConfig.SkillMode.SEARCH, cfg.skill().mode());
    }

    @Test
    void illegalModeFallsBackToSearch() throws Exception {
        AgentConfig cfg = loadWith("agent:\n  skill:\n    mode: bogus\n");
        assertEquals(AgentConfig.SkillMode.SEARCH, cfg.skill().mode(), "非法 mode 应回退 search");
    }

    @Test
    void illegalNumericFallsBackToDefaults() throws Exception {
        AgentConfig cfg =
                loadWith(
                        "agent:\n"
                                + "  skill:\n"
                                + "    recall-k: 0\n"
                                + "    hybrid-direct-limit: -5\n"
                                + "    decay-lambda: -1\n");
        AgentConfig.Skill s = cfg.skill();
        assertEquals(20, s.recallK(), "recall-k <= 0 应回退 20");
        assertEquals(10, s.hybridDirectLimit(), "hybrid-direct-limit <= 0 应回退 10");
        assertEquals(0.05, s.decayLambda(), 1e-9, "decay-lambda <= 0 应回退 0.05");
    }

    @Test
    void partialSectionKeepsOtherDefaults() throws Exception {
        AgentConfig cfg = loadWith("agent:\n  skill:\n    mode: hybrid\n");
        AgentConfig.Skill s = cfg.skill();
        assertEquals(AgentConfig.SkillMode.HYBRID, s.mode());
        assertEquals(10, s.hybridDirectLimit(), "未指定的数值保持默认");
        assertEquals(20, s.recallK());
    }

    @Test
    void skillModeEnumParsesCaseInsensitive() {
        assertEquals(AgentConfig.SkillMode.SEARCH, AgentConfig.SkillMode.from("SEARCH"));
        assertEquals(AgentConfig.SkillMode.HYBRID, AgentConfig.SkillMode.from("Hybrid"));
        assertEquals(AgentConfig.SkillMode.ALL, AgentConfig.SkillMode.from("all"));
        assertEquals(AgentConfig.SkillMode.SEARCH, AgentConfig.SkillMode.from("unknown-value"));
        assertEquals(AgentConfig.SkillMode.SEARCH, AgentConfig.SkillMode.from(null));
    }

    @Test
    void projectQuotaClampedToDirectLimit() {
        // quota 不应超过 direct limit
        var s = new AgentConfig.Skill(AgentConfig.SkillMode.HYBRID, 5, 20, 20, 0.05, 40);
        assertEquals(5, s.hybridProjectQuota(), "project quota 应被 clamp 到 direct limit");
    }
}
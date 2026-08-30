package com.example.agent.web.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agent.config.AgentConfig;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.log.SessionLogSink;
import com.example.agent.tools.ToolRegistry;
import org.junit.jupiter.api.Test;

/** Bump web.stream coverage: WebAgentRuntime 装配 & createLoop / tools / agentDataDir. */
class WebAgentRuntimeTest {

    private LlmProvider mockProvider() {
        LlmProvider p = mock(LlmProvider.class);
        when(p.name()).thenReturn("deepseek");
        when(p.contextWindow()).thenReturn(100_000);
        when(p.maxOutputTokens()).thenReturn(8192);
        return p;
    }

    @Test
    void createLoopReturnsAgentLoop() {
        WebAgentRuntime rt = new WebAgentRuntime(mockProvider(), new ToolRegistry(), new TokenEstimator());
        var loop = rt.createLoop("s1", "s1", SessionLogSink.NOOP, null, null);
        assertThat(loop).isNotNull();
    }

    @Test
    void exposesToolsAndAgentDataDir() {
        WebAgentRuntime rt = new WebAgentRuntime(mockProvider(), new ToolRegistry(), new TokenEstimator());
        assertThat(rt.tools()).isNotNull();
        assertThat(rt.agentDataDir()).isNotNull();
    }

    @Test
    void createLoopWithConfirmerBuilds() {
        WebAgentRuntime rt = new WebAgentRuntime(mockProvider(), new ToolRegistry(), new TokenEstimator());
        var loop = rt.createLoop("s1", "s1", SessionLogSink.NOOP, com.example.agent.permission.PermissionConfirmer.allowAll(), null);
        assertThat(loop).isNotNull();
    }
}


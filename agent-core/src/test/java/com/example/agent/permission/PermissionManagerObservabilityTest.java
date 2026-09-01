package com.example.agent.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.log.SessionLogSink;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PermissionManager 可观测性：ask/deny 裁决广播 permission/decision，allow 不广播。
 */
class PermissionManagerObservabilityTest {

    static final class CapturingSink implements SessionLogSink {
        final List<String> decisions = new ArrayList<>();

        @Override
        public void onPermissionDecision(Map<String, Object> payload) {
            decisions.add(String.valueOf(payload));
        }
    }

    @Test
    void askDecisionIsBroadcast() {
        CapturingSink sink = new CapturingSink();
        PermissionManager mgr = new PermissionManager();
        mgr.setSink(sink);

        // OTHER 分类默认 ask
        mgr.decide("UnknownTool", null);

        assertEquals(1, sink.decisions.size());
        String ev = sink.decisions.get(0);
        assertTrue(ev.contains("tool=UnknownTool"));
        assertTrue(ev.contains("decision=ask"));
    }

    @Test
    void allowDecisionIsNotBroadcast() {
        CapturingSink sink = new CapturingSink();
        PermissionManager mgr = new PermissionManager();
        mgr.setSink(sink);

        // READ 分类默认 allow（defaultRead=true）
        mgr.decide("ReadFile", null);

        assertEquals(0, sink.decisions.size(), "allow 不应产生 permission/decision 事件");
    }

    @Test
    void sensitivePathAskIncludesPath() {
        CapturingSink sink = new CapturingSink();
        PermissionManager mgr = new PermissionManager();
        mgr.setSink(sink);

        // 敏感路径触发 ask 且带 path
        mgr.decide("ReadFile", new com.example.agent.tools.file.ReadFileTool.Input("~/.ssh/id_rsa"));

        assertEquals(1, sink.decisions.size());
        assertTrue(sink.decisions.get(0).contains("decision=ask"));
    }

    @Test
    void noopSinkWithoutSetterDoesNothing() {
        PermissionManager mgr = new PermissionManager();
        // 未 setSink 时默认 no-op，decide 不抛异常
        mgr.decide("UnknownTool", null);
    }

    @Test
    void fullAccessAllowIsNotBroadcast() {
        CapturingSink sink = new CapturingSink();
        PermissionManager mgr = new PermissionManager();
        mgr.setSink(sink);
        mgr.setMode(PermissionMode.FULL_ACCESS);

        // FULL_ACCESS 下 WriteFile 应为 allow，不广播
        mgr.decide("WriteFile", new com.example.agent.tools.file.WriteFileTool.Input("a.txt", "x"));

        assertEquals(0, sink.decisions.size(), "full_access allow 不应产生 permission/decision 事件");
    }

    @Test
    void workspaceWriteOutsideAsksAndBroadcasts() {
        CapturingSink sink = new CapturingSink();
        PermissionManager mgr = new PermissionManager();
        mgr.setSink(sink);
        mgr.setMode(PermissionMode.WORKSPACE_WRITE);
        mgr.setWorkingDirectory(java.nio.file.Paths.get("/ws"));

        // 工作区外写 → ask 且广播
        mgr.decide("WriteFile", new com.example.agent.tools.file.WriteFileTool.Input("/outside/b.txt", "x"));

        assertEquals(1, sink.decisions.size());
        assertTrue(sink.decisions.get(0).contains("decision=ask"));
    }
}

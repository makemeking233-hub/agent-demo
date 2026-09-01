package com.example.agent.log;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.agent.core.Message;
import com.example.agent.llm.ToolCall;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class CompositeSessionLogSinkTest {

    /** 记录收到的各回调的测试 sink。 */
    private static final class RecordingSink implements SessionLogSink {
        final List<String> calls = new ArrayList<>();

        @Override
        public void onTurnStart(int turn) {
            calls.add("onTurnStart:" + turn);
        }

        @Override
        public void onUser(Message.User user) {
            calls.add("onUser:" + user.content());
        }

        @Override
        public void onAssistant(Message.Assistant assistant, List<String> thinking) {
            calls.add("onAssistant:" + assistant.content());
        }

        @Override
        public void onToolCall(ToolCall call) {
            calls.add("onToolCall:" + call.name());
        }

        @Override
        public void onSystemEvent(String type, Map<String, Object> payload) {
            calls.add("onSystemEvent:" + type);
        }

        @Override
        public void onPermissionDecision(Map<String, Object> payload) {
            calls.add("onPermissionDecision:" + payload);
        }
    }

    @Test
    void forwardsToAllDelegates() {
        RecordingSink a = new RecordingSink();
        RecordingSink b = new RecordingSink();
        CompositeSessionLogSink sink = new CompositeSessionLogSink(List.of(a, b));

        sink.onTurnStart(1);
        sink.onUser(new Message.User("hi"));
        sink.onAssistant(new Message.Assistant("hello", List.of()), List.of());
        sink.onToolCall(new ToolCall("c1", "ReadFile", "{}"));
        sink.onSystemEvent("system/config", Map.of("k", 1));
        sink.onPermissionDecision(Map.of("tool", "WriteFile"));

        // 两个子 sink 都应收到全部 6 个回调
        for (RecordingSink r : List.of(a, b)) {
            assertEquals(6, r.calls.size());
            assertEquals("onTurnStart:1", r.calls.get(0));
            assertEquals("onUser:hi", r.calls.get(1));
            assertEquals("onAssistant:hello", r.calls.get(2));
            assertEquals("onToolCall:ReadFile", r.calls.get(3));
            assertEquals("onSystemEvent:system/config", r.calls.get(4));
            assertEquals("onPermissionDecision:{tool=WriteFile}", r.calls.get(5));
        }
    }

    @Test
    void emptyDelegatesAreNoop() {
        CompositeSessionLogSink sink = new CompositeSessionLogSink(List.of());
        sink.onUser(new Message.User("x")); // 不抛异常即可
    }

    @Test
    void delegateThrowDoesNotAbortOthers() {
        RecordingSink good = new RecordingSink();
        SessionLogSink bad = new SessionLogSink() {
            @Override
            public void onUser(Message.User user) {
                throw new RuntimeException("boom");
            }
        };
        CompositeSessionLogSink sink = new CompositeSessionLogSink(List.of(bad, good));

        sink.onUser(new Message.User("hi"));

        assertEquals(1, good.calls.size());
        assertEquals("onUser:hi", good.calls.get(0));
    }

    @Test
    void constructorSkipsNullDelegates() {
        AtomicInteger count = new AtomicInteger(0);
        SessionLogSink good = new SessionLogSink() {
            @Override
            public void onUser(Message.User user) {
                count.incrementAndGet();
            }
        };
        CompositeSessionLogSink sink =
                new CompositeSessionLogSink(null, good, null); // first=null, rest 含 null
        sink.onUser(new Message.User("hi"));
        assertEquals(1, count.get());
        // null 列表视为空，不抛异常
        new CompositeSessionLogSink((List<SessionLogSink>) null).onUser(new Message.User("x"));
    }
}

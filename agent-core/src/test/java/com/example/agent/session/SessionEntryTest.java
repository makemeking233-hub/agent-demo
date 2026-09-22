package com.example.agent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.llm.ToolCall;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link SessionEntry} 工厂方法覆盖（fix-jacoco-rule）。
 *
 * <p>{@code SessionEntry} 的 BRANCH 覆盖率此前只有 0.375：4 个工厂方法里
 * {@code parent == null ? null : parent.toString()} 的「非空」分支从未被跑到（既有测试一律传
 * {@code null}）。本类逐个补上非空 parent，并覆盖 record 的 {@code equals}/{@code hashCode}。
 */
class SessionEntryTest {

    @Test
    void userWithParentSerialisesParentUuid() {
        UUID parent = UUID.randomUUID();
        SessionEntry e = SessionEntry.user("hi", parent);

        assertEquals("user", e.type());
        assertEquals(parent.toString(), e.parentUuid());
        assertEquals("hi", e.content());
        assertNull(e.extras());
        assertTrue(e.timestamp() > 0);
    }

    @Test
    void assistantWithParentKeepsToolCallsInExtras() {
        UUID parent = UUID.randomUUID();
        List<ToolCall> calls = List.of(new ToolCall("c1", "ReadFile", "{}"));
        SessionEntry e = SessionEntry.assistant("answer", calls, parent);

        assertEquals("assistant", e.type());
        assertEquals(parent.toString(), e.parentUuid());
        assertEquals(calls, e.extras().get("toolCalls"));
    }

    @Test
    void toolResultWithParentKeepsCallIdAndErrorFlag() {
        UUID parent = UUID.randomUUID();
        SessionEntry e = SessionEntry.toolResult("c9", "out", true, parent);

        assertEquals("tool_result", e.type());
        assertEquals(parent.toString(), e.parentUuid());
        assertEquals("c9", e.extras().get("toolCallId"));
        assertEquals(true, e.extras().get("isError"));
    }

    @Test
    void systemWithParentSerialisesParentUuid() {
        UUID parent = UUID.randomUUID();
        SessionEntry e = SessionEntry.system("sys", parent);

        assertEquals("system", e.type());
        assertEquals(parent.toString(), e.parentUuid());
        assertNull(e.extras());
    }

    @Test
    void metaHasNoParentAndCarriesKeyValue() {
        SessionEntry e = SessionEntry.meta("prompt", 10);

        assertEquals("meta", e.type());
        assertNull(e.parentUuid());
        assertNull(e.content());
        assertEquals("prompt", e.extras().get("key"));
        assertEquals(10, e.extras().get("value"));
    }

    @Test
    void factoriesWithNullParentProduceNullParentUuid() {
        assertNull(SessionEntry.user("a", null).parentUuid());
        assertNull(SessionEntry.assistant("b", List.of(), null).parentUuid());
        assertNull(SessionEntry.toolResult("c", "d", false, null).parentUuid());
        assertNull(SessionEntry.system("e", null).parentUuid());
    }

    @Test
    void recordEqualsAndHashCodeAreValueBased() {
        SessionEntry a =
                new SessionEntry("user", "u-1", null, "same", Map.of("k", "v"), 1234L);
        SessionEntry b =
                new SessionEntry("user", "u-1", null, "same", Map.of("k", "v"), 1234L);
        SessionEntry c =
                new SessionEntry("user", "u-2", null, "same", Map.of("k", "v"), 1234L);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        // 与非同类对象比较走 record 隐式 equals 的 instanceof false 分支
        assertNotEquals(a, "not-an-entry");
        assertEquals(a, a);
    }

    @Test
    void recordAccessorsExposeCanonicalComponents() {
        SessionEntry e =
                new SessionEntry("assistant", "uuid", "parent", "body", Map.of("x", 1), 42L);

        assertEquals("assistant", e.type());
        assertEquals("uuid", e.uuid());
        assertEquals("parent", e.parentUuid());
        assertEquals("body", e.content());
        assertEquals(1, e.extras().get("x"));
        assertEquals(42L, e.timestamp());
    }
}

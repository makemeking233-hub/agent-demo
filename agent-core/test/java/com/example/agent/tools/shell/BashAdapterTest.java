package com.example.agent.tools.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

class BashAdapterTest {

    @Test
    void commandLineFormat() {
        assertEquals(List.of("/bin/bash", "-c", "ls"), new BashAdapter().commandLine("ls"));
    }

    @Test
    void denylistIncludesRmRf() {
        assertTrue(new BashAdapter().defaultDenylist().contains("rm -rf"));
    }

    /** §6.6 黑名单匹配语义验证 */
    @Test
    void matchesRmRf() {
        var a = new BashAdapter();
        assertTrue(a.isDenylisted("rm -rf /tmp"));
        assertTrue(a.isDenylisted("rm -fr /tmp"));
        assertTrue(a.isDenylisted("/bin/rm -r -f /tmp"));
    }

    @Test
    void doesNotMatchInnocuous() {
        var a = new BashAdapter();
        assertFalse(a.isDenylisted("ls -rf"));
        assertFalse(a.isDenylisted("rm /tmp"));
        assertFalse(a.isDenylisted("rm -r /tmp"));
        assertFalse(a.isDenylisted("echo hello"));
    }
}

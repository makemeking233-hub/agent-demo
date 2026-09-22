package com.example.agent.tools;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** {@link PathGuard} 覆盖（fix-jacoco-rule）：补 BRANCH。 */
class PathGuardTest {
    @Test
    void denyIfTraversalReturnsNullForSafePath() {
        assertNull(PathGuard.denyIfTraversal("a/b/c.txt"));
    }

    @Test
    void denyIfTraversalDeniesParentTraversal() {
        assertNotNull(PathGuard.denyIfTraversal("../etc/passwd"));
    }

    @Test
    void denyIfTraversalDeniesMidPathTraversal() {
        // .. 出现在路径中间同样应被拒绝
        assertNotNull(PathGuard.denyIfTraversal("a/../b"));
    }

    @Test
    void denyIfTraversalDeniesNullPath() {
        assertNotNull(PathGuard.denyIfTraversal(null));
    }
}

package com.example.agent.web.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PermissionBridgeTest {
    @Test
    void yesDecisionResolvesWaiter() throws Exception {
        var bridge = new PermissionBridge();
        String permId = bridge.newPermissionId();
        var waiterDone = new CountDownLatch(1);
        var decisionHolder = new String[1];
        Thread waiter = new Thread(() -> {
            decisionHolder[0] = bridge.waitForDecision(permId, "c1", "WriteFile", "d", List.of("yes", "no", "always"));
            waiterDone.countDown();
        });
        waiter.start();
        Thread.sleep(50);
        assertThat(bridge.submitDecision(permId, "yes")).isTrue();
        assertThat(waiterDone.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(decisionHolder[0]).isEqualTo("yes");
    }

    @Test
    void noDecisionResolvesWaiter() throws Exception {
        var bridge = new PermissionBridge();
        String permId = bridge.newPermissionId();
        var decisionHolder = new String[1];
        Thread waiter = new Thread(() -> decisionHolder[0] = bridge.waitForDecision(permId, "c1", "rm", "d", List.of("yes", "no")));
        waiter.start();
        Thread.sleep(50);
        bridge.submitDecision(permId, "no");
        waiter.join(2000);
        assertThat(decisionHolder[0]).isEqualTo("no");
    }

    @Test
    void alwaysDecisionResolvesWaiter() throws Exception {
        var bridge = new PermissionBridge();
        String permId = bridge.newPermissionId();
        var decisionHolder = new String[1];
        Thread waiter = new Thread(() -> decisionHolder[0] = bridge.waitForDecision(permId, "c1", "WriteFile", "d", List.of("yes", "no", "always")));
        waiter.start();
        Thread.sleep(50);
        bridge.submitDecision(permId, "always");
        waiter.join(2000);
        assertThat(decisionHolder[0]).isEqualTo("always");
    }

    @Test
    void invalidDecisionIsIgnored() throws Exception {
        var bridge = new PermissionBridge();
        String permId = bridge.newPermissionId();
        var decisionHolder = new String[1];
        Thread waiter = new Thread(() -> decisionHolder[0] = bridge.waitForDecision(permId, "c1", "WriteFile", "d", List.of("yes", "no")));
        waiter.start();
        Thread.sleep(50);
        assertThat(bridge.submitDecision(permId, "maybe")).isFalse();
        assertThat(bridge.hasPending(permId)).isTrue();
        bridge.submitDecision(permId, "yes");
        waiter.join(2000);
        assertThat(decisionHolder[0]).isEqualTo("yes");
    }

    @Test
    void submitOnUnknownIdReturnsFalse() {
        var bridge = new PermissionBridge();
        assertThat(bridge.submitDecision("not-there", "yes")).isFalse();
    }

    @Test
    void hasPendingTracksWaiter() {
        var bridge = new PermissionBridge();
        String permId = bridge.newPermissionId();
        assertThat(bridge.hasPending(permId)).isFalse();
        var t = new Thread(() -> bridge.waitForDecision(permId, "c1", "rm", "d", List.of("yes", "no")));
        t.start();
        try { Thread.sleep(50); } catch (Exception e) {}
        assertThat(bridge.hasPending(permId)).isTrue();
        bridge.submitDecision(permId, "yes");
        try { t.join(2000); } catch (Exception e) {}
        assertThat(bridge.hasPending(permId)).isFalse();
    }
}

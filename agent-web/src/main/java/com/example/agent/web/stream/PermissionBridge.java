package com.example.agent.web.stream;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("web")
public class PermissionBridge {

    private static final Logger log = LoggerFactory.getLogger(PermissionBridge.class);

    public static final String DECISION_YES = "yes";
    public static final String DECISION_NO = "no";
    public static final String DECISION_ALWAYS = "always";

    private final Map<String, Waiter> waiters = new ConcurrentHashMap<>();

    private static class Waiter {
        String decision;
        volatile boolean done;
        final Thread thread = Thread.currentThread();
    }

    public String waitForDecision(String permissionId, String toolCallId, String toolName,
                                  String reason, List<String> choices) {
        Waiter w = new Waiter();
        waiters.put(permissionId, w);
        try {
            while (!w.done) {
                LockSupport.park(this);
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    return DECISION_NO;
                }
            }
            return w.decision;
        } finally {
            waiters.remove(permissionId);
        }
    }

    public boolean submitDecision(String permissionId, String decision) {
        Waiter w = waiters.get(permissionId);
        if (w == null) {
            log.debug("submitDecision: no waiter for {}", permissionId);
            return false;
        }
        if (!List.of(DECISION_YES, DECISION_NO, DECISION_ALWAYS).contains(decision)) {
            log.warn("submitDecision: invalid decision ''{}'' for {}", decision, permissionId);
            return false;
        }
        synchronized (w) {
            w.decision = decision;
            w.done = true;
            LockSupport.unpark(w.thread);
        }
        return true;
    }

    public boolean hasPending(String permissionId) {
        return waiters.containsKey(permissionId);
    }

    public String newPermissionId() {
        return UUID.randomUUID().toString();
    }

    @PreDestroy
    public void shutdown() {
        for (Waiter w : waiters.values()) {
            w.done = true;
            LockSupport.unpark(w.thread);
        }
        waiters.clear();
    }
}

package com.example.agent.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * settings 变更广播（add-settings-foundation M1）。
 *
 * <p>维护一组 listener；任何写操作完成后调用 {@link #broadcast(SettingsView)}，所有 listener 收到事件。
 */
public class SettingsChangeBroadcaster {
    private static final Logger log = LoggerFactory.getLogger(SettingsChangeBroadcaster.class);

    public interface Listener {
        void onSettingsChanged(SettingsView view);
    }

    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private final AtomicLong eventCount = new AtomicLong(0);

    /** 注册监听器；返回反注册 lambda */
    public Runnable register(Listener listener) {
        listeners.add(listener);
        return () -> {
            listeners.remove(listener);
        };
    }

    public void broadcast(SettingsView view) {
        long seq = eventCount.incrementAndGet();
        log.debug("[settings] 广播变更 #{}", seq);
        for (Listener l : listeners) {
            try {
                l.onSettingsChanged(view);
            } catch (Exception e) {
                log.warn("[settings] listener 异常: {}", e.getMessage());
            }
        }
    }

    public int listenerCount() {
        return listeners.size();
    }

    public long totalBroadcasts() {
        return eventCount.get();
    }

    /** 测试用：清空所有 listener */
    public void clearForTest() {
        listeners.clear();
    }

    /** 测试用：当前 listeners 快照 */
    public Set<Listener> listenersSnapshot() {
        return Set.copyOf(listeners);
    }
}

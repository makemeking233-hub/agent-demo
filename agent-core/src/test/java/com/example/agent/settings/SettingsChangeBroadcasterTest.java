package com.example.agent.settings;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsChangeBroadcasterTest {

    @Test
    void broadcast_deliversToAllListeners() {
        SettingsChangeBroadcaster b = new SettingsChangeBroadcaster();
        AtomicInteger c1 = new AtomicInteger();
        AtomicInteger c2 = new AtomicInteger();
        b.register(v -> c1.incrementAndGet());
        b.register(v -> c2.incrementAndGet());

        b.broadcast(new SettingsView());

        assertEquals(1, c1.get());
        assertEquals(1, c2.get());
        assertEquals(1L, b.totalBroadcasts());
    }

    @Test
    void unregister_stopsDelivery() {
        SettingsChangeBroadcaster b = new SettingsChangeBroadcaster();
        AtomicInteger c = new AtomicInteger();
        Runnable unregister = b.register(v -> c.incrementAndGet());

        b.broadcast(new SettingsView());
        unregister.run();
        b.broadcast(new SettingsView());

        assertEquals(1, c.get());
    }

    @Test
    void listenerException_doesNotStopOthers() {
        SettingsChangeBroadcaster b = new SettingsChangeBroadcaster();
        AtomicInteger c2 = new AtomicInteger();
        b.register(v -> { throw new RuntimeException("boom"); });
        b.register(v -> c2.incrementAndGet());

        b.broadcast(new SettingsView());

        assertEquals(1, c2.get());
    }

    @Test
    void listenerCount_reflectsRegistrations() {
        SettingsChangeBroadcaster b = new SettingsChangeBroadcaster();
        assertEquals(0, b.listenerCount());
        Runnable r1 = b.register(v -> {});
        Runnable r2 = b.register(v -> {});
        assertEquals(2, b.listenerCount());
        r1.run();
        assertEquals(1, b.listenerCount());
        r2.run();
        assertEquals(0, b.listenerCount());
    }
}

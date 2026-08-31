package com.example.agent.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agent.config.AgentConfig;
import com.example.agent.tools.ToolRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PluginManagerTest {

    @Test
    void initCallsPluginsInOrder() {        TestPlugin.events.clear();
        TestPlugin a = new TestPlugin("a");
        TestPlugin b = new TestPlugin("b");
        TestPlugin c = new TestPlugin("c");

        new PluginManager(List.of(a, b, c), cfg(), new ToolRegistry()).init();

        assertThat(a.inited).isTrue();
        assertThat(b.inited).isTrue();
        assertThat(c.inited).isTrue();
        // 记录顺序
        assertThat(TestPlugin.events).containsExactly("a.init", "b.init", "c.init");
    }

    @Test
    void closeCallsPluginsInReverseOrder() {
        TestPlugin a = new TestPlugin("a");
        TestPlugin b = new TestPlugin("b");
        TestPlugin c = new TestPlugin("c");

        var mgr = new PluginManager(List.of(a, b, c), cfg(), new ToolRegistry());
        mgr.init();
        TestPlugin.events.clear();
        mgr.close();

        assertThat(TestPlugin.events).containsExactly("c.close", "b.close", "a.close");
    }

    @Test
    void singleInitFailureDoesNotPreventOthers() {        TestPlugin.events.clear();
        TestPlugin a = new TestPlugin("a");
        TestPlugin b = new TestPlugin("b", true, false);
        TestPlugin c = new TestPlugin("c");

        new PluginManager(List.of(a, b, c), cfg(), new ToolRegistry()).init();

        assertThat(a.inited).isTrue();
        assertThat(c.inited).isTrue();
    }

    @Test
    void singleCloseFailureDoesNotPreventOthers() {        TestPlugin.events.clear();
        TestPlugin a = new TestPlugin("a", false, true);
        TestPlugin b = new TestPlugin("b");

        var mgr = new PluginManager(List.of(a, b), cfg(), new ToolRegistry());
        mgr.init();
        TestPlugin.events.clear();
        mgr.close();
        // a 抛异常, b 仍 close
        assertThat(b.closed).isTrue();
    }

    @Test
    void duplicateClassNameSecondInitSkipped() {        TestPlugin.events.clear();
        TestPlugin a1 = new TestPlugin("a");
        TestPlugin a2 = new TestPlugin("a");
        new PluginManager(List.of(a1, a2), cfg(), new ToolRegistry()).init();
        assertThat(a1.inited).isTrue();
        assertThat(a2.inited).isFalse();
    }

    @Test
    void contextExposesToolRegistry() {
        AgentConfig cfg = cfg();
        ToolRegistry registry = new ToolRegistry();
        PluginContext ctx = new PluginContext(cfg, registry, null, null, null, null);
        assertThat(ctx.cfg()).isSameAs(cfg);
        assertThat(ctx.tools()).isSameAs(registry);
    }

    private static AgentConfig cfg() {
        return AgentConfig.defaults();
    }

    /** 真实 Plugin 实现, 记录所有 lifecycle 调用, 可选 throw. */
    static class TestPlugin implements Plugin {
        static final List<String> events = new ArrayList<>();
        final String name;
        final boolean throwOnInit;
        final boolean throwOnClose;
        boolean inited = false;
        boolean closed = false;

        TestPlugin(String name) { this(name, false, false); }
        TestPlugin(String name, boolean throwOnInit, boolean throwOnClose) {
            this.name = name;
            this.throwOnInit = throwOnInit;
            this.throwOnClose = throwOnClose;
        }

        @Override public String name() { return name; }
        @Override public void init(PluginContext ctx) {
            events.add(name + ".init");
            inited = true;
            if (throwOnInit) throw new RuntimeException(name + "-init-fail");
        }
        @Override public void close() {
            events.add(name + ".close");
            closed = true;
            if (throwOnClose) throw new RuntimeException(name + "-close-fail");
        }
    }
}


package com.example.agent.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

/**
 * fix-cli-residue：{@link AgentLoop#DEFAULT_MODEL} 不再是已停用的 {@code deepseek-chat}。
 */
class AgentLoopDefaultModelTest {

    @Test
    void defaultModelConstantsAlignWithWebProfile() throws Exception {
        // DEFAULT_MODEL 是 private static final，通过反射读常量
        var field = AgentLoop.class.getDeclaredField("DEFAULT_MODEL");
        field.setAccessible(true);
        assertThat(Modifier.isStatic(field.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(field.getModifiers())).isTrue();
        Object value = field.get(null);
        assertThat(value).isEqualTo("deepseek-v4-flash");
    }
}

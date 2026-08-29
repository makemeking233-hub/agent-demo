package com.example.agent.web.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * T6.3: web profile 启动时打印 {@code dsh web: http://<host>:<port>} 单行 banner.
 *
 * <p>测试只调用 {@link WebStartupBanner#banner(WebProperties)} 的纯函数逻辑,
 * 不要求真实 Spring 上下文 / HTTP server 启动 (避免引入 @LocalServerPort 的集成复杂度).
 * 断言: 输出行 = "dsh web: http://<host>:<port>", 单行, 无多余换行.
 */
class WebStartupBannerTest {

    @Test
    void formatsLoopbackHostBanner() {
        var props = new WebProperties("127.0.0.1", 8080, java.util.List.of());
        String banner = WebStartupBanner.banner(props);
        assertThat(banner).isEqualTo("dsh web: http://127.0.0.1:8080");
    }

    @Test
    void formatsLanHostBanner() {
        var props = new WebProperties("192.168.1.42", 8080, java.util.List.of());
        String banner = WebStartupBanner.banner(props);
        assertThat(banner).isEqualTo("dsh web: http://192.168.1.42:8080");
    }

    @Test
    void isSingleLine() {
        var props = new WebProperties("127.0.0.1", 8080, java.util.List.of());
        String banner = WebStartupBanner.banner(props);
        assertThat(banner).doesNotContain("\n").doesNotContain("\r");
    }
}

package com.example.agent.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.agent.web.config.WebProperties;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

/** T2.2: TrustedHostFilter IP 鉴权 (spec §Trusted Host Auth / 5 个 scenario). */
class TrustedHostFilterTest {

    private static WebProperties props(List<String> trusted) {
        return new WebProperties("127.0.0.1", 8080, trusted, null);
    }

    /** https profile 启用/关闭的构造器；jacoco security 覆盖专用。 */
    private static WebProperties propsHttps(List<String> trusted, boolean httpsEnabled) {
        var https = new WebProperties.Https(httpsEnabled, null, null, null);
        return new WebProperties("127.0.0.1", 8080, trusted, https);
    }

    @Test
    void loopbackAlwaysAllowed() {
        var filter = new TrustedHostFilter(props(List.of()));
        var exchange = exchange("/api/chat/send", "127.0.0.1");
        StepVerifier.create(filter.filter(exchange, e -> reactor.core.publisher.Mono.empty()))
                .verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isNull(); // 没设 = 透传
    }

    @Test
    void ipv6LoopbackAlsoAllowed() {
        var filter = new TrustedHostFilter(props(List.of()));
        var exchange = exchange("/api/chat/send", "0:0:0:0:0:0:0:1");
        StepVerifier.create(filter.filter(exchange, e -> reactor.core.publisher.Mono.empty()))
                .verifyComplete();
    }

    @Test
    void untrustedNonLoopbackIs403() {
        var filter = new TrustedHostFilter(props(List.of("192.168.1.0/24")));
        var exchange = exchange("/api/chat/send", "10.0.0.5");
        StepVerifier.create(filter.filter(exchange, e -> reactor.core.publisher.Mono.empty()))
                .verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void trustedNonLoopbackPasses() {
        var filter = new TrustedHostFilter(props(List.of("192.168.1.0/24")));
        var exchange = exchange("/api/chat/send", "192.168.1.42");
        StepVerifier.create(filter.filter(exchange, e -> reactor.core.publisher.Mono.empty()))
                .verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void healthPathSkipsCheck() {
        // 配空 trusted, 非 loopback 也允许 /api/health
        var filter = new TrustedHostFilter(props(List.of()));
        var exchange = exchange("/api/health", "10.0.0.5");
        StepVerifier.create(filter.filter(exchange, e -> reactor.core.publisher.Mono.empty()))
                .verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void nonApiPathSkipsCheck() {
        // /assets/x.js 之类绕过
        var filter = new TrustedHostFilter(props(List.of()));
        var exchange = exchange("/assets/index.js", "10.0.0.5");
        StepVerifier.create(filter.filter(exchange, e -> reactor.core.publisher.Mono.empty()))
                .verifyComplete();
    }

    @Test
    void emptyTrustedHostsDeniesAllNonLoopback() {
        var filter = new TrustedHostFilter(props(List.of()));
        var exchange = exchange("/api/chat/send", "192.168.1.42");
        StepVerifier.create(filter.filter(exchange, e -> reactor.core.publisher.Mono.empty()))
                .verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void singleIpTrustedHost() {
        var filter = new TrustedHostFilter(props(List.of("192.168.1.42")));
        // 命中
        var ok = exchange("/api/chat/send", "192.168.1.42");
        StepVerifier.create(filter.filter(ok, e -> reactor.core.publisher.Mono.empty())).verifyComplete();
        assertThat(ok.getResponse().getStatusCode()).isNull();
        // 不命中 (CIDR 外)
        var deny = exchange("/api/chat/send", "192.168.1.43");
        StepVerifier.create(filter.filter(deny, e -> reactor.core.publisher.Mono.empty())).verifyComplete();
        assertThat(deny.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private static MockServerWebExchange exchange(String path, String remoteIp) {
        var req = MockServerHttpRequest.get(path)
                .remoteAddress(new InetSocketAddress(remoteIp, 12345));
        return MockServerWebExchange.from(req);
    }

    // ===== jacoco security 包分支覆盖（fix-security-coverage）=====

    @Test
    void httpsLocalhostAllowedEvenIfNotInTrustedList() {
        // add-pwa-support：HTTPS profile 下，localhost 即使不在 trusted 列表也放行
        var filter = new TrustedHostFilter(propsHttps(List.of(), true));
        var exchange = exchange("/api/chat/send", "127.0.0.1");
        StepVerifier.create(filter.filter(exchange, e -> reactor.core.publisher.Mono.empty()))
                .verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void httpsEnabledButRemoteNotLocalhostFallsThroughToTrustedCheck() {
        // HTTPS 启用但远端不在 localhost 范围内 → 不走 https 兜底，按 trusted 判断
        var filter = new TrustedHostFilter(propsHttps(List.of("192.168.1.0/24"), true));
        var exchange = exchange("/api/chat/send", "10.0.0.5");
        StepVerifier.create(filter.filter(exchange, e -> reactor.core.publisher.Mono.empty()))
                .verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void nullTrustedHostsListDeniesAllNonLoopback() {
        // WebProperties 把 null 标准化成 List.of()，但 raw null 路径仍需覆盖（防御性）
        // 用直接 new WebProperties 并显式传 null：compact constructor 会标准化，所以这里测的是
        // isTrusted 在空集合路径上的 deny 分支。
        var filter = new TrustedHostFilter(new WebProperties("127.0.0.1", 8080, null, null));
        var exchange = exchange("/api/chat/send", "192.168.1.42");
        StepVerifier.create(filter.filter(exchange, e -> reactor.core.publisher.Mono.empty()))
                .verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void loopback127xRangeMatchViaStartsWithPrefix() {
        // 走 startsWith("127.") 分支（不是 127.0.0.1 的精确匹配）
        var filter = new TrustedHostFilter(props(List.of()));
        for (var ip : new String[] {"127.0.0.99", "127.255.255.254"}) {
            var exchange = exchange("/api/chat/send", ip);
            StepVerifier.create(filter.filter(exchange, e -> reactor.core.publisher.Mono.empty()))
                    .verifyComplete();
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }
    }

    @Test
    void trustedHostsEntryWithOnlyWhitespaceSkippedNotMatched() {
        // "  " 经 trim 后为空 → continue；非 loopback 远程被拒
        var filter = new TrustedHostFilter(props(List.of("  ")));
        var exchange = exchange("/api/chat/send", "192.168.1.42");
        StepVerifier.create(filter.filter(exchange, e -> reactor.core.publisher.Mono.empty()))
                .verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void cidr25PrefixMatchAndMissCoverRestBitsBranch() {
        // /25 → prefix=25 → fullBytes=3 → restBits=1 → 走掩码分支
        // 命中：192.168.1.0/25 含 0..127；不命中：128..255
        var matching = new TrustedHostFilter(props(List.of("192.168.1.0/25")));
        var ok = exchange("/api/chat/send", "192.168.1.50");
        StepVerifier.create(matching.filter(ok, e -> reactor.core.publisher.Mono.empty())).verifyComplete();
        assertThat(ok.getResponse().getStatusCode()).isNull();

        var deny = exchange("/api/chat/send", "192.168.1.200");
        StepVerifier.create(matching.filter(deny, e -> reactor.core.publisher.Mono.empty())).verifyComplete();
        assertThat(deny.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}

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
        return new WebProperties("127.0.0.1", 8080, trusted);
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
}
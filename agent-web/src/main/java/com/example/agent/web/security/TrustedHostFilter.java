package com.example.agent.web.security;

import com.example.agent.web.config.WebProperties;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Trusted-host 鉴权 (spec §Trusted Host Auth).
 *
 * <p>对 {@code /api/**} 校验请求源 IP:
 * <ul>
 *   <li>loopback (127.0.0.1, ::1) 永远放行</li>
 *   <li>非 loopback 必须在 {@code agent.web.trusted-hosts} (CIDR 或单 IP) 内</li>
 *   <li>不命中返 403 + {@code {"error":"host_not_trusted"}}</li>
 * </ul>
 *
 * <p>{@code /api/health} 跳过本 filter (spec §Health Check 永远 200).
 */
@Component
@Profile("web")
public class TrustedHostFilter implements WebFilter {

    private static final String PATH_PREFIX = "/api/";
    private static final String HEALTH_PATH = "/api/health";

    private final WebProperties props;

    public TrustedHostFilter(WebProperties props) {
        this.props = props;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith(PATH_PREFIX)) {
            return chain.filter(exchange);
        }
        if (HEALTH_PATH.equals(path)) {
            // spec §Health Check: /api/health 跳过 IP 校验, 永远 200
            return chain.filter(exchange);
        }

        String remote = resolveRemote(exchange);
        if (isLoopback(remote)) {
            return chain.filter(exchange);
        }

        if (!isTrusted(remote, props.trustedHosts())) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            String body = "{\"error\":\"host_not_trusted\"}";
            return exchange.getResponse()
                    .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes())));
        }

        return chain.filter(exchange);
    }

    private static String resolveRemote(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote == null) {
            return "0.0.0.0";
        }
        InetAddress addr = remote.getAddress();
        if (addr == null) {
            return remote.getHostString();
        }
        // strip IPv6 zone id if any
        return addr.getHostAddress().split("%")[0];
    }

    private static boolean isLoopback(String ip) {
        if (ip == null) return false;
        // 127.0.0.0/8 + ::1
        if (ip.equals("127.0.0.1") || ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1")) {
            return true;
        }
        if (ip.startsWith("127.")) {
            return true;
        }
        return false;
    }

    /**
     * 单 IP 或 CIDR 匹配. v0.1 简单实现: 拆分 IP, 逐个 CIDR 比 IPv4 前缀.
     * IPv6 CIDR 不支持 (够 v0.1 用, v0.2 再加).
     */
    private static boolean isTrusted(String ip, List<String> trustedHosts) {
        if (trustedHosts == null || trustedHosts.isEmpty()) {
            return false;
        }
        for (String rule : trustedHosts) {
            rule = rule.trim();
            if (rule.isEmpty()) continue;
            if (!rule.contains("/")) {
                // 单 IP
                if (rule.equals(ip)) return true;
            } else {
                // CIDR, v0.1 仅支持 IPv4
                int slash = rule.indexOf('/');
                String cidrIp = rule.substring(0, slash);
                int prefix = Integer.parseInt(rule.substring(slash + 1));
                if (matchIpv4Cidr(ip, cidrIp, prefix)) return true;
            }
        }
        return false;
    }

    private static boolean matchIpv4Cidr(String ip, String cidrIp, int prefix) {
        if (!ip.contains(".") || !cidrIp.contains(".")) return false;
        byte[] ipBytes = ipv4ToBytes(ip);
        byte[] cidrBytes = ipv4ToBytes(cidrIp);
        if (ipBytes == null || cidrBytes == null) return false;
        int fullBytes = prefix / 8;
        int restBits = prefix % 8;
        for (int i = 0; i < fullBytes && i < 4; i++) {
            if (ipBytes[i] != cidrBytes[i]) return false;
        }
        if (restBits > 0 && fullBytes < 4) {
            int mask = (0xFF << (8 - restBits)) & 0xFF;
            return (ipBytes[fullBytes] & mask) == (cidrBytes[fullBytes] & mask);
        }
        return true;
    }

    private static byte[] ipv4ToBytes(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return null;
        byte[] out = new byte[4];
        for (int i = 0; i < 4; i++) {
            try {
                int v = Integer.parseInt(parts[i]);
                if (v < 0 || v > 255) return null;
                out[i] = (byte) v;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return out;
    }
}
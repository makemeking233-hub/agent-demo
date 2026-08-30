package com.example.agent.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * SPA fallback (spec §Requirement: Static Resource Serving / Scenario: Client-side routes serve same index).
 *
 * <p>只匹配知名客户端路由前缀, 不匹配 /api/** 与 /assets/**.
 * 客户端路由前缀 v0.1: /sessions, /help, /settings, /chat, /logs.
 */
@Configuration
@Profile("web")
public class StaticResourceConfig {

    private static final Resource INDEX_HTML = new ClassPathResource("static/index.html");

    private static final String[] CLIENT_ROOT_PREFIXES = {"/sessions", "/help", "/settings", "/chat", "/logs"};

    @Bean
    public RouterFunction<ServerResponse> spaFallbackRouter() {
        RouterFunctions.Builder builder = RouterFunctions.route();
        for (String prefix : CLIENT_ROOT_PREFIXES) {
            builder = builder.GET(prefix, req -> okIndex()).GET(prefix + "/{*path}", req -> okIndex());
        }
        return builder.build();
    }

    private static Mono<ServerResponse> okIndex() {
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_HTML)
                .bodyValue(INDEX_HTML);
    }
}

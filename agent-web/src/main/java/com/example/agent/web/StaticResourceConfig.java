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
 * <p>职责:
 * <ul>
 *   <li>/assets/** 与根路径 / 由 Spring Boot 默认 static resource handling 接管
 *       (classpath:static/), 1 年 immutable cache 走 application-web.yml 配置</li>
 *   <li>本 config 只补 SPA fallback: 非 /api/, 非 /assets/, 非 / 的路径返 index.html,
 *       让 React Router 接管客户端路由</li>
 * </ul>
 */
@Configuration
@Profile("web")
public class StaticResourceConfig {

    private static final Resource INDEX_HTML = new ClassPathResource("static/index.html");

    @Bean
    public RouterFunction<ServerResponse> spaFallbackRouter() {
        return RouterFunctions.route()
                .GET("/**", req -> {
                    String p = req.path();
                    if (p.startsWith("/api/") || p.startsWith("/assets/") || "/".equals(p)) {
                        return ServerResponse.notFound().build();
                    }
                    return ServerResponse.ok()
                            .contentType(MediaType.TEXT_HTML)
                            .bodyValue(INDEX_HTML);
                })
                .before(req -> req)
                .build();
    }
}
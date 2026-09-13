package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agent.web.security.HomePathGuard;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * GET /api/fs/raw 的 **HTTP 层**验证（add-rich-markdown-rendering，T9）。
 *
 * <p>与 {@link FsControllerRawTest} 的分工：那边验证"业务语义"（状态码、错误码、字节内容），
 * 用的还是 {@code ResponseEntity} 对象；这边验证**经 WebFlux 真实序列化之后**的响应——
 * 尤其是响应头落在报文里长什么样。这两者不等价，实测会踩的坑包括：
 *
 * <ul>
 *   <li>{@code Content-Disposition} 带非 ASCII 文件名时可能被容器拒绝或写成乱码；
 *   <li>{@code Content-Type} 是否被容器改写、{@code X-Content-Type-Options} 是否真的发出去；
 *   <li>{@code Content-Security-Policy: sandbox} 是否会被过滤掉。
 * </ul>
 *
 * <p>用 {@code bindToController} 起 WebFlux 独立装配，不启动完整 Spring 上下文，因此不需要真实
 * {@code $HOME}，也不会碰用户真实数据（全局规则 §10）。trusted-host 过滤器不在此装配内，
 * 其行为由既有 spec 与 {@code TrustedHostFilter} 的测试覆盖。
 */
class FsControllerRawHttpTest {

    @TempDir Path home;

    /** 家目录之外的真实目录，用于验证 403 边界。 */
    @TempDir Path outsideHome;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client =
                WebTestClient.bindToController(new FsController(new HomePathGuard(home))).build();
    }

    @Test
    void servesPngWithHardenedHeadersOverHttp() throws IOException {
        Path img = home.resolve("arch.png");
        byte[] content = "PNGDATA".getBytes(StandardCharsets.UTF_8);
        Files.write(img, content);

        client.get()
                .uri(uri -> uri.path("/api/fs/raw").queryParam("path", img.toString()).build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType("image/png")
                .expectHeader()
                .valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader()
                .doesNotExist("Content-Security-Policy")
                .expectHeader()
                .value("Content-Disposition", v -> assertThat(v).startsWith("inline"))
                .expectBody()
                .consumeWith(r -> assertThat(r.getResponseBody()).isEqualTo(content));
    }

    @Test
    void servesSvgWithSandboxCspOverHttp() throws IOException {
        Path svg = home.resolve("arch.svg");
        Files.writeString(svg, "<svg xmlns=\"http://www.w3.org/2000/svg\"/>");

        client.get()
                .uri(uri -> uri.path("/api/fs/raw").queryParam("path", svg.toString()).build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType("image/svg+xml")
                .expectHeader()
                .valueEquals("Content-Security-Policy", "sandbox");
    }

    @Test
    void escapesNonAsciiFilenameInContentDisposition() throws IOException {
        Path img = home.resolve("架构图.png");
        Files.write(img, new byte[] {1, 2, 3});

        client.get()
                .uri(uri -> uri.path("/api/fs/raw").queryParam("path", img.toString()).build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .value(
                        "Content-Disposition",
                        v -> {
                            assertThat(v).startsWith("inline");
                            // RFC 5987 形式必须存在，否则中文名会被按 ISO-8859-1 写坏
                            assertThat(v).contains("filename*=UTF-8''");
                        });
    }

    @Test
    void rejectsPathOutsideHomeOverHttp() throws IOException {
        Path outside = outsideHome.resolve("outside.png");
        Files.write(outside, new byte[] {1});

        client.get()
                .uri(uri -> uri.path("/api/fs/raw").queryParam("path", outside.toString()).build())
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.error")
                .isEqualTo("path_outside_home");
    }

    @Test
    void rejectsNonWhitelistedTypeOverHttp() throws IOException {
        Path html = home.resolve("evil.html");
        Files.writeString(html, "<script>alert(1)</script>");

        client.get()
                .uri(uri -> uri.path("/api/fs/raw").queryParam("path", html.toString()).build())
                .exchange()
                .expectStatus()
                .isEqualTo(415)
                .expectBody()
                .jsonPath("$.error")
                .isEqualTo("unsupported_media_type");
    }
}

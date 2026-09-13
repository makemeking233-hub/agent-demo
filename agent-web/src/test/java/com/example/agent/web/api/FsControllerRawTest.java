package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agent.web.security.HomePathGuard;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * GET /api/fs/raw（add-rich-markdown-rendering）：对话区渲染本地图片所需的字节流端点。
 *
 * <p>覆盖三类关注点：
 *
 * <ul>
 *   <li>安全边界——与既有 {@code /api/fs/**} 完全一致（{@code $HOME} 子树 + 403）；
 *   <li>错误分支——目录 / 不存在 / 超大 / 非白名单类型；
 *   <li>响应头加固——{@code nosniff}、{@code Content-Disposition: inline}、SVG 的 {@code sandbox}。
 * </ul>
 */
class FsControllerRawTest {

    /** 与实现保持一致的大小上限（16 MiB）。 */
    private static final long MAX_RAW_BYTES = 16L * 1024 * 1024;

    @TempDir Path home;

    private FsController controller;

    @BeforeEach
    void setUp() {
        controller = new FsController(new HomePathGuard(home));
    }

    // ----- 正常路径 -----

    @Test
    void rawReturnsImageBytesWithinHome() throws IOException {
        Path img = home.resolve("arch.png");
        byte[] content = "PNGDATA".getBytes(StandardCharsets.UTF_8);
        Files.write(img, content);

        ResponseEntity<?> resp = controller.raw(img.toString());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat((byte[]) resp.getBody()).isEqualTo(content);
    }

    @Test
    void rawMapsWhitelistedExtensionsToMimeTypes() throws IOException {
        String[][] cases = {
            {"png", "image/png"},
            {"jpg", "image/jpeg"},
            {"jpeg", "image/jpeg"},
            {"gif", "image/gif"},
            {"webp", "image/webp"},
            {"avif", "image/avif"},
            {"bmp", "image/bmp"},
            {"ico", "image/x-icon"},
            {"svg", "image/svg+xml"},
        };
        for (String[] c : cases) {
            Path f = home.resolve("pic." + c[0]);
            Files.write(f, new byte[] {1, 2, 3});
            ResponseEntity<?> resp = controller.raw(f.toString());
            assertThat(resp.getStatusCode())
                    .as("extension .%s", c[0])
                    .isEqualTo(HttpStatus.OK);
            assertThat(resp.getHeaders().getContentType().toString())
                    .as("extension .%s", c[0])
                    .isEqualTo(c[1]);
        }
    }

    @Test
    void rawAcceptsUppercaseExtension() throws IOException {
        Path img = home.resolve("ARCH.PNG");
        Files.write(img, new byte[] {9});

        ResponseEntity<?> resp = controller.raw(img.toString());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getHeaders().getContentType().toString()).isEqualTo("image/png");
    }

    // ----- 安全边界 -----

    @Test
    void rawRejectsPathOutsideHome(@TempDir Path outsideHome) throws IOException {
        Path secret = outsideHome.resolve("secret.png");
        Files.write(secret, new byte[] {1});

        ResponseEntity<?> resp = controller.raw(secret.toString());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody()).extracting("error").isEqualTo("path_outside_home");
    }

    @Test
    void rawRejectsDotDotEscape() throws IOException {
        Path img = home.resolve("arch.png");
        Files.write(img, new byte[] {1});
        // home/../<something> 归一化后必然落在 home 之外
        String escaping = home.resolve("..").resolve("evil.png").toString();

        ResponseEntity<?> resp = controller.raw(escaping);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody()).extracting("error").isEqualTo("path_outside_home");
    }

    @Test
    void rawRejectsRelativePath() {
        ResponseEntity<?> resp = controller.raw("relative/pic.png");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).extracting("error").isEqualTo("path_not_absolute");
    }

    // ----- 错误分支 -----

    @Test
    void rawReturns404ForMissingFile() {
        ResponseEntity<?> resp = controller.raw(home.resolve("nope.png").toString());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).extracting("error").isEqualTo("path_not_found");
    }

    @Test
    void rawReturns400ForDirectory() throws IOException {
        Path dir = Files.createDirectory(home.resolve("a-folder"));
        ResponseEntity<?> resp = controller.raw(dir.toString());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).extracting("error").isEqualTo("not_a_file");
    }

    @Test
    void rawReturns413ForOversizeFile() throws IOException {
        Path big = home.resolve("big.png");
        // 稀疏文件：setLength 不实际写盘，避免测试写出 16 MiB 数据
        try (RandomAccessFile raf = new RandomAccessFile(big.toFile(), "rw")) {
            raf.setLength(MAX_RAW_BYTES + 1);
        }

        ResponseEntity<?> resp = controller.raw(big.toString());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(resp.getBody()).extracting("error").isEqualTo("file_too_large");
    }

    @Test
    void rawReturns415ForNonWhitelistedExtension() throws IOException {
        Path html = home.resolve("evil.html");
        Files.writeString(html, "<script>alert(1)</script>");

        ResponseEntity<?> resp = controller.raw(html.toString());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(resp.getBody()).extracting("error").isEqualTo("unsupported_media_type");
    }

    @Test
    void rawReturns415ForFileWithoutExtension() throws IOException {
        Path noExt = home.resolve("README");
        Files.writeString(noExt, "text");

        ResponseEntity<?> resp = controller.raw(noExt.toString());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(resp.getBody()).extracting("error").isEqualTo("unsupported_media_type");
    }

    // ----- 响应头加固 -----

    @Test
    void rawSetsNosniffOnSuccess() throws IOException {
        Path img = home.resolve("arch.png");
        Files.write(img, new byte[] {1});

        ResponseEntity<?> resp = controller.raw(img.toString());

        assertThat(resp.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void rawSetsInlineContentDisposition() throws IOException {
        Path img = home.resolve("arch.png");
        Files.write(img, new byte[] {1});

        ResponseEntity<?> resp = controller.raw(img.toString());

        String cd = resp.getHeaders().getFirst("Content-Disposition");
        assertThat(cd).isNotNull();
        assertThat(cd).startsWith("inline");
        assertThat(cd).contains("arch.png");
    }

    @Test
    void rawSetsSandboxCspForSvg() throws IOException {
        Path svg = home.resolve("arch.svg");
        Files.writeString(svg, "<svg xmlns=\"http://www.w3.org/2000/svg\"/>");

        ResponseEntity<?> resp = controller.raw(svg.toString());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getHeaders().getFirst("Content-Security-Policy")).isEqualTo("sandbox");
    }

    @Test
    void rawDoesNotSetSandboxCspForPng() throws IOException {
        Path img = home.resolve("arch.png");
        Files.write(img, new byte[] {1});

        ResponseEntity<?> resp = controller.raw(img.toString());

        assertThat(resp.getHeaders().getFirst("Content-Security-Policy")).isNull();
    }
}

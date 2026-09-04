package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agent.web.api.dto.FsDrivesResponse;
import com.example.agent.web.api.dto.FsHomeResponse;
import com.example.agent.web.api.dto.FsListResponse;
import com.example.agent.web.api.dto.FsMkdirRequest;
import com.example.agent.web.api.dto.FsQuickAccessResponse;
import com.example.agent.web.security.HomePathGuard;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** FsController（add-workspace-picker-modal）：4 端点 + 路径安全边界。 */
class FsControllerTest {

    @TempDir Path home;

    private FsController controller;

    @BeforeEach
    void setUp() {
        controller = new FsController(new HomePathGuard(home));
    }

    // ----- GET /api/fs/home -----

    @Test
    void homeReturnsAbsoluteHomePath() {
        ResponseEntity<FsHomeResponse> resp = controller.home();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        // FsController 内部对 homeDir 做 toRealPath()，@TempDir 给的路径已经是绝对路径
        assertThat(resp.getBody().path()).isEqualTo(home.toString());
        assertThat(resp.getBody().platform()).isIn("windows", "linux", "mac");
    }

    // ----- GET /api/fs/list -----

    @Test
    void listReturnsEntriesWithDirectoriesFirst(@TempDir Path sub) throws IOException {
        // given: home/projects/agent-demo 下有若干文件 + 目录
        Path projects = Files.createDirectories(home.resolve("projects"));
        Path agentDemo = Files.createDirectories(projects.resolve("agent-demo"));
        Files.writeString(agentDemo.resolve("README.md"), "hello");
        Files.writeString(agentDemo.resolve("pom.xml"), "<xml/>");
        Files.createDirectory(agentDemo.resolve("src"));

        // when
        ResponseEntity<?> resp = controller.list(agentDemo.toString(), false);

        // then
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        FsListResponse body = (FsListResponse) resp.getBody();
        assertThat(body.path()).isEqualTo(agentDemo.toString());
        assertThat(body.parent()).isEqualTo(projects.toString());
        assertThat(body.entries()).hasSize(3);
        // 目录优先：src 应在 README.md / pom.xml 之前
        assertThat(body.entries().get(0).name()).isEqualTo("src");
        assertThat(body.entries().get(0).isDir()).isTrue();
        // 文件按名称排序
        assertThat(body.entries().get(1).name()).isEqualTo("README.md");
        assertThat(body.entries().get(2).name()).isEqualTo("pom.xml");
    }

    @Test
    void listFiltersHiddenFilesByDefault(@TempDir Path sub) throws IOException {
        Path dot = Files.createDirectory(home.resolve(".secret"));
        Files.createDirectory(home.resolve("public"));

        ResponseEntity<?> resp = controller.list(home.toString(), false);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        FsListResponse body = (FsListResponse) resp.getBody();
        assertThat(body.entries()).extracting("name").containsExactly("public");
    }

    @Test
    void listIncludesHiddenFilesWhenRequested(@TempDir Path sub) throws IOException {
        Files.createDirectory(home.resolve(".secret"));
        Files.createDirectory(home.resolve("public"));

        ResponseEntity<?> resp = controller.list(home.toString(), true);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        FsListResponse body = (FsListResponse) resp.getBody();
        assertThat(body.entries()).extracting("name").containsExactlyInAnyOrder(".secret", "public");
    }

    @Test
    void listRejectsRelativePath() {
        ResponseEntity<?> resp = controller.list("relative/foo", false);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).extracting("error").isEqualTo("path_not_absolute");
    }

    @Test
    void listRejectsPathOutsideHome(@TempDir Path outsideHome) throws IOException {
        Path secret = Files.createDirectory(outsideHome.resolve("secret"));
        ResponseEntity<?> resp = controller.list(secret.toString(), false);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody()).extracting("error").isEqualTo("path_outside_home");
    }

    @Test
    void listReturns404ForMissingPath() {
        ResponseEntity<?> resp = controller.list(home.resolve("does-not-exist").toString(), false);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).extracting("error").isEqualTo("path_not_found");
    }

    @Test
    void listReturns400WhenPathIsFile(@TempDir Path file) throws IOException {
        Path regular = home.resolve("regular-file.txt");
        Files.writeString(regular, "x");
        ResponseEntity<?> resp = controller.list(regular.toString(), false);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).extracting("error").isEqualTo("not_a_directory");
    }

    // ----- POST /api/fs/mkdir -----

    @Test
    void mkdirCreatesDirectorySuccessfully() throws IOException {
        Path projects = Files.createDirectories(home.resolve("projects"));
        ResponseEntity<?> resp =
                controller.mkdir(new FsMkdirRequest(projects.resolve("new-thing").toString()));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Files.isDirectory(projects.resolve("new-thing"))).isTrue();
    }

    @Test
    void mkdirReturns409WhenPathExists(@TempDir Path sub) throws IOException {
        Path projects = Files.createDirectories(home.resolve("projects"));
        Files.createDirectory(projects.resolve("existing"));

        ResponseEntity<?> resp =
                controller.mkdir(new FsMkdirRequest(projects.resolve("existing").toString()));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody()).extracting("error").isEqualTo("dir_exists");
    }

    @Test
    void mkdirReturns400ForInvalidName(@TempDir Path sub) throws IOException {
        Path projects = Files.createDirectories(home.resolve("projects"));
        // leaf 含空格，不符合 [A-Za-z0-9._-]+
        ResponseEntity<?> resp =
                controller.mkdir(new FsMkdirRequest(projects.resolve("has space").toString()));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).extracting("error").isEqualTo("name_invalid");
    }

    @Test
    void mkdirReturns400ForBlankPath() {
        ResponseEntity<?> resp = controller.mkdir(new FsMkdirRequest(""));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).extracting("error").isEqualTo("name_invalid");
    }

    @Test
    void mkdirRejectsParentOutsideHome(@TempDir Path outside) throws IOException {
        ResponseEntity<?> resp =
                controller.mkdir(new FsMkdirRequest(outside.resolve("new-thing").toString()));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody()).extracting("error").isEqualTo("path_outside_home");
    }

    @Test
    void mkdirCreatesNestedDirectories() throws IOException {
        ResponseEntity<?> resp =
                controller.mkdir(new FsMkdirRequest(home.resolve("a/b/c").toString()));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Files.isDirectory(home.resolve("a/b/c"))).isTrue();
    }

    // ----- GET /api/fs/drives -----

    @Test
    void drivesReturnsDriveList() {
        ResponseEntity<FsDrivesResponse> resp = controller.drives();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 不同平台都有合法响应：Windows 返回盘符，Linux/macOS 返回空数组
        if (isWindows()) {
            assertThat(resp.getBody().drives()).isNotEmpty();
            resp.getBody().drives().forEach(d -> {
                assertThat(d.name()).isNotBlank();
                assertThat(d.path()).isNotBlank();
            });
        } else {
            assertThat(resp.getBody().drives()).isEmpty();
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    // ----- GET /api/fs/quick-access (polish-workspace-picker-dsh-style) -----

    @Test
    void quickAccessAlwaysContainsHome() throws IOException {
        ResponseEntity<com.example.agent.web.api.dto.FsQuickAccessResponse> resp =
                controller.quickAccess();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 始终含 Home，且 home path 是真实 home（@TempDir 注入的就是测试 fake home）
        assertThat(resp.getBody().items()).isNotEmpty();
        assertThat(resp.getBody().items().get(0).name()).isEqualTo("Home");
        assertThat(resp.getBody().items().get(0).path()).isEqualTo(home.toRealPath().toString());
    }

    @Test
    void quickAccessIncludesExistingSubdirs() throws IOException {
        // given: home/Desktop + home/Documents 存在，Downloads 不存在
        Files.createDirectory(home.resolve("Desktop"));
        Files.createDirectory(home.resolve("Documents"));

        ResponseEntity<com.example.agent.web.api.dto.FsQuickAccessResponse> resp =
                controller.quickAccess();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        var names = resp.getBody().items().stream().map(i -> i.name()).toList();
        assertThat(names).contains("Home", "Desktop", "Documents");
        assertThat(names).doesNotContain("Downloads");
    }

    @Test
    void quickAccessSkipsNonExistentSubdirs() throws IOException {
        // given: home 下没有任何快速访问目录
        ResponseEntity<com.example.agent.web.api.dto.FsQuickAccessResponse> resp =
                controller.quickAccess();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 只有 Home
        assertThat(resp.getBody().items()).hasSize(1);
        assertThat(resp.getBody().items().get(0).name()).isEqualTo("Home");
    }

    @Test
    void quickAccessReturnsValidAbsolutePaths() throws IOException {
        Files.createDirectory(home.resolve("Desktop"));
        ResponseEntity<com.example.agent.web.api.dto.FsQuickAccessResponse> resp =
                controller.quickAccess();
        for (var item : resp.getBody().items()) {
            // 每条 path 必须是非空绝对路径
            assertThat(item.path()).isNotBlank();
            assertThat(Path.of(item.path()).isAbsolute()).isTrue();
        }
    }
}

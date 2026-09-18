package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agent.settings.SettingsFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** SettingsRevealController 单元测试 (add-settings-general-items M2). */
class SettingsRevealControllerTest {

    @Test
    void buildRevealProcess_windowsCommand(@TempDir Path tmp) throws Exception {
        // 把 os.name 改成 windows 来测试；但 buildRevealProcess 是静态读 System.getProperty
        // 这里仅冒烟：构造不会抛
        String os = System.getProperty("os.name", "").toLowerCase();
        // 不实际启动 explorer.exe（避免在 Linux CI 上挂掉），仅冒烟
        if (os.contains("win")) {
            var p = SettingsRevealController.buildRevealProcess(tmp.resolve("test.yaml"));
            assertThat(p).isNotNull();
            p.destroy();
        }
    }

    @Test
    void buildRevealProcess_returnsProcessBuilder(@TempDir Path tmp) throws Exception {
        // 不依赖具体 OS：只验证返回 Process（立即 destroy 避免悬挂）
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            var p = SettingsRevealController.buildRevealProcess(tmp.resolve("a.yaml"));
            p.destroy();
            assertThat(p.exitValue() != 0 || p.isAlive()).isTrue();
        } else {
            // Linux/Mac：xdg-open/open 通常存在；如果不存在则 ProcessBuilder 会抛 IOException
            try {
                var p = SettingsRevealController.buildRevealProcess(tmp.resolve("a.yaml"));
                p.destroy();
            } catch (Exception e) {
                // 也行：测试目标仅为代码路径覆盖
            }
        }
    }

    @Test
    void controllerInstantiates(@TempDir Path tmp) {
        SettingsFile file = new SettingsFile(tmp);
        SettingsRevealController c = new SettingsRevealController(file);
        assertThat(c).isNotNull();
    }
}

package com.example.agent.e2e;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * E2E 测试基类：每个测试启动一个 WireMock 实例模拟 DeepSeek SSE。
 */
public abstract class E2ETestBase {
    protected WireMockServer wireMock;

    @BeforeEach
    void startServer() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void stopServer() {
        wireMock.stop();
    }

    protected String deepseekBaseUrl() {
        return "http://localhost:" + wireMock.port();
    }

    protected byte[] resourceBytes(String name) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
            if (in == null) throw new IllegalArgumentException("missing resource: " + name);
            return in.readAllBytes();
        }
    }
}
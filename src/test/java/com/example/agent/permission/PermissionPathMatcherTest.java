package com.example.agent.permission;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 覆盖 Ant glob 各种边界场景（参考 design.md §6.5 敏感路径匹配）。
 */
class PermissionPathMatcherTest {

    private final PermissionPathMatcher matcher = new PermissionPathMatcher(List.of(
        "**/.ssh/**",
        "**/.env*",
        "**/*credentials*",
        "**/*.pem"
    ));

    @Test
    void matchesBareFileName() {
        // **/ 可为空：裸 .env 也应命中
        assertTrue(matcher.matches(".env"));
    }

    @Test
    void matchesNestedSshDir() {
        assertTrue(matcher.matches("home/.ssh/id_rsa"));
        assertTrue(matcher.matches(".ssh/known_hosts"));
    }

    @Test
    void matchesEnvVariants() {
        assertTrue(matcher.matches("config/.env.production"));
        assertTrue(matcher.matches(".envrc"));
    }

    @Test
    void matchesCredentialsInName() {
        assertTrue(matcher.matches("aws-credentials.json"));
        assertTrue(matcher.matches("db/credentials-pg.txt"));
    }

    @Test
    void matchesPem() {
        assertTrue(matcher.matches("certs/server.pem"));
        assertTrue(matcher.matches("client.pem"));
    }

    @Test
    void doesNotMatchUnrelatedPaths() {
        assertFalse(matcher.matches("src/main/java/Foo.java"));
        assertFalse(matcher.matches("README.md"));
        assertFalse(matcher.matches("config.yaml"));
    }

    @Test
    void toRegexExposesGlobToRegexConversion() {
        // 公开方法用于测试（不是 private）
        assertTrue(PermissionPathMatcher.toRegex("**/.env*").startsWith("^"));
        assertTrue(PermissionPathMatcher.toRegex("**/.env*").endsWith("$"));
        assertTrue(PermissionPathMatcher.toRegex("**/.env*").contains("(?:.*/)?"));
    }

    @Test
    void nullPathReturnsFalse() {
        assertFalse(matcher.matches(null));
    }
}
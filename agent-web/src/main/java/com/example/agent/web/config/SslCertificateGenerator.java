package com.example.agent.web.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;
import javax.security.auth.x500.X500Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 自签证书生成器（add-pwa-support）。
 *
 * <p>当 {@code https} profile 激活 + {@code agent.web.https.enabled=true} 时，启动时
 * 用 keytool 命令行生成 RSA 2048 / SHA256withRSA / 365 天有效期的自签证书，
 * 写入 {@code ${agent.web.https.cert-dir}/keystore.p12}（PKCS12 格式）。
 *
 * <p>Spring Boot 通过 {@code server.ssl.key-store} + {@code key-store-password} + {@code key-alias}
 * 直接读 PKCS12 文件。
 *
 * <p>生成走 {@code keytool -genkeypair} 子进程，避免反射使用 sun.security.x509 内部 API。
 */
@Component
@Profile("https")
public class SslCertificateGenerator
        implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final Logger log = LoggerFactory.getLogger(SslCertificateGenerator.class);

    /** 证书有效期（天）。 */
    private static final int VALIDITY_DAYS = 365;

    /** RSA 密钥长度。 */
    private static final int KEY_SIZE = 2048;

    /** PKCS12 keystore 默认密码（自签证书场景，文档化）。 */
    static final String DEFAULT_PASSWORD = "changeit";

    private final Environment environment;

    @Value("${agent.web.https.cert-dir:${java.io.tmpdir}/agent-demo-cert}")
    private String certDir;

    @Value("${agent.web.https.keystore-password:" + DEFAULT_PASSWORD + "}")
    private String keystorePassword;

    @Value("${agent.web.https.keystore-alias:agent-demo}")
    private String keystoreAlias;

    public SslCertificateGenerator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        boolean httpsEnabled =
                Boolean.parseBoolean(
                        environment.getProperty("agent.web.https.enabled", "false"));
        if (!httpsEnabled) {
            log.info("[pwa-https] agent.web.https.enabled=false, skipping cert generation");
            return;
        }
        try {
            generateSelfSignedCert();
        } catch (Exception e) {
            log.error("[pwa-https] failed to generate self-signed certificate", e);
        }
    }

    /**
     * 生成自签 PKCS12 keystore。如果已存在（cert.pem + keystore.p12）则跳过。
     */
    void generateSelfSignedCert() throws IOException, InterruptedException {
        Path dir = Path.of(certDir);
        Path keystorePath = dir.resolve("keystore.p12");
        Files.createDirectories(dir);
        if (Files.exists(keystorePath)) {
            log.info("[pwa-https] keystore already exists at {}, skipping", keystorePath);
            return;
        }
        log.info("[pwa-https] generating self-signed keystore (this may take ~5 seconds)...");
        long start = System.currentTimeMillis();

        // keytool -genkeypair -alias <alias> -keyalg RSA -keysize 2048
        // -dname "CN=localhost,O=Agent-Demo" -validity 365 -storetype PKCS12
        // -keystore <path> -storepass <pass>
        ProcessBuilder pb =
                new ProcessBuilder(
                        "keytool",
                        "-genkeypair",
                        "-alias", keystoreAlias,
                        "-keyalg", "RSA",
                        "-keysize", String.valueOf(KEY_SIZE),
                        "-dname", "CN=localhost, O=Agent-Demo, C=CN",
                        "-validity", String.valueOf(VALIDITY_DAYS),
                        "-storetype", "PKCS12",
                        "-keystore", keystorePath.toString(),
                        "-storepass", keystorePassword,
                        "-keypass", keystorePassword,
                        "-noprompt");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        // 吞掉 stdout（避免日志噪音）
        try (var in = proc.getInputStream()) {
            byte[] buf = new byte[256];
            while (in.read(buf) > 0) {
                /* discard */
            }
        }
        boolean ok = proc.waitFor(30, TimeUnit.SECONDS);
        if (!ok) {
            proc.destroyForcibly();
            throw new IOException("keytool generation timed out after 30s");
        }
        if (proc.exitValue() != 0) {
            throw new IOException("keytool generation failed with exit code " + proc.exitValue());
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("[pwa-https] generated keystore at {} ({} ms)", keystorePath, elapsed);

        // 顺便导出 PEM（给 OpenSSL / 其他工具用）
        Path certPemPath = dir.resolve("cert.pem");
        exportPem(keystorePath, keystoreAlias, keystorePassword, certPemPath);
    }

    /**
     * 从 PKCS12 keystore 导出 PEM 格式的证书（给 NGINX / OpenSSL 用）。
     */
    private static void exportPem(
            Path keystorePath, String alias, String password, Path certPemPath)
            throws IOException, InterruptedException {
        ProcessBuilder pb =
                new ProcessBuilder(
                        "keytool",
                        "-exportcert",
                        "-alias", alias,
                        "-keystore", keystorePath.toString(),
                        "-storepass", password,
                        "-rfc",
                        "-file", certPemPath.toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        try (var in = proc.getInputStream()) {
            byte[] buf = new byte[256];
            while (in.read(buf) > 0) { /* discard */
            }
        }
        boolean ok = proc.waitFor(15, TimeUnit.SECONDS);
        if (ok && proc.exitValue() == 0) {
            log.info("[pwa-https] exported PEM cert to {}", certPemPath);
        } else {
            log.warn("[pwa-https] PEM export failed (non-fatal, PKCS12 still available)");
        }
    }

    /** 提供给 WebSecurityConfig 读取 keystore 文件路径。 */
    public Path keystorePath() {
        return Path.of(certDir, "keystore.p12");
    }

    public String keystorePassword() {
        return keystorePassword;
    }

    public String keystoreAlias() {
        return keystoreAlias;
    }

    /**
     * 验证生成的 keystore 可用（用于测试 / 健康检查）。
     */
    public X509Certificate loadCertificate() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(keystorePath())) {
            ks.load(in, keystorePassword().toCharArray());
        }
        Enumeration<String> aliases = ks.aliases();
        String alias = aliases.hasMoreElements() ? aliases.nextElement() : null;
        if (alias == null) throw new IllegalStateException("empty keystore");
        return (X509Certificate) ks.getCertificate(alias);
    }

    public X500Principal subjectPrincipal() throws Exception {
        return loadCertificate().getSubjectX500Principal();
    }
}
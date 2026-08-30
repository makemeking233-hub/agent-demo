package com.example.agent.web.e2e;

import io.github.bonigarcia.wdm.WebDriverManager;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Paths;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * M11 E2E 浏览器端测试基类。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>启动 Edge WebDriver（WebDriverManager 自动管理 msedgedriver）
 *   <li>校验 web 后端在 {@code webBaseUrl} 可访问（默认 {@code http://127.0.0.1:18080}）
 *   <li>提供共享 {@link WebDriverWait} 与 helper
 * </ul>
 *
 * <p><b>运行前置</b>：在跑 E2E 之前必须用以下命令启动 web 后端：
 *
 * <pre>
 *   DEEPSEEK_API_KEY=sk-xxx SPRING_PROFILES_ACTIVE=web \
 *     java -jar agent-web/target/agent-web.jar --server.port=18080
 * </pre>
 *
 * <p><b>为什么不自带启动</b>：mvn test 跑 surefire fork 在 Windows 上启停外部进程时不稳定；
 * 用户显式启动便于看后端日志（agent-web/target/e2e-stdout.log）。
 *
 * <p>关键 API：
 *
 * <ul>
 *   <li>{@link #driver} — Selenium WebDriver，所有测试共享
 *   <li>{@link #wait()} — 10s 默认等待（处理 SSE 流式渲染）
 *   <li>{@link #WEB_BASE} — web 后端 URL，从系统属性 {@code e2e.web.base} 读取
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class E2EBase {

    protected static final String WEB_BASE = System.getProperty("e2e.web.base", "http://127.0.0.1:18080");
    protected static final Duration DEFAULT_WAIT = Duration.ofSeconds(10);

    protected WebDriver driver;
    protected WebDriverWait wait;

    @BeforeAll
    void setUpWebDriver() throws Exception {
        // 1. 校验 web 后端可达；不可达（连接失败/非 200）时跳过 E2E。这些用例需要预先启动 jar 后端：
        //    java -jar agent-web/target/agent-web.jar --server.port=18080
        URL url = URI.create(WEB_BASE + "/api/health").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        int code;
        try {
            code = conn.getResponseCode();
        } catch (java.io.IOException e) {
            Assumptions.assumeTrue(
                    false,
                    "Web backend not reachable at " + WEB_BASE + ": " + e.getMessage()
                            + " — E2E skipped (requires pre-started jar backend on 18080).");
            return;
        }
        Assumptions.assumeTrue(
                code == 200,
                "Web backend not reachable at " + WEB_BASE + " (HTTP " + code + "). "
                        + "E2E skipped (requires pre-started jar backend on 18080).");

        // 2. WebDriverManager 自动下载匹配 Chrome 版本的 chromedriver（从 chrome-for-testing 源）
        WebDriverManager.chromedriver().setup();

        // 3. Chrome 配置：headless 默认 false（开发可见）；CI 环境设 E2E_HEADLESS=true
        ChromeOptions options = new ChromeOptions();
        if ("true".equalsIgnoreCase(System.getenv("E2E_HEADLESS"))) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--no-sandbox", "--disable-dev-shm-usage");
        options.addArguments("--window-size=1280,800");

        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, DEFAULT_WAIT);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));
    }

    @AfterAll
    void tearDownWebDriver() {
        if (driver != null) {
            driver.quit();
        }
    }

    /** 导航到首页并等首屏稳定。 */
    protected void navigateToHome() {
        driver.get(WEB_BASE + "/");
        // 等 React hydration：Composer 的 textarea 出现即为已渲染
        wait.until(d -> d.findElement(org.openqa.selenium.By.cssSelector("textarea")).isDisplayed());
    }

    /** 公共绝对路径工具（让测试断言文件型工具读到的绝对路径）。 */
    protected static String userHome() {
        return Paths.get(System.getProperty("user.home")).toString();
    }

    // ------------------------------------------------------------------
    // 通用 E2E helper（供各浏览器级用例复用）
    // ------------------------------------------------------------------

    /**
     * 在页面里执行 JS 并返回结果。
     *
     * @param script JavaScript 表达式
     * @return 执行结果
     */
    protected Object eval(String script) {
        return ((JavascriptExecutor) driver).executeScript(script);
    }

    /** 读取 localStorage 指定键的值（不存在返回 null）。 */
    protected String localStorage(String key) {
        return (String) eval("return localStorage.getItem('" + key + "');");
    }

    /** 写入 localStorage 指定键。 */
    protected void setLocalStorage(String key, String value) {
        eval("localStorage.setItem('" + key + "', '" + value + "');");
    }

    /** 读取当前主题：body.dataset.dsDarkTheme 为空串表示 dark，否则 light。 */
    protected String currentTheme() {
        return (String) eval("return document.body.dataset.dsDarkTheme === '' ? 'dark' : 'light';");
    }

    /** 等待某个 {@code data-testid} 元素出现且可交互。 */
    protected WebElement waitForTestId(String testId) {
        return wait.until(d -> d.findElement(By.cssSelector("[data-testid='" + testId + "']")));
    }

    /** 等待并返回匹配 CSS 选择器的首元素（可交互）。 */
    protected WebElement waitForCss(String css) {
        return wait.until(d -> d.findElement(By.cssSelector(css)));
    }

    /**
     * 填写 Composer 输入框。
     *
     * @param text 要输入的内容
     */
    protected void typeToComposer(String text) {
        WebElement textarea = waitForCss("textarea");
        textarea.clear();
        textarea.sendKeys(text);
    }

    /**
     * 定位并返回 Composer 的发送按钮（输入框 textarea 之后的 button）。
     *
     * <p>Composer 非 busy 时，{@code .row} 内只有发送按钮（无 abort），用 textarea 的下一兄弟
     * element 定位，避免依赖 CSS module 的 hash 类名。
     *
     * @return 发送按钮 WebElement
     */
    protected WebElement sendButton() {
        WebElement textarea = driver.findElement(By.tagName("textarea"));
        return textarea.findElement(By.xpath("./following-sibling::button"));
    }

    /**
     * 点击 Composer 发送按钮。
     *
     * @return 是否成功点击（非 disabled）
     */
    protected boolean clickSend() {
        WebElement btn = sendButton();
        boolean disabled = btn.getAttribute("disabled") != null;
        if (!disabled) btn.click();
        return !disabled;
    }

    /** 等待对话区出现文本（用于校验助手/用户消息渲染）。 */
    protected boolean waitForText(String text) {
        return wait.until(d -> d.getPageSource().contains(text));
    }
}
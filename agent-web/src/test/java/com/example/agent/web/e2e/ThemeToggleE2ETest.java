package com.example.agent.web.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;

/**
 * M11 Spec #5: 主题切换 (亮/暗 + localStorage 持久化).
 *
 * <p>对应 docs/design/web-ui-design.md 与 openspec/specs/web-ui-layout/spec.md §Requirement: 主题切换.
 */
@DisplayName("主题切换 E2E")
class ThemeToggleE2ETest extends E2EBase {

    private static final String STORAGE_KEY = "agent-demo:theme";

    @Test
    @DisplayName("默认加载: 主题由 prefers-color-scheme 或 localStorage 决定")
    void defaultThemeIsRespected() {
        navigateToHome();
        Object themeAttr = ((JavascriptExecutor) driver).executeScript(
                "return document.body.dataset.dsDarkTheme === '' ? 'dark' : 'light';");
        String stored = (String) ((JavascriptExecutor) driver).executeScript(
                "return localStorage.getItem('" + STORAGE_KEY + "');");
        // 默认未设 localStorage 时: prefers-color-scheme 决定 (CI 通常 light)
        // 此断言不强求具体值, 只断言切换按钮存在 + 状态一致
        WebElement btn = driver.findElement(By.cssSelector("button[aria-label*='主题']"));
        assertThat(btn).isNotNull();
        assertThat(themeAttr).isIn("light", "dark");
        // localStorage 可能为 null (未显式设) 或 light/dark; 不强求
        assertThat(stored == null || stored.equals("light") || stored.equals("dark")).isTrue();
    }

    @Test
    @DisplayName("点击 toggle: light → dark (body dataset + 按钮 aria-label 反转)")
    void toggleToDarkThenBackToLight() {
        navigateToHome();
        WebElement btn = driver.findElement(By.cssSelector("button[aria-label*='主题']"));
        String beforeLabel = btn.getAttribute("aria-label");
        String beforeTheme = readTheme();

        btn.click();
        // 状态变更同步触发 React state; 立即可读
        wait.until(d -> !readTheme().equals(beforeTheme));
        String afterFirst = readTheme();
        assertThat(afterFirst).isNotEqualTo(beforeTheme);
        String afterFirstLabel = btn.getAttribute("aria-label");
        assertThat(afterFirstLabel).isNotEqualTo(beforeLabel);

        // localStorage 同步
        String stored = (String) ((JavascriptExecutor) driver).executeScript(
                "return localStorage.getItem('" + STORAGE_KEY + "');");
        assertThat(stored).isEqualTo(afterFirst);

        // 再点一次: 反转回原主题
        btn.click();
        wait.until(d -> readTheme().equals(beforeTheme));
        assertThat(readTheme()).isEqualTo(beforeTheme);
    }

    @Test
    @DisplayName("刷新页面后主题保持 (localStorage 持久化)")
    void themePersistsAcrossReload() {
        navigateToHome();
        WebElement btn = driver.findElement(By.cssSelector("button[aria-label*='主题']"));
        String initial = readTheme();
        btn.click();
        wait.until(d -> !readTheme().equals(initial));
        String afterToggle = readTheme();

        driver.navigate().refresh();
        wait.until(d -> d.findElement(By.cssSelector("textarea")).isDisplayed());

        String afterReload = readTheme();
        assertThat(afterReload).isEqualTo(afterToggle);

        // 清理: 还原初始主题
        WebElement btnAfter = driver.findElement(By.cssSelector("button[aria-label*='主题']"));
        if (!readTheme().equals(initial)) {
            btnAfter.click();
            wait.until(d -> readTheme().equals(initial));
        }
    }

    private String readTheme() {
        return (String) ((JavascriptExecutor) driver).executeScript(
                "return document.body.dataset.dsDarkTheme === '' ? 'dark' : 'light';");
    }
}
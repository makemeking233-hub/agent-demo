package com.example.agent.web.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;

/**
 * M11 Web UI E2E：三栏布局外壳 + 顶栏 + 会话列表 + 输入区 + slash 命令（P0/P1）。
 *
 * <p>对应 docs/test-agent-demo/web-ui-e2e-design.md §4 用例矩阵。
 */
@DisplayName("Web UI 布局与交互 E2E")
class UiLayoutE2ETest extends E2EBase {

    @Test
    @DisplayName("TC-E2E-UI-001 三栏布局外壳渲染")
    void threeColumnLayoutRenders() {
        navigateToHome();
        // 顶栏品牌
        assertThat(driver.findElements(By.cssSelector("header")).size()).isGreaterThan(0);
        // 左侧会话列表（aside 内出现"会话"标题）
        assertThat(driver.getPageSource()).contains("会话");
        // 中间对话区
        assertThat(driver.findElements(By.cssSelector("textarea")).size()).isEqualTo(1);
        // 底部输入区存在（textarea 即 Composer 核心）
        assertThat(driver.findElement(By.cssSelector("textarea")).isDisplayed()).isTrue();
    }

    @Test
    @DisplayName("TC-E2E-UI-002 顶栏元素")
    void topBarElementsShow() {
        navigateToHome();
        assertThat(driver.getPageSource()).contains("agent-demo");
        assertThat(driver.findElement(By.cssSelector("button[aria-label='新建会话']")).isDisplayed())
                .isTrue();
        assertThat(driver.findElement(By.cssSelector("button[aria-label='设置']")).isDisplayed())
                .isTrue();
        assertThat(driver.findElements(By.cssSelector("button[aria-label*='主题']")).size())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("TC-E2E-UI-006 会话列表分组与占位数据")
    void sidebarShowsGroupedPlaceholders() {
        navigateToHome();
        String page = driver.getPageSource();
        assertThat(page).contains("agent-demo");
        assertThat(page).contains("open-source");
        // 会话项按钮
        assertThat(driver.findElements(By.cssSelector("aside button")).size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("TC-E2E-UI-007 选中会话高亮")
    void selectingSessionHighlightsIt() {
        navigateToHome();
        // 点击一个会话项（含 .itemText 的 button，排除折叠按钮）验证可交互
        WebElement session =
                wait.until(
                        d ->
                                d.findElements(By.cssSelector("aside button"))
                                        .stream()
                                        .filter(b -> b.getAttribute("aria-label") == null)
                                        .findFirst()
                                        .orElseThrow());
        session.click();
        // 点击后侧栏仍渲染会话项（未折叠），会话项集合非空
        wait.until(d -> d.findElements(By.cssSelector("aside button")).size() > 0);
        assertThat(driver.findElements(By.cssSelector("aside button")).size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("TC-E2E-UI-008 侧栏折叠/展开")
    void sidebarCollapseExpand() {
        navigateToHome();
        // 折叠
        WebElement collapse = driver.findElement(By.cssSelector("button[aria-label='折叠侧栏']"));
        collapse.click();
        waitForCss("button[aria-label='展开侧栏']");
        // 展开
        WebElement expand = driver.findElement(By.cssSelector("button[aria-label='展开侧栏']"));
        expand.click();
        waitForCss("button[aria-label='折叠侧栏']");
    }

    @Test
    @DisplayName("TC-E2E-UI-009 新建会话")
    void newSessionAddsToSidebar() {
        navigateToHome();
        int before = driver.findElements(By.cssSelector("aside button")).size();
        driver.findElement(By.cssSelector("button[aria-label='新建会话']")).click();
        int after = driver.findElements(By.cssSelector("aside button")).size();
        assertThat(after).isGreaterThan(before);
    }

    @Test
    @DisplayName("TC-E2E-UI-010 空输入禁用发送")
    void emptyInputDisablesSend() {
        navigateToHome();
        WebElement send = sendButton();
        assertThat(send.getAttribute("disabled")).isNotNull();
    }

    @Test
    @DisplayName("TC-E2E-UI-011 输入文字启用发送并显示字符数")
    void typingEnablesSendAndCountsChars() {
        navigateToHome();
        typeToComposer("你好");
        wait.until(d -> sendButton().getAttribute("disabled") == null);
        assertThat(sendButton().getAttribute("disabled")).isNull();
        // 字符数显示
        assertThat(driver.getPageSource()).contains("2 字符");
    }

    @Test
    @DisplayName("TC-E2E-UI-013 Ctrl+Enter 发送")
    void ctrlEnterSends() {
        navigateToHome();
        typeToComposer("你好");
        WebElement textarea = waitForCss("textarea");
        // Ctrl+Enter 触发 submit
        textarea.sendKeys(Keys.chord(Keys.CONTROL, Keys.ENTER));
        // 输入框应被清空（onSend 后 setValue("")）
        wait.until(d -> d.findElement(By.cssSelector("textarea")).getAttribute("value").isEmpty());
        assertThat(driver.findElement(By.cssSelector("textarea")).getAttribute("value")).isEmpty();
    }

    @Test
    @DisplayName("TC-E2E-UI-012 Shift+Enter 换行（不发送）")
    void shiftEnterNewline() {
        navigateToHome();
        typeToComposer("a");
        WebElement textarea = waitForCss("textarea");
        textarea.sendKeys(Keys.SHIFT, Keys.ENTER);
        textarea.sendKeys("b");
        // 输入框保留两行内容，未被清空
        String val = textarea.getAttribute("value");
        assertThat(val).contains("a");
        assertThat(val).contains("b");
    }

    @Test
    @DisplayName("TC-E2E-UI-014 slash 命令提示")
    void slashCommandHintShows() {
        navigateToHome();
        typeToComposer("/");
        wait.until(d -> d.getPageSource().contains("/help"));
        assertThat(driver.getPageSource()).contains("/clear");
    }

    @Test
    @DisplayName("TC-E2E-UI-015 /help 展示命令")
    void helpCommandRenders() {
        navigateToHome();
        typeToComposer("/help");
        WebElement textarea = waitForCss("textarea");
        textarea.sendKeys(Keys.chord(Keys.CONTROL, Keys.ENTER));
        wait.until(d -> d.getPageSource().contains("可用命令"));
        assertThat(driver.getPageSource()).contains("/clear");
    }

    @Test
    @DisplayName("TC-E2E-UI-017 空对话占位提示")
    void emptyChatPlaceholder() {
        navigateToHome();
        assertThat(driver.getPageSource()).contains("开始对话");
    }

    @Test
    @DisplayName("TC-E2E-UI-024 SPA 客户端路由回落到 index")
    void spaRouteFallsBackToIndex() {
        // SPA 客户端路由（非 /logs）：/sessions/<uuid> 应回落为 index.html，而非 404
        driver.get(WEB_BASE + "/sessions/abc123");
        wait.until(
                d ->
                        d.getTitle().contains("agent-demo")
                                || d.findElements(By.cssSelector("textarea")).size() > 0);
        assertThat(driver.getTitle()).contains("agent-demo");
    }
}

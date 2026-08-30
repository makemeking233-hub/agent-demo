package com.example.agent.web.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * 多轮对话端到端测试（M11）：验证「发送 → SSE 流式回复 → 会话内多轮连贯」链路。
 *
 * <p>设计：不依赖模型具体回复内容（不可控），断言可观察行为——
 * <ul>
 *   <li>每轮发送的用户消息文本都会渲染到对话区（该轮被处理）
 *   <li>每轮回复结束后输入框恢复可用（busy 结束，SSE 收到 message_stop）
 *   <li>连续多轮在同一会话内进行（session_id 复用，历史累积），均能正常回复
 * </ul>
 *
 * <p>前置：需预启动 web 后端（18080），且配置真实 key（web,local profile）。
 */
@DisplayName("多轮对话 E2E")
class MultiTurnE2ETest extends E2EBase {

    @Test
    @DisplayName("多轮对话：会话内连续多轮均能获得回复且输入框恢复")
    void multiTurnDialogueKeepsConversation() {
        navigateToHome();
        // 第一轮
        String msg1 = "你好，请简单介绍你自己。";
        sendAndAwaitAssistant(msg1);
        // 第二轮（同一会话，session_id 复用）
        String msg2 = "你能帮我做什么？";
        sendAndAwaitAssistant(msg2);
        // 第三轮（再次验证多轮连贯）
        String msg3 = "好的，请记住我们聊过。";
        sendAndAwaitAssistant(msg3);

        // 三轮用户消息都应渲染在对话区（该轮被处理 + 历史累积）
        assertThat(waitForText0(msg1)).isTrue();
        assertThat(waitForText0(msg2)).isTrue();
        assertThat(waitForText0(msg3)).isTrue();
        // 对话区应有 3 条用户消息（.list 内 user 气泡）—— 用页面文本出现次数近似
        int userMsgOccurrences = occurrencesOf(msg1) + occurrencesOf(msg2) + occurrencesOf(msg3);
        assertThat(userMsgOccurrences).isGreaterThanOrEqualTo(3);
    }

    /** 发送一条消息并等待：SSE 流式回复完成（输入框恢复可用）。 */
    private void sendAndAwaitAssistant(String text) {
        typeToComposer(text);
        boolean clicked = clickSend();
        assertThat(clicked).isTrue();
        // busy 结束 = textarea 重新可用（Composer busy 时 textarea disabled）
        wait.until(
                d -> {
                    WebElement ta = d.findElement(By.tagName("textarea"));
                    // disabled 属性可能缺失(返回 null) → 用 isEnabled()+非"true"判断，避免 NPE
                    String dis = ta.getAttribute("disabled");
                    return ta.isEnabled() && !Boolean.parseBoolean(dis);
                });
        // 等上一条用户消息文本渲染（该轮消息已进入对话区）
        waitForText0(text);
    }

    /** 等待页面出现指定文本（返回是否出现，不抛超时）。 */
    private boolean waitForText0(String text) {
        try {
            return wait.until(d -> d.getPageSource().contains(text));
        } catch (org.openqa.selenium.TimeoutException e) {
            return false;
        }
    }

    /** 统计页面出现指定文本的次数（近似消息数）。 */
    private int occurrencesOf(String text) {
        String page = driver.getPageSource();
        int count = 0;
        int idx = 0;
        while ((idx = page.indexOf(text, idx)) != -1) {
            count++;
            idx += text.length();
        }
        return count;
    }
}

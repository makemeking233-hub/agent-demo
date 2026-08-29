package com.example.agent.llm;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;

/**
 * 基于 JTokkit CL100K_BASE 编码的 token 估算器。
 *
 * <p>DeepSeek 未公开官方 tokenizer，CL100K_BASE 对 DeepSeek 中英文混合输入误差通常 &lt; 10%（可接受）。
 *
 * <p>v0.2 改进：校准系数（在 {@code DeepSeekProviderTest} 测真实 usage vs 估算值，偏差 &gt; 5% 时引入）。
 */
public class TokenEstimator {
    /**
     * CL100K_BASE 编码器（DeepSeek 中英文混合输入误差 &lt; 10%）
     */
    private final Encoding encoding =
            Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    /**
     * 估算单段文本的 token 数。
     *
     * @param text 待估算文本，{@code null} 或空串返回 0
     * @return token 数
     */
    public int estimate(String text) {
        if (text == null || text.isEmpty()) return 0;
        return encoding.countTokens(text);
    }
}

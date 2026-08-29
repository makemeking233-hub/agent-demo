package com.example.agent.log;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 敏感信息脱敏器：日志写出前的单点过滤（observability 设计 D3）。
 *
 * <p>规则覆盖常见 API key 格式（与 .gitleaks.toml 规则对齐），命中统一替换为
 * {@code ***REDACTED***}。集中在此保证 session.jsonl / chat.log / thinking.log /
 * tools.log 四条写路径一致，避免各埋点遗漏。
 */
public final class Redactor {

    /** 替换标记 */
    static final String REPLACEMENT = "***REDACTED***";

    /** 规则列表（顺序执行；全部是保守正则，避免误伤普通文本） */
    private static final List<Pattern> PATTERNS =
            List.of(
                    // sk- 前缀 API key（DeepSeek / OpenAI / Anthropic 风格）
                    Pattern.compile("\\bsk-(?:proj-|ant-|or-|live-)?[A-Za-z0-9_-]{16,}\\b"),
                    // Bearer token（JWT / opaque）
                    Pattern.compile("\\bBearer\\s+[A-Za-z0-9._~+/-]+=*\\b"),
                    // apiKey / api_key / api-key 键值对
                    Pattern.compile(
                            "(?i)\\bapi[_-]?key\\s*[:=]\\s*[\"']?[A-Za-z0-9._-]{8,}"));

    /**
     * 对文本做脱敏替换。
     *
     * @param s 原始文本（可空）
     * @return 脱敏后的文本；{@code null} 原样返回
     */
    public static String redact(String s) {
        if (s == null || s.isEmpty()) return s;
        String out = s;
        for (Pattern p : PATTERNS) {
            out = p.matcher(out).replaceAll(REPLACEMENT);
        }
        return out;
    }

    private Redactor() {}
}

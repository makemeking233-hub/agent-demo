package com.example.agent.memory.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * BERT WordPiece tokenizer（中文版，add-embedding-rag T8b）。
 *
 * <p>bge-small-zh-v1.5 是 BERT 架构：ONNX 模型只做「token ids → 向量」的推理，
 * 「文本 → token ids」由本类完成。算法对齐 HuggingFace {@code BertTokenizer}：
 *
 * <ol>
 *   <li><b>BasicTokenizer</b>：清理控制字符 → 空白归一化 → CJK 逐字切分 → 标点两侧切分 →
 *       （可选）小写化 → （可选）去重音
 *   <li><b>WordPieceTokenizer</b>：对每个词做贪心最长匹配，未匹配部分加 {@code ##} 前缀继续匹配；
 *       整词无法切分 → {@code [UNK]}
 *   <li>首尾包裹 {@code [CLS]} / {@code [SEP]}；按 {@code maxLength} 截断；可选补 {@code [PAD]}
 * </ol>
 *
 * <p><b>纯函数</b>：除构造时读一次词表外无状态，可安全并发使用（{@code encode} 无副作用）。
 *
 * <p><b>未就绪行为</b>：词表缺失 / 读取失败时 {@link #isReady()}=false，{@code encode} 返回
 * 仅含 {@code [CLS] [SEP]} 的结构（不抛异常）——与项目既有的降级原则一致。
 */
public class BertWordPieceTokenizer {

    private static final Logger log = LoggerFactory.getLogger(BertWordPieceTokenizer.class);

    /** BERT 默认最大序列长度。 */
    public static final int DEFAULT_MAX_LENGTH = 512;

    /** WordPiece 子词前缀。 */
    private static final String CONTINUATION_PREFIX = "##";

    /** 单次 encode 允许的字符数上限（防止超长输入把 tokenizer 拖死）。 */
    private static final int MAX_INPUT_CHARS = 20_000;

    private final Path vocabPath;
    private final int maxLength;
    private final boolean doLowerCase;
    private final boolean padToMaxLength;

    /** token -> id。 */
    private Map<String, Integer> vocab = Map.of();
    /** id -> token（未用，但保留便于调试/断言）。 */
    private List<String> idToToken = List.of();

    private final int clsId;
    private final int sepId;
    private final int unkId;
    private final int padId;
    private final boolean ready;

    /**
     * 构造 tokenizer。
     *
     * @param vocabPath      vocab.txt 路径（每行一个 token，行号即 id）
     * @param maxLength      序列长度上限（含 [CLS] / [SEP]）
     * @param doLowerCase    是否小写化（bge-small-zh 为 true）
     * @param padToMaxLength 是否补齐到 maxLength（ONNX 固定形状输入时为 true）
     */
    public BertWordPieceTokenizer(
            Path vocabPath, int maxLength, boolean doLowerCase, boolean padToMaxLength) {
        this.vocabPath = vocabPath;
        this.maxLength = maxLength > 2 ? maxLength : DEFAULT_MAX_LENGTH;
        this.doLowerCase = doLowerCase;
        this.padToMaxLength = padToMaxLength;

        Map<String, Integer> loaded = new HashMap<>();
        List<String> tokens = new ArrayList<>();
        boolean ok = false;
        try {
            if (vocabPath != null && Files.isRegularFile(vocabPath)) {
                List<String> lines = Files.readAllLines(vocabPath, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String t = lines.get(i);
                    loaded.put(t, i);
                    tokens.add(t);
                }
                ok = true;
            } else {
                log.warn("vocab.txt not found at {}; tokenizer stays unavailable", vocabPath);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("cannot read vocab.txt at {}: {}", vocabPath, e.toString());
        }
        this.vocab = loaded;
        this.idToToken = tokens;
        this.ready = ok;
        this.padId = loaded.getOrDefault("[PAD]", 0);
        this.unkId = loaded.getOrDefault("[UNK]", 0);
        this.clsId = loaded.getOrDefault("[CLS]", 0);
        this.sepId = loaded.getOrDefault("[SEP]", 0);
        if (ok && (loaded.containsKey("[CLS]") == false || loaded.containsKey("[SEP]") == false)) {
            log.warn("vocab.txt at {} lacks [CLS]/[SEP]; tokenization will be degraded", vocabPath);
        }
    }

    /** 词表是否可用。 */
    public boolean isReady() {
        return ready;
    }

    /** 词表大小（行数）。 */
    public int vocabSize() {
        return idToToken.size();
    }

    public int clsId() {
        return clsId;
    }

    public int sepId() {
        return sepId;
    }

    public int unkId() {
        return unkId;
    }

    public int padId() {
        return padId;
    }

    /** 序列长度上限。 */
    public int maxLength() {
        return maxLength;
    }

    /**
     * 文本 → token ids（含 [CLS] / [SEP]，按需截断或补齐）。
     *
     * @param text 输入文本（{@code null} / 空白安全）
     * @return token id 数组；未就绪时返回 {@code [CLS] [SEP]}
     */
    public long[] encode(String text) {
        List<Integer> ids = encodeToList(text);
        return toLongArray(ids);
    }

    /**
     * 文本 → attention mask（1 = 真实 token，0 = padding）。
     *
     * @param text 输入文本
     * @return 与 {@link #encode(String)} 等长的 mask
     */
    public long[] attentionMask(String text) {
        List<Integer> ids = encodeToList(text);
        long[] mask = new long[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            mask[i] = ids.get(i) == padId ? 0L : 1L;
        }
        return mask;
    }

    /**
     * 生成长度为 {@code length} 的全零 token_type_ids（单句输入恒为 0）。
     *
     * @param length 长度
     * @return 全零数组
     */
    public long[] tokenTypeIds(int length) {
        return new long[Math.max(0, length)];
    }

    // ---- BasicTokenizer ----

    /** 内部：文本 → id 列表（含截断/补齐）。 */
    private List<Integer> encodeToList(String text) {
        List<String> basicTokens = basicTokenize(text);
        List<Integer> ids = new ArrayList<>();
        ids.add(clsId);
        for (String token : basicTokens) {
            if (ids.size() >= maxLength - 1) break;  // 预留 [SEP]
            wordPiece(token, ids);
        }
        ids.add(sepId);
        if (ids.size() > maxLength) {
            ids = new ArrayList<>(ids.subList(0, maxLength - 1));
            ids.add(sepId);
        }
        if (padToMaxLength) {
            while (ids.size() < maxLength) {
                ids.add(padId);
            }
        }
        return ids;
    }

    /**
     * BasicTokenizer：清理 + 空白归一化 + CJK 逐字切分 + 标点切分 + 小写 + 去重音。
     *
     * @param text 原始文本
     * @return 词序列
     */
    List<String> basicTokenize(String text) {
        if (text == null) return List.of();
        String s = text;
        if (s.length() > MAX_INPUT_CHARS) {
            s = s.substring(0, MAX_INPUT_CHARS);
        }
        s = cleanText(s);
        // 中文按字切分
        s = tokenizeChineseChars(s);
        // 标点两侧加空格
        s = splitOnPunctuation(s);
        // 按空白切分
        List<String> tokens = new ArrayList<>();
        for (String t : s.trim().split("\\s+")) {
            if (t.isEmpty()) continue;
            tokens.add(t);
        }
        // 小写 + 去重音
        List<String> out = new ArrayList<>(tokens.size());
        for (String t : tokens) {
            out.add(normalizeToken(t));
        }
        return out;
    }

    /** 去掉控制字符并把各类空白归一化为单空格。 */
    private static String cleanText(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == 0 || cp == 0xFFFD) continue;
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                sb.append(' ');
                continue;
            }
            if (Character.getType(cp) == Character.CONTROL) continue;
            sb.appendCodePoint(cp);
        }
        return sb.toString();
    }

    /** 在 CJK 字符两侧加空格，使后续按空白切分时中文逐字成为独立 token。 */
    private static String tokenizeChineseChars(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (isCjk(cp)) {
                sb.append(' ').appendCodePoint(cp).append(' ');
            } else {
                sb.appendCodePoint(cp);
            }
        }
        return sb.toString();
    }

    /** 在标点两侧加空格（BERT 的 punctuation 处理）。 */
    private static String splitOnPunctuation(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (isPunctuation(cp)) {
                sb.append(' ').appendCodePoint(cp).append(' ');
            } else {
                sb.appendCodePoint(cp);
            }
        }
        return sb.toString();
    }

    /** 小写化 + 去重音（NFD 去掉 combining marks 后重新组合）。 */
    private String normalizeToken(String token) {
        String t = token;
        if (doLowerCase) {
            t = t.toLowerCase(Locale.ROOT);
        }
        // 去重音（BERT 默认 strip_accents 跟随 do_lower_case）
        if (doLowerCase && !isAsciiOnly(t) && t.codePoints().anyMatch(BertWordPieceTokenizer::isAccentMark)) {
            String decomposed = Normalizer.normalize(t, Normalizer.Form.NFD);
            StringBuilder sb = new StringBuilder(decomposed.length());
            decomposed.codePoints()
                    .filter(cp -> !isAccentMark(cp))
                    .forEach(sb::appendCodePoint);
            t = Normalizer.normalize(sb.toString(), Normalizer.Form.NFC);
        }
        return t;
    }

    private static boolean isAsciiOnly(String s) {
        return s.chars().allMatch(c -> c < 128);
    }

    /** 判断某个码点是否为组合用附加符号（重音）。 */
    private static boolean isAccentMark(int cp) {
        return switch (Character.getType(cp)) {
            case Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true;
            default -> false;
        };
    }

    /** CJK 统一表意文字 + 扩展区 + 常见东亚标点所在区段（简化判定）。 */
    private static boolean isCjk(int cp) {
        return (cp >= 0x4E00 && cp <= 0x9FFF)      // CJK 统一表意
                || (cp >= 0x3400 && cp <= 0x4DBF)  // 扩展 A
                || (cp >= 0xF900 && cp <= 0xFAFF)  // 兼容表意
                || (cp >= 0x3040 && cp <= 0x30FF)  // 日文假名（bge 中文版同规则处理）
                || (cp >= 0xAC00 && cp <= 0xD7AF); // 韩文音节
    }

    /** 标点判定：ASCII 标点 + Unicode 标点类（含全角标点）。 */
    private static boolean isPunctuation(int cp) {
        if ((cp >= 33 && cp <= 47) || (cp >= 58 && cp <= 64) || (cp >= 91 && cp <= 96) || (cp >= 123 && cp <= 126)) {
            return true;
        }
        int type = Character.getType(cp);
        return type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION;
    }

    // ---- WordPieceTokenizer ----

    /**
     * WordPiece 贪心最长匹配：从位置 0 起找词表中最长的前缀；未匹配则回退一位继续；
     * 首段失败 → 整词 {@code [UNK]}；后续段失败 → 整个词回退为 {@code [UNK]} 并丢弃已产出子词。
     *
     * @param token 待切分的词
     * @param out   输出 id 列表（会追加）
     */
    private void wordPiece(String token, List<Integer> out) {
        if (token.isEmpty()) return;
        if (!ready) return;  // 未就绪时不产出任何子词
        int len = token.length();
        if (len > 100) {
            // BERT 对超长「词」直接判 UNK（防止 O(n²) 退化）
            out.add(unkId);
            return;
        }
        List<Integer> subIds = new ArrayList<>();
        int start = 0;
        while (start < len) {
            int end = len;
            Integer curId = null;
            while (start < end) {
                String piece = token.substring(start, end);
                if (start > 0) piece = CONTINUATION_PREFIX + piece;
                Integer id = vocab.get(piece);
                if (id != null) {
                    curId = id;
                    break;
                }
                end--;
            }
            if (curId == null) {
                // 整词判 UNK（丢弃已匹配的子词，与 HF 行为一致）
                out.add(unkId);
                return;
            }
            subIds.add(curId);
            start = end;
        }
        out.addAll(subIds);
    }

    private static long[] toLongArray(List<Integer> ids) {
        long[] arr = new long[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            arr[i] = ids.get(i);
        }
        return arr;
    }
}

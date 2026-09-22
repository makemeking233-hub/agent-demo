package com.example.agent.memory.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * BertWordPieceTokenizer 边界分支测试（add-embedding-rag T10）。
 *
 * <p>本类专门补齐主测试（{@link BertWordPieceTokenizerTest}）未覆盖的分支，使
 * {@code com.example.agent.memory.embedding} 包满足 jacoco `PACKAGE` 逐包门禁
 *（LINE ≥ 0.80 / BRANCH ≥ 0.70）。补的分支包括：超长输入截断、控制字符清理、
 * CJK 各区间、Unicode 标点类别、去重音、超长 token、未就绪早返回。
 */
class BertWordPieceTokenizerEdgeTest {

    @TempDir Path tmp;

    /** 含 CJK 区间、标点、重音字符、特殊 token 的词表。 */
    private Path vocab() throws Exception {
        Path v = tmp.resolve("vocab.txt");
        Files.writeString(
                v,
                String.join(
                        "\n",
                        "[PAD]",
                        "[UNK]",
                        "[CLS]",
                        "[SEP]",
                        "a",
                        "b",
                        "##c",
                        "安",
                        "あ",
                        "한",
                        "，",
                        "。",
                        "、",
                        "！",
                        "？",
                        "café",
                        "cafe",
                        "\u0000"));
        return v;
    }

    private BertWordPieceTokenizer tk(Path vocab) {
        return new BertWordPieceTokenizer(vocab, 64, true, false);
    }

    // ---- 超长输入 ----

    @Test
    void oversizedInputIsTruncated() throws Exception {
        var tokenizer = tk(vocab());
        // 超过 MAX_INPUT_CHARS（20000）的输入应被截断而非 OOM/超时
        String huge = "a".repeat(30_000);
        long[] ids = tokenizer.encode(huge);
        assertTrue(ids.length > 0);
        assertTrue(ids.length <= 64, "结果仍受 maxLength 约束");
    }

    // ---- 控制字符与替换字符 ----

    @Test
    void controlCharactersAreStripped() throws Exception {
        var tokenizer = tk(vocab());
        // 含 NUL / BEL / ESC 等控制字符
        long[] withControl = tokenizer.encode("a\u0000\u0007\u001b b");
        long[] clean = tokenizer.encode("a b");
        assertEquals(clean.length, withControl.length, "控制字符应被清理后等长");
    }

    @Test
    void replacementCharIsStripped() throws Exception {
        var tokenizer = tk(vocab());
        long[] withReplacement = tokenizer.encode("a\uFFFD b");
        long[] clean = tokenizer.encode("a b");
        assertEquals(clean.length, withReplacement.length, "U+FFFD 应被清理");
    }

    @Test
    void variousWhitespaceNormalized() throws Exception {
        var tokenizer = tk(vocab());
        long[] tabs = tokenizer.encode("a\tb");
        long[] spaces = tokenizer.encode("a b");
        long[] newlines = tokenizer.encode("a\n\nb");
        assertEquals(spaces.length, tabs.length);
        assertEquals(spaces.length, newlines.length);
    }

    // ---- CJK 各区间 ----

    @Test
    void japaneseKanaIsSplitPerChar() throws Exception {
        var tokenizer = tk(vocab());
        long[] ids = tokenizer.encode("ああ");
        // [CLS] あ あ [SEP]
        assertEquals(4, ids.length, "日文假名应逐字切分");
    }

    @Test
    void koreanHangulIsSplitPerChar() throws Exception {
        var tokenizer = tk(vocab());
        long[] ids = tokenizer.encode("한한");
        assertEquals(4, ids.length, "韩文音节应逐字切分");
    }

    @Test
    void cjkCompatibilityIdeographsSplit() throws Exception {
        var tokenizer = tk(vocab());
        // U+F900 区间（兼容表意）
        long[] ids = tokenizer.encode("\uF900\uF901");
        assertEquals(4, ids.length, "兼容表意区应逐字切分");
    }

    // ---- 标点类别 ----

    @Test
    void fullWidthPunctuationSplit() throws Exception {
        var tokenizer = tk(vocab());
        long[] comma = tokenizer.encode("a，b");
        long[] period = tokenizer.encode("a。b");
        long[] exclaim = tokenizer.encode("a！b");
        assertEquals(5, comma.length, "全角逗号应独立成 token");
        assertEquals(5, period.length);
        assertEquals(5, exclaim.length);
    }

    @Test
    void asciiPunctuationSplit() throws Exception {
        var tokenizer = tk(vocab());
        long[] ids = tokenizer.encode("a-b");
        assertTrue(ids.length >= 4, "ASCII 标点应被切出");
    }

    // ---- 去重音 ----

    @Test
    void accentedWordHandled() throws Exception {
        var tokenizer = tk(vocab());
        // café：词表里同时有 cafe 与 café；lowercase+去重音后应能匹配其一
        long[] ids = tokenizer.encode("CAFÉ");
        assertTrue(ids.length >= 2, "大写重音词应被处理");
        // 不应整词变 UNK（词表含 cafe / café 之一）
        assertFalse(ids.length == 3 && ids[1] == 1, "不应退化为单个 [UNK]");
    }

    // ---- 超长 token ----

    @Test
    void overlongTokenBecomesUnk() throws Exception {
        var tokenizer = tk(vocab());
        // 单 token 超过 100 字符 → 直接 [UNK]（防 O(n^2)）
        String overlong = "z".repeat(150);
        long[] ids = tokenizer.encode(overlong);
        assertEquals(3, ids.length);
        assertEquals(1, ids[1], "超长 token 应成为 [UNK]");
    }

    // ---- wordPiece 在未就绪时的早返回 ----

    @Test
    void unreadyTokenizerProducesOnlySpecialTokens() {
        // 词表不存在 → ready=false；wordPiece 应早返回，不产任何子词
        var tokenizer = new BertWordPieceTokenizer(
                tmp.resolve("missing-vocab.txt"), 16, true, false);
        assertFalse(tokenizer.isReady());
        long[] ids = tokenizer.encode("a b c");
        assertEquals(2, ids.length, "未就绪时仅 [CLS] [SEP]");
    }

    @Test
    void unreadyTokenizerVocabSizeZero() {
        var tokenizer = new BertWordPieceTokenizer(
                tmp.resolve("nope.txt"), 16, true, false);
        assertEquals(0, tokenizer.vocabSize());
        assertEquals(0, tokenizer.clsId());
        assertEquals(0, tokenizer.sepId());
        assertEquals(0, tokenizer.unkId());
        assertEquals(0, tokenizer.padId());
    }

    // ---- 空 token / 空输入边界 ----

    @Test
    void emptyAfterCleaningStillWraps() throws Exception {
        var tokenizer = tk(vocab());
        // 全是空白与控制字符 → 清理后为空
        long[] ids = tokenizer.encode("   \u0000\u0007   ");
        assertEquals(2, ids.length, "清理后为空时仍只有 [CLS] [SEP]");
    }

    @Test
    void maxLengthTooSmallFallsBackToDefault() throws Exception {
        // maxLength <= 2 时回落到 DEFAULT_MAX_LENGTH（512）
        var tokenizer = new BertWordPieceTokenizer(vocab(), 1, true, false);
        assertEquals(BertWordPieceTokenizer.DEFAULT_MAX_LENGTH, tokenizer.maxLength());
    }

    @Test
    void noPaddingModeProducesVariableLength() throws Exception {
        var tokenizer = new BertWordPieceTokenizer(vocab(), 32, true, false);
        long[] shortIds = tokenizer.encode("a");
        long[] longIds = tokenizer.encode("a b a b a b");
        assertTrue(longIds.length > shortIds.length, "不补齐时长度应随输入变化");
    }

    @Test
    void attentionMaskAllOnesWhenNoPadding() throws Exception {
        var tokenizer = new BertWordPieceTokenizer(vocab(), 32, true, false);
        long[] mask = tokenizer.attentionMask("a b");
        for (long m : mask) {
            assertEquals(1L, m, "不补齐时 mask 应全为 1");
        }
    }

    @Test
    void tokenTypeIdsZeroLengthForEmpty() throws Exception {
        var tokenizer = tk(vocab());
        assertEquals(0, tokenizer.tokenTypeIds(0).length);
        assertEquals(0, tokenizer.tokenTypeIds(-5).length, "负长度应被夹到 0");
    }

    @Test
    void nullVocabPathStaysUnready() {
        var tokenizer = new BertWordPieceTokenizer(null, 16, true, false);
        assertFalse(tokenizer.isReady());
        assertEquals(0, tokenizer.vocabSize());
    }
}

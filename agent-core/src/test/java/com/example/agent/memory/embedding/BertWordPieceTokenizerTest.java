package com.example.agent.memory.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * BertWordPieceTokenizer 单元测试（add-embedding-rag T8b）。
 *
 * <p>覆盖 BERT 中文分词的几条关键规则：特殊 token 包裹、CJK 逐字切分、WordPiece 贪心最长匹配、
 * 未登录词 → [UNK]、标点切分、truncate/padding。
 */
class BertWordPieceTokenizerTest {

    @TempDir Path tmp;

    /** 构造一个最小词表：覆盖常见 ASCII 词、中文单字、##子词、以及特殊 token。 */
    private Path minimalVocab() throws Exception {
        Path vocab = tmp.resolve("vocab.txt");
        Files.writeString(
                vocab,
                String.join(
                        "\n",
                        "[PAD]",
                        "[UNK]",
                        "[CLS]",
                        "[SEP]",
                        "hello",
                        "world",
                        "##s",
                        "##ing",
                        "play",
                        "安",
                        "装",
                        "java",
                        "j",
                        "##ava",
                        ",",
                        ".",
                        "。",
                        "！"));
        return vocab;
    }

    private static final int CLS = 2;
    private static final int SEP = 3;
    private static final int UNK = 1;
    private static final int PAD = 0;

    private BertWordPieceTokenizer tokenizer(Path vocab) {
        return new BertWordPieceTokenizer(vocab, /* maxLength */ 32, /* doLowerCase */ true, /* padToMaxLength */ false);
    }

    @Test
    void emptyTextStillWrapsWithSpecialTokens() throws Exception {
        var tk = tokenizer(minimalVocab());
        long[] ids = tk.encode("");
        // [CLS] [SEP]
        assertEquals(2, ids.length);
        assertEquals(CLS, ids[0]);
        assertEquals(SEP, ids[1]);
    }

    @Test
    void nullTextIsSafe() throws Exception {
        var tk = tokenizer(minimalVocab());
        long[] ids = tk.encode(null);
        assertNotNull(ids);
        assertEquals(CLS, ids[0]);
        assertEquals(SEP, ids[1]);
    }

    @Test
    void asciiWordIsSingleToken() throws Exception {
        var tk = tokenizer(minimalVocab());
        long[] ids = tk.encode("hello world");
        // [CLS] hello world [SEP]
        assertEquals(4, ids.length);
        assertEquals(CLS, ids[0]);
        assertEquals(4, ids[1], "hello 的 id 应为 4");
        assertEquals(5, ids[2], "world 的 id 应为 5");
        assertEquals(SEP, ids[3]);
    }

    @Test
    void chineseCharsAreSplitPerCharacter() throws Exception {
        var tk = tokenizer(minimalVocab());
        long[] ids = tk.encode("安装");
        // [CLS] 安 装 [SEP]
        assertEquals(4, ids.length);
        assertEquals(9, ids[1], "安 的 id 应为 9");
        assertEquals(10, ids[2], "装 的 id 应为 10");
    }

    @Test
    void wordPieceGreedyLongestMatch() throws Exception {
        var tk = tokenizer(minimalVocab());
        // "java" 在词表里是完整词（id 11），也拆成 j + ##ava
        long[] ids = tk.encode("java");
        assertEquals(3, ids.length);
        assertEquals(11, ids[1], "完整词优先于子词切分");

        // "playings" = play + ##ing + ##s
        long[] ids2 = tk.encode("playings");
        assertEquals(5, ids2.length, "[CLS] play ##ing ##s [SEP]");
        assertEquals(8, ids2[1], "play");
        assertEquals(7, ids2[2], "##ing");
        assertEquals(6, ids2[3], "##s");
    }

    @Test
    void unknownWordBecomesUnk() throws Exception {
        var tk = tokenizer(minimalVocab());
        long[] ids = tk.encode("zzzz");
        assertEquals(3, ids.length);
        assertEquals(UNK, ids[1], "完全无法切分的词应成为 [UNK]");
    }

    @Test
    void lowercaseApplied() throws Exception {
        var tk = tokenizer(minimalVocab());
        long[] idsLower = tk.encode("HELLO");
        long[] idsUpper = tk.encode("hello");
        assertEquals(idsUpper[1], idsLower[1], "doLowerCase=true 时 HELLO 与 hello 应同 id");
    }

    @Test
    void punctuationIsSplitAsToken() throws Exception {
        var tk = tokenizer(minimalVocab());
        long[] ids = tk.encode("hello,world");
        // [CLS] hello , world [SEP]（逗号被单独切出）
        assertEquals(5, ids.length);
        assertEquals(14, ids[2], "逗号的 id 应为 14");
    }

    @Test
    void truncatesToMaxLength() throws Exception {
        Path vocab = minimalVocab();
        var tk = new BertWordPieceTokenizer(vocab, /* maxLength */ 5, true, false);
        long[] ids = tk.encode("hello world hello world hello world");
        assertEquals(5, ids.length, "应截断到 maxLength");
        assertEquals(CLS, ids[0]);
        assertEquals(SEP, ids[4], "截断后末位仍是 [SEP]");
    }

    @Test
    void padsToMaxLengthWhenRequested() throws Exception {
        var tk = new BertWordPieceTokenizer(minimalVocab(), 8, true, /* padToMaxLength */ true);
        long[] ids = tk.encode("hello");
        assertEquals(8, ids.length);
        assertEquals(CLS, ids[0]);
        assertEquals(4, ids[1]);
        assertEquals(SEP, ids[2]);
        assertEquals(PAD, ids[3], "其余位置补 [PAD]=0");
    }

    @Test
    void attentionMaskMatchesNonPadPositions() throws Exception {
        var tk = new BertWordPieceTokenizer(minimalVocab(), 8, true, true);
        long[] mask = tk.attentionMask("hello");
        assertEquals(8, mask.length);
        assertEquals(1L, mask[0]);
        assertEquals(1L, mask[1]);
        assertEquals(1L, mask[2]);
        assertEquals(0L, mask[3], "padding 位置的 attention mask 应为 0");
    }

    @Test
    void tokenTypeIdsAreAllZero() throws Exception {
        var tk = tokenizer(minimalVocab());
        long[] types = tk.tokenTypeIds(4);
        assertEquals(4, types.length);
        for (long t : types) {
            assertEquals(0L, t, "单句输入的 token_type_ids 应全为 0");
        }
    }

    @Test
    void vocabMissingFallsBackToEmptyGracefully() {
        Path missing = tmp.resolve("no-such-vocab.txt");
        var tk = new BertWordPieceTokenizer(missing, 16, true, false);
        assertTrue(tk.isReady() == false, "词表缺失时应报告未就绪");
        long[] ids = tk.encode("hello");
        // 未就绪时仍返回合法结构：仅 [CLS] [SEP]
        assertEquals(2, ids.length);
    }

    @Test
    void vocabMetadataLoaded() throws Exception {
        var tk = tokenizer(minimalVocab());
        assertTrue(tk.isReady());
        assertEquals(18, tk.vocabSize(), "最小词表共 18 行");
        assertEquals(CLS, tk.clsId());
        assertEquals(SEP, tk.sepId());
        assertEquals(UNK, tk.unkId());
        assertEquals(PAD, tk.padId());
    }

    @Test
    void fullWidthPunctuationSplit() throws Exception {
        var tk = tokenizer(minimalVocab());
        long[] ids = tk.encode("安装。");
        // [CLS] 安 装 。 [SEP]
        assertEquals(5, ids.length);
        assertEquals(16, ids[3], "全角句号应在词表中（id 16）");
    }

    @Test
    void adjacentChineseAndAsciiSplit() throws Exception {
        var tk = tokenizer(minimalVocab());
        long[] ids = tk.encode("安装java");
        // [CLS] 安 装 java [SEP]
        assertEquals(5, ids.length);
        assertEquals(9, ids[1]);
        assertEquals(10, ids[2]);
        assertEquals(11, ids[3]);
    }

    @Test
    void whitespaceNormalized() throws Exception {
        var tk = tokenizer(minimalVocab());
        long[] a = tk.encode("hello   world");
        long[] b = tk.encode("hello world");
        assertEquals(b.length, a.length, "连续空白应归一化为单个分隔");
    }
}

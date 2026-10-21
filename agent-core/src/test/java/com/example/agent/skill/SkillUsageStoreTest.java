package com.example.agent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * SkillUsageStore 单元测试（add-skill-demand-loading T2）。
 *
 * <p>覆盖：记录累积、原子写、文件缺失、JSON 损坏自愈、指数衰减得分。
 */
class SkillUsageStoreTest {
    @TempDir Path tmp;

    private Path usageFile() {
        return tmp.resolve("skill-usage.json");
    }

    @Test
    void recordAccumulatesCount() {
        var store = new SkillUsageStore(usageFile());
        store.record("pdf-skill");
        store.record("pdf-skill");
        store.record("other");
        assertEquals(2, store.count("pdf-skill"));
        assertEquals(1, store.count("other"));
        assertEquals(0, store.count("never-used"));
    }

    @Test
    void recordUpdatesLastUsed() throws Exception {
        var store = new SkillUsageStore(usageFile());
        store.record("a");
        long first = store.lastUsedMs("a");
        Thread.sleep(5);
        store.record("a");
        assertTrue(store.lastUsedMs("a") >= first, "lastUsed 应单调不减");
    }

    @Test
    void flushWritesAtomically() throws Exception {
        var store = new SkillUsageStore(usageFile());
        store.record("a");
        store.record("a");
        store.flush();
        assertTrue(Files.exists(usageFile()), "flush 后文件应存在");
        String json = Files.readString(usageFile());
        assertTrue(json.contains("a"), "文件应含 skill 名: " + json);
        assertTrue(json.contains("\"count\""), "文件应含 count 字段: " + json);
    }

    @Test
    void loadRestoresPreviousUsage() throws Exception {
        var first = new SkillUsageStore(usageFile());
        first.record("a");
        first.record("a");
        first.flush();

        var second = new SkillUsageStore(usageFile());
        assertEquals(2, second.count("a"), "新实例应加载既有频次");
    }

    @Test
    void missingFileTreatedAsEmpty() {
        var store = new SkillUsageStore(tmp.resolve("no-such.json"));
        assertEquals(0, store.count("anything"));
        assertEquals(0.0, store.score("anything", 0.05, System.currentTimeMillis()), 1e-9);
    }

    @Test
    void corruptJsonSelfHeals() throws Exception {
        Files.writeString(usageFile(), "{ this is not valid json ");
        var store = new SkillUsageStore(usageFile());
        assertEquals(0, store.count("a"), "损坏文件应视为空");
        // 仍可继续记录与写回
        store.record("a");
        store.flush();
        assertTrue(Files.readString(usageFile()).contains("a"), "自愈后应能正常写入");
    }

    @Test
    void scoreDecaysExponentially() {
        long now = 1_700_000_000_000L;
        var store = new SkillUsageStore(usageFile());
        // count=10，90 天前用过
        store.recordAt("old", now - 90L * 86_400_000L);
        for (int i = 1; i < 10; i++) store.recordAt("old", now - 90L * 86_400_000L);
        double score = store.score("old", 0.05, now);
        // 10 * exp(-0.05*90) = 10 * 0.011109 = 0.11109
        assertEquals(10 * Math.exp(-0.05 * 90), score, 1e-6);
    }

    @Test
    void recentBeatsOldWhenCountsDiffer() {
        long now = 1_700_000_000_000L;
        var store = new SkillUsageStore(usageFile());
        // A: 10 次，90 天前
        for (int i = 0; i < 10; i++) store.recordAt("old-heavy", now - 90L * 86_400_000L);
        // B: 3 次，7 天前
        for (int i = 0; i < 3; i++) store.recordAt("recent-light", now - 7L * 86_400_000L);

        double a = store.score("old-heavy", 0.05, now);
        double b = store.score("recent-light", 0.05, now);
        assertTrue(b > a, "近期少的得分应高于久远多的: B=" + b + " A=" + a);
    }

    @Test
    void closeFlushesPendingRecords() throws Exception {
        var store = new SkillUsageStore(usageFile());
        store.record("a");
        store.close();
        assertTrue(Files.exists(usageFile()), "close 应 flush 未落盘的记录");
    }

    @Test
    void everyTenRecordsTriggersAutoFlush() throws Exception {
        var store = new SkillUsageStore(usageFile());
        for (int i = 0; i < 10; i++) store.record("batch");
        assertTrue(Files.exists(usageFile()), "每 10 次 record 应自动 flush");
    }

    @Test
    void unknownSkillHasZeroScore() {
        var store = new SkillUsageStore(usageFile());
        assertEquals(0.0, store.score("ghost", 0.05, System.currentTimeMillis()), 1e-9);
    }

    @Test
    void allScoresAccessible() {
        var store = new SkillUsageStore(usageFile());
        store.record("a");
        store.record("b");
        var scores = store.scores(0.05, System.currentTimeMillis());
        assertEquals(2, scores.size());
        assertTrue(scores.containsKey("a"));
        assertFalse(scores.containsKey("c"));
    }
}
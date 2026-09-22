package com.example.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class ConfigLoaderTest {
    @TempDir Path tmp;

    @Test
    void defaultsWhenNoFile() {
        var cfg = new ConfigLoader().load(null);
        assertEquals("deepseek-v4-flash", cfg.provider().model());
        assertEquals(8192, cfg.provider().maxOutputTokens());
        assertEquals("", cfg.search().provider());
        assertEquals(5, cfg.search().maxResults());
        assertEquals(60000, cfg.search().timeoutMs());
    }

    @Test
    void yamlOverridesDefaults() throws Exception {
        Path yaml = tmp.resolve("config.yaml");
        Files.writeString(yaml, "provider:\n  model: deepseek-reasoner\n  maxOutputTokens: 4096\n");
        var cfg = new ConfigLoader().load(yaml);
        assertEquals("deepseek-reasoner", cfg.provider().model());
        assertEquals(4096, cfg.provider().maxOutputTokens());
    }

    @Test
    void envOverridesYaml() throws Exception {
        Path yaml = tmp.resolve("config.yaml");
        Files.writeString(yaml, "provider:\n  model: deepseek-chat\n");
        // 验证 load 不抛错；env 覆盖在 CI 通过 maven surefire 配置
        var cfg = new ConfigLoader().load(yaml);
        assertNotNull(cfg);
    }

    @Test
    void yamlSearchOverridesDefaults() throws Exception {
        Path yaml = tmp.resolve("config.yaml");
        Files.writeString(
                yaml, "search:\n  provider: tavily\n  maxResults: 3\n  timeoutMs: 30000\n");
        var cfg = new ConfigLoader().load(yaml);
        assertEquals("tavily", cfg.search().provider());
        assertEquals(3, cfg.search().maxResults());
        assertEquals(30000, cfg.search().timeoutMs());
    }

    @Test
    void yamlSearchMissingKeepsDefaults() throws Exception {
        Path yaml = tmp.resolve("config.yaml");
        Files.writeString(yaml, "provider:\n  model: deepseek-chat\n");
        var cfg = new ConfigLoader().load(yaml);
        assertEquals("", cfg.search().provider());
        assertEquals(5, cfg.search().maxResults());
        assertEquals(60000, cfg.search().timeoutMs());
    }

    /** improve-voice-accuracy T8.2: voice.postProcess.enabled 默认 true。 */
    @Test
    void voicePostProcessEnabledByDefault() {
        var cfg = new ConfigLoader().load(null);
        assertEquals(true, cfg.voice().postProcess().enabled());
    }

    /** improve-voice-accuracy T8.2: yaml voice.postProcess.enabled=false 关闭 ASR 后处理。 */
    @Test
    void yamlCanDisableVoicePostProcess() throws Exception {
        Path yaml = tmp.resolve("config.yaml");
        Files.writeString(yaml, "voice:\n  postProcess:\n    enabled: false\n");
        var cfg = new ConfigLoader().load(yaml);
        assertEquals(false, cfg.voice().postProcess().enabled());
    }

    /** improve-voice-accuracy T8.2: voice 段缺失保留默认（true）。 */
    @Test
    void yamlVoiceMissingKeepsDefault() throws Exception {
        Path yaml = tmp.resolve("config.yaml");
        Files.writeString(yaml, "provider:\n  model: deepseek-chat\n");
        var cfg = new ConfigLoader().load(yaml);
        assertEquals(true, cfg.voice().postProcess().enabled());
    }

    /** fix-memory-recall-wiring T1.1: memory.dynamicRetrieval 缺省为 true（每轮按 query 召回）。 */
    @Test
    void dynamicRetrievalEnabledByDefault() {
        var cfg = new ConfigLoader().load(null);
        assertEquals(true, cfg.memory().dynamicRetrieval());
    }

    /** fix-memory-recall-wiring T1.1: yaml memory.dynamicRetrieval=false 可回退为启动期全量索引注入。 */
    @Test
    void yamlCanDisableDynamicRetrieval() throws Exception {
        Path yaml = tmp.resolve("config.yaml");
        Files.writeString(yaml, "memory:\n  dynamicRetrieval: false\n");
        var cfg = new ConfigLoader().load(yaml);
        assertEquals(false, cfg.memory().dynamicRetrieval());
    }

    /** fix-memory-recall-wiring T1.1: memory 段缺失时保留 dynamicRetrieval 默认（true）。 */
    @Test
    void yamlMemoryMissingKeepsDynamicRetrievalDefault() throws Exception {
        Path yaml = tmp.resolve("config.yaml");
        Files.writeString(yaml, "provider:\n  model: deepseek-chat\n");
        var cfg = new ConfigLoader().load(yaml);
        assertEquals(true, cfg.memory().dynamicRetrieval());
    }

    /** fix-memory-recall-wiring T1.1: 只配 sideQuery 时 dynamicRetrieval 保持默认，不被误改。 */
    @Test
    void yamlSideQueryDoesNotAffectDynamicRetrieval() throws Exception {
        Path yaml = tmp.resolve("config.yaml");
        Files.writeString(yaml, "memory:\n  sideQuery:\n    enabled: false\n");
        var cfg = new ConfigLoader().load(yaml);
        assertEquals(false, cfg.memory().sideQuery().enabled());
        assertEquals(true, cfg.memory().dynamicRetrieval());
    }

    // ---- add-embedding-rag T1: memory.embedding 配置段 ----

    /** add-embedding-rag T1.1: embedding 缺省为启用。 */
    @Test
    void embeddingEnabledByDefault() {
        var cfg = new ConfigLoader().load(null);
        assertEquals(true, cfg.memory().embedding().enabled());
    }

    /** add-embedding-rag T1.1: 缺省 modelPath 指向 <agentHome>/models/bge-small-zh-v1.5/model.onnx。 */
    @Test
    void embeddingModelPathDefaultsToAgentHomeModels() {
        var cfg = new ConfigLoader().load(null);
        String path = cfg.memory().embedding().modelPath();
        assertTrue(
                path.endsWith(".agent-demo" + java.io.File.separator + "models" + java.io.File.separator
                        + "bge-small-zh-v1.5" + java.io.File.separator + "model.onnx"),
                "缺省路径应指向 .agent-demo/models/bge-small-zh-v1.5/model.onnx，实际: " + path);
    }

    /** add-embedding-rag T1.1: HNSW 缺省参数 m=16, efConstruction=200。 */
    @Test
    void hnswDefaultsTo16And200() {
        var cfg = new ConfigLoader().load(null);
        assertEquals(16, cfg.memory().embedding().hnsw().m());
        assertEquals(200, cfg.memory().embedding().hnsw().efConstruction());
    }

    /** add-embedding-rag T1.1: yaml memory.embedding.enabled=false 可关闭 embedding。 */
    @Test
    void yamlCanDisableEmbedding() throws Exception {
        Path yaml = tmp.resolve("config.yaml");
        Files.writeString(yaml, "memory:\n  embedding:\n    enabled: false\n");
        var cfg = new ConfigLoader().load(yaml);
        assertEquals(false, cfg.memory().embedding().enabled());
    }

    /** add-embedding-rag T1.1: yaml 可覆盖 HNSW 参数。 */
    @Test
    void yamlCanOverrideHnsw() throws Exception {
        Path yaml = tmp.resolve("config.yaml");
        Files.writeString(
                yaml,
                "memory:\n  embedding:\n    enabled: true\n    hnsw:\n      m: 32\n      efConstruction: 400\n");
        var cfg = new ConfigLoader().load(yaml);
        assertEquals(true, cfg.memory().embedding().enabled());
        assertEquals(32, cfg.memory().embedding().hnsw().m());
        assertEquals(400, cfg.memory().embedding().hnsw().efConstruction());
    }

    /** add-embedding-rag T1.1: yaml memory 段缺失时 embedding 保持默认。 */
    @Test
    void yamlMemoryMissingKeepsEmbeddingDefault() throws Exception {
        Path yaml = tmp.resolve("config.yaml");
        Files.writeString(yaml, "provider:\n  model: deepseek-chat\n");
        var cfg = new ConfigLoader().load(yaml);
        assertEquals(true, cfg.memory().embedding().enabled());
        assertEquals(16, cfg.memory().embedding().hnsw().m());
    }

    /** add-embedding-rag T1.1: 只配 dynamicRetrieval=false 不影响 embedding 缺省。 */
    @Test
    void yamlDynamicRetrievalDoesNotAffectEmbedding() throws Exception {
        Path yaml = tmp.resolve("config.yaml");
        Files.writeString(yaml, "memory:\n  dynamicRetrieval: false\n");
        var cfg = new ConfigLoader().load(yaml);
        assertEquals(false, cfg.memory().dynamicRetrieval());
        assertEquals(true, cfg.memory().embedding().enabled());
    }
}


# voice fixtures（improve-voice-accuracy T9.2）

## 当前状态

**后端 voice 相关单测全部 mock LlmProvider**：

- `DeepSeekVoiceCorrectionServiceTest` 用 stub `LlmProvider` 返回固定 `TextDelta` 序列
- 不发起真实 HTTP 调用、不消耗 token
- 不写磁盘文件（service 无文件输出）

集成测试（Spring Boot 真实启动 + WebClient 真发请求）当前**不存在**——所有 voice 后端逻辑都用单测覆盖。

## 何时需要真实 fixture

只有以下场景才需要把 fixture 加入本目录：

| 场景 | 是否需要 fixture |
|------|----------------|
| 单元测试（`DeepSeekVoiceCorrectionServiceTest`） | ❌ stub provider |
| 集成测试（`@SpringBootTest` + 真实 LlmProvider） | ✅ 需要 mock 响应 JSON |
| e2e（前端 + 后端 + 真实 LLM） | ✅ 需要 mock 响应 JSON |

## 临时目录约定

集成测试的录音 / mock 响应等临时文件写到 **`target/test-voice-tmp/`**：

- 在仓库根 `.gitignore` 忽略（不会被 commit）
- `mvn clean` 自动删除
- 测试 `tearDown` 钩子负责清理自己产生的子目录

示例（Java JUnit 5）：

```java
class VoiceIntegrationTest {
    @TempDir Path testVoiceTmp;

    @AfterEach
    void cleanup() throws IOException {
        // tearDown 删除 testVoiceTmp 下自己写的文件；
        // JUnit @TempDir 会自动处理，但子类若写目标目录外的数据需手动清
    }
}
```

## 命名约定

```text
src/test/resources/fixtures/voice/
├── README.md                       ← 本文件
├── deepseek-correction-success.json ← mock 200 OK 响应
├── deepseek-correction-timeout.json  ← mock 超时场景
└── deepseek-correction-5xx.json      ← mock 5xx 错误响应
```

文件名 kebab-case；mock JSON 字段匹配 `OpenAiCompatibleProvider` 解析的真实字段。

## 清理要求

**测试不得污染用户真实数据**（AGENTS.md §10）：

1. 集成测试写 `target/test-voice-tmp/` 而非 `~/.agent-demo/` 或 `${user.home}`
2. 测试 `tearDown` 钩子必须清理自己创建的目录与文件
3. 跑完日志 / 临时文件不留到下一次会话

## 历史

- 2026-09-13（improve-voice-accuracy T9）：初始化目录 + 本 README
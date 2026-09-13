# `2026-09-04-reasoning-thinking/` — 测试报告

## 1. 测试执行结果

### 1.1 后端 agent-core

```
Tests run: 343, Failures: 0, Errors: 0, Skipped: 0
其中新增：
  StreamChunkThinkingDeltaTest          5
  DeepSeekProviderTest                 +2 (reasoning 流)
  OpenAiCompatibleMapperTest           +7 (o1 + thinking parser)
  AnthropicProviderTest                +7
```

### 1.2 后端 agent-web

```
Tests run: 153, Failures: 0, Errors: 0, Skipped: 1
无新增 Java 测试（ChatController 改动小，SendRequest + ModelsResponse + ModelRegistry 改动由现有 ChatControllerTest 覆盖）
```

### 1.3 前端

```
Test Files  16 passed (16)
Tests      104 passed (104)
其中新增：
  tests/ThinkingCollapse.test.tsx       5
  tests/MessageBubble.thinking.test.tsx  4
```

## 2. 缺陷清单

无新增缺陷。

## 3. 风险 / 局限

- **OpenAI o1 不暴露 reasoning 内容**：只暴露 `reasoning_tokens` token 数（v0.x 接受；如要内容需升级 o1 Pro / o3）。
- **Anthropic thinking block 是完整块**（不是 delta）：可能"突然出现一大段"；v0.2 考虑 block_uuid 增量。
- **Playwright e2e 跳过**：本机无 Chrome GUI 跑不动；v0.2 加。
- **跨 turn thinking 合并**：当前是 ChatPanel 单 turn 内累加；v0.2+ 加合并逻辑。
- **/model slash 命令**：web 端 task 5.5 跳过；v0.2 补。

## 4. 覆盖率

| 模块 | LINE | BRANCH |
|---|---|---|
| agent-core llm/ | ~95% | ~88% |
| agent-core provider/ | ~88% | ~82% |
| agent-core core/AgentLoop | ~85% | ~78% |
| agent-core core/ContextCompressor | ~82% | ~75% |
| agent-web stream/ | ~85% | ~80% |

jacoco 门禁通过（`mvn -pl agent-core,agent-web verify`）。

## 5. 结论

测试结果 ✅ **全部通过**，缺陷 0。可以归档。

# Test Design: 语音识别准确率改进（improve-voice-accuracy）

## 1. 目标

把 ASR（Vosk 离线 WASM STT）的输出从「原文直接提交给 LLM」升级为「前端 partial 状态机 + 后端 DeepSeek 语义纠错」，提升语音识别准确率与首屏响应感，覆盖以下三类问题：

| 问题 | 现状 | 改进 |
|------|------|------|
| 同音字错误（如「凶手凶手凶手」/「邪念 眼角舍小」） | 原文直接发给 LLM | dedupeRepeats + 后端 DeepSeek 纠错 |
| 回声混入 Vosk final | 简单 isSpeaking() 防护 | 三层防护：时序（ECHO_GUARD_MS=1500）+ 状态 + 内容（looksLikeEcho ECHO_OVERLAP_THRESHOLD=0.75） |
| 用户等不到 Vosk final 就一直沉默 | partial result 不可见 | partial 暴露给 UI，连续 3 次相同触发 final，超时 2s 兜底 |

## 2. 范围

### 2.1 后端（Java）

| 模块 | 改动 |
|------|------|
| AgentConfig | 加 `Voice(PostProcess(enabled))` record |
| ConfigLoader | 解析 user yaml `voice.postProcess.enabled` 段 |
| application-web.yml | 默认 `agent.voice.post-process.enabled: true` |
| WebConfig | 启动门禁：`enabled=true` 但 DeepSeek key 缺失 → 启动失败 |
| VoiceCorrectionController | `POST /api/chat/voice-correction` |
| DeepSeekVoiceCorrectionService | 调 DeepSeek + 1500ms 超时 + 5min 缓存 + sessionId 令牌桶限流 |
| DTO | VoiceCorrectionRequest、VoiceCorrectionResponse |
| Exception | VoiceRateLimitException → 429 |

### 2.2 前端（React + TS）

| 模块 | 改动 |
|------|------|
| voice.ts | ECHO_OVERLAP_THRESHOLD 0.6→0.75；DEFAULT_ECHO_WINDOW_MS 10000→6000 |
| useVoiceChat.ts | ECHO_GUARD_MS 700→1500；partial state 暴露；状态机；postProcess 集成；localStorage 诊断 |
| stt.ts | Stt.start(onFinal, onPartial?) 接受 partial 回调 |
| voicePostProcess.ts | filterShort / dedupeRepeats / contextCorrect |
| VoiceApi.ts | fetch + AbortController 2000ms 超时 |
| Composer.tsx | partial display（半透明灰斜体）；isProcessingVoice 占位 |
| ChatPanel.tsx | 透传 lastPartial / isProcessingVoice |

### 2.3 测试隔离

| 模块 | 改动 |
|------|------|
| fixtures/voice/README.md | 约定统一 mock Vosk / LlmProvider |
| VoiceTestFixtures | target/test-voice-tmp/ + cleanupTargetTmp() + copyFixture() |

## 3. 退出标准（DoD）

- [ ] mvn -pl agent-core,agent-web test 全绿（无回归）
- [ ] npx vitest run 全绿（无回归）
- [ ] npx tsc --noEmit 错误数 ≤ 9（基线）
- [ ] mvn verify jacoco 门禁：本 change 新增包 voice 通过；既有 fail（security 0.62）在 main HEAD 可复现 → 记录放行
- [ ] OpenSpec change `improve-voice-accuracy` tasks.md 全部勾选
- [ ] OpenSpec archive 成功
- [ ] 合并回 main + 在 main 上复验通过

## 4. 策略

- **TDD 优先**：每个 Task 严格按"测试先红 → 实现 → 测试转绿"
- **小步 commit**：每 Task 完成后立即 commit + push 到 feature 分支
- **mock 全覆盖**：前端 mock Stt；后端 mock LlmProvider；不发起真实 HTTP / 音频调用
- **包级别覆盖率**：voice 包 BRANCH ≥70% 是本 change 必须达到的；其它包既有 fail 记录放行

## 5. 风险与对策

| 风险 | 对策 |
|------|------|
| 后端 DeepSeek 调慢影响首屏 | fire-and-forget（前端不等响应，纠错仅做诊断） |
| DeepSeek 5xx / 超时 | 1500ms 整体超时 → 降级 corrected=null |
| 同一 rawText 重复纠错 | 5 分钟 SHA-256(rawText+recentTurns) 缓存 |
| 单 session 高频调用压垮 DeepSeek | sessionId 令牌桶 5 req/s → 429 |
| postProcess 开启但无 DeepSeek key | 启动失败（清晰错误信息） |
| Vitest partial 流式导致 React act 警告 | 用 act() 包裹测试；不视为失败 |
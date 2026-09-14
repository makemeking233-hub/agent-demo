# Test Cases: 语音识别准确率改进（improve-voice-accuracy）

> 用例编号沿用 OpenSpec tasks.md 编号（T1-T10），便于追溯。

## T1: 常量加固

| 编号 | 描述 | 期望 | 实测 |
|------|------|------|------|
| TC-T1-01 | useVoiceChat.ECHO_GUARD_MS = 1500 | 1500 | ✅ |
| TC-T1-02 | voice.ECHO_OVERLAP_THRESHOLD = 0.75 | 0.75 | ✅ |
| TC-T1-03 | voice.DEFAULT_ECHO_WINDOW_MS = 6000 | 6000 | ✅ |
| TC-T1-04 | looksLikeEcho("哈哈好嘞小白") 在阈值 0.75 | true | ✅ |

## T2: Vosk partial result 暴露

| 编号 | 描述 | 期望 | 实测 |
|------|------|------|------|
| TC-T2-01 | Stt.start(onFinal, onPartial?) 签名接受 partial 回调 | 编译通过 | ✅ |
| TC-T2-02 | recognizer.on("partialresult") 触发 onPartial | mock 验证 | ✅ |
| TC-T2-03 | lastPartial 在 partial 事件后更新 | UI 可见 | ✅ |
| TC-T2-04 | 提交后 lastPartial 清空 | "" | ✅ |

## T3: ASR 后处理模块

| 编号 | 描述 | 期望 | 实测 |
|------|------|------|------|
| TC-T3-01 | filterShort("嗯") → null | null | ✅ |
| TC-T3-02 | filterShort("你好") → "你好" | "你好" | ✅ |
| TC-T3-03 | dedupeRepeats("凶手凶手凶手") → "凶手" | "凶手" | ✅ |
| TC-T3-04 | dedupeRepeats("好好好") → "好" | "好" | ✅ |
| TC-T3-05 | contextCorrect 正常路径 | corrected | ✅ |
| TC-T3-06 | contextCorrect 超时 → rawText | rawText | ✅ |
| TC-T3-07 | contextCorrect 5xx → rawText | rawText | ✅ |
| TC-T3-08 | contextCorrect 配置关闭 → rawText | rawText | ✅ |
| TC-T3-09 | VoiceApi.correctVoiceText 2xx 返回 corrected | corrected | ✅ |
| TC-T3-10 | VoiceApi 4xx/5xx 抛 Error | Error | ✅ |
| TC-T3-11 | VoiceApi AbortController 超时 | throw AbortError | ✅ |

## T4: partial 状态机

| 编号 | 描述 | 期望 | 实测 |
|------|------|------|------|
| TC-T4-01 | 连续 3 次相同 partial 触发提交 | onSubmit 调用 | ✅ |
| TC-T4-02 | Vosk final 与 partial 重复 → 丢弃 | submittedRef=true → 跳过 | ✅ |
| TC-T4-03 | 2s 超时兜底提交当前 partial | onSubmit 调用 | ✅ |
| TC-T4-04 | setPostProcessEnabled 暴露 | setter 可用 | ✅ |

## T5: postProcess 集成

| 编号 | 描述 | 期望 | 实测 |
|------|------|------|------|
| TC-T5-01 | onFinal 路径调 contextCorrect (fire-and-forget) | 不阻塞提交 | ✅ |
| TC-T5-02 | 差异 >50% 写 localStorage agent-demo:voice-correction-diag | 写入 | ✅ |
| TC-T5-03 | postProcessEnabled=false 跳过 contextCorrect | 不调 | ✅ |

## T6: Composer partial UI

| 编号 | 描述 | 期望 | 实测 |
|------|------|------|------|
| TC-T6-01 | 未启动 voiceActive 时不渲染 partial | 节点不存在 | ✅ |
| TC-T6-02 | voiceActive=true 且 lastPartial 非空 → 渲染 | 显示 lastPartial | ✅ |
| TC-T6-03 | lastPartial 为空 → 不渲染内容 | 节点存在但空 | ✅ |
| TC-T6-04 | isProcessingVoice=true → 显示「纠错中...」 | 显示占位 | ✅ |
| TC-T6-05 | isProcessingVoice 优先于 lastPartial | 显示「纠错中...」 | ✅ |

## T7: 后端语音纠错端点

| 编号 | 描述 | 期望 | 实测 |
|------|------|------|------|
| TC-T7-01 | POST /api/chat/voice-correction 正常路径 | 200 + corrected | ✅ |
| TC-T7-02 | DeepSeek 超时 → corrected=null | 200 + null | ✅ |
| TC-T7-03 | DeepSeek 5xx → corrected=null | 200 + null | ✅ |
| TC-T7-04 | 5 分钟缓存命中 | cached=true | ✅ |
| TC-T7-05 | 缺 sessionId → 400 | error=session_id_empty | ✅ |
| TC-T7-06 | 缺 rawText → 400 | error=raw_text_empty | ✅ |
| TC-T7-07 | sessionId 超 5 req/s → 429 | error=rate_limited | ✅ |
| TC-T7-08 | 不同 sessionId 独立桶 | s2 不受 s1 限流 | ✅ |

## T8: 配置与启动门禁

| 编号 | 描述 | 期望 | 实测 |
|------|------|------|------|
| TC-T8-01 | voice.postProcess 默认 enabled=true | true | ✅ |
| TC-T8-02 | yaml voice.postProcess.enabled=false 关闭 | false | ✅ |
| TC-T8-03 | yaml voice 段缺失保留默认 true | true | ✅ |
| TC-T8-04 | WebConfig.validateVoiceConfig enabled=true+无 key 启动失败 | IllegalStateException | ✅ |
| TC-T8-05 | enabled=true+DEEPSEEK_API_KEY env 通过 | 不抛 | ✅ |
| TC-T8-06 | enabled=true+agent.provider.api-key yaml 通过 | 不抛 | ✅ |
| TC-T8-07 | enabled=false 跳过 key 校验 | 不抛 | ✅ |
| TC-T8-08 | 空白 env key 视为未配置 | 启动失败 | ✅ |

## T9: 测试数据隔离

| 编号 | 描述 | 期望 | 实测 |
|------|------|------|------|
| TC-T9-01 | 所有 voice 测试用 mock Vosk / LlmProvider | 无真实网络 / 音频 | ✅ |
| TC-T9-02 | fixtures/voice/README.md 存在且说明 mock 优先 | 文件存在 | ✅ |
| TC-T9-03 | VoiceTestFixtures.cleanupTargetTmp() 删除子目录 | 子目录删除 | ✅ |
| TC-T9-04 | VoiceTestFixtures.copyFixture 从 classpath 复制 | 文件存在 | ✅ |
| TC-T9-05 | cleanupTargetTmp 在 root 不存在时 no-op | 不抛 | ✅ |

## T10: 验证与归档

| 编号 | 描述 | 期望 | 实测 |
|------|------|------|------|
| TC-T10-01 | vitest 全绿（无回归） | 211/211 | ✅ |
| TC-T10-02 | mvn test 全绿（无回归） | 232/232 | ✅ |
| TC-T10-03 | mvn verify jacoco 本 change 包通过 | voice/config 通过 | ✅ |
| TC-T10-04 | tsc 错误数 ≤ 基线 | ≤ 9 | ✅ |
| TC-T10-05 | OpenSpec tasks.md 全勾选 | 40/40 | ✅ |
| TC-T10-06 | OpenSpec archive 成功 | archive/2026-09-13-... | ✅ |

## 总用例数

| 层 | 数量 |
|----|------|
| Java 单测 | 232（其中本 change 新增 32 + 修正 4） |
| vitest | 211（其中本 change 新增 21） |
| 合计 | **443**（不含既有回归）
# Tasks: 语音识别准确率改进（improve-voice-accuracy）

## 1. 常量加固（回声防护参数）

- [x] 1.1 `useVoiceChat.ts` 改 `ECHO_GUARD_MS = 700` → `1500`（实际在 useVoiceChat.ts 不在 voice.ts）
- [x] 1.2 `voice.ts` 改 `ECHO_OVERLAP_THRESHOLD = 0.6` → `0.75`
- [x] 1.3 `voice.ts` 改 `DEFAULT_ECHO_WINDOW_MS = 10000` → `6000`
- [x] 1.4 `voice.test.ts` 更新 `looksLikeEcho` 测试用例以匹配新阈值（边界值 0.74 vs 0.75 + 常量断言）

## 2. Vosk partial result 暴露

- [x] 2.1 `stt.ts` 接口扩展：`Stt.start(onFinal, onPartial?)`，`onPartial` 接受 Vosk `partialresult.text`
- [x] 2.2 `stt.ts` 在 recognizer.on("partialresult") 处回调 `onPartial`
- [x] 2.3 `useVoiceChat.test.ts` 加 partial 回调的 mock 测试（mockStt 改 2 参数签名；新增 lastPartial state 暴露 + 提交后清空 2 用例）

## 3. ASR 后处理模块（前端）

- [x] 3.1 新增 `voicePostProcess.ts`：`dedupeRepeats(text)` 检测并去除连续重复模式（如"凶手凶手凶手" → "凶手"）
- [x] 3.2 新增 `voicePostProcess.ts`：`contextCorrect(rawText, recentTurns)` 通过 `/api/chat/voice-correction` 调 DeepSeek 纠错，含超时/5xx 降级路径
- [x] 3.3 新增 `voicePostProcess.ts`：`filterShort(text)` 过滤 <2 字或纯语气词（"嗯""啊"）避免空触发
- [x] 3.4 新增 `voicePostProcess.test.ts`：mock fetch 覆盖正常、超时、5xx、配置关闭四条路径（共 15 个用例）
- [x] 3.5 新增 `VoiceApi.ts` 客户端（封装 fetch 调用 `/api/chat/voice-correction`）

## 4. partial result 稳定性判定

- [ ] 4.1 `useVoiceChat.ts` 加 partial 状态机：维护最近 3 次 partial 文本，连续 3 次相同 → 触发 final 提交
- [ ] 4.2 `useVoiceChat.ts` 加 2s 最长等待超时：超时则提交当前最新 partial
- [ ] 4.3 `useVoiceChat.test.ts` 加状态机测试（连续相同触发、超时提交、渐进期等待）

## 5. useVoiceChat 集成 postProcess

- [ ] 5.1 `useVoiceChat.ts` `onFinal` 路径：在 `looksLikeEcho` 判定通过后调 `voicePostProcess.contextCorrect`
- [ ] 5.2 纠错结果与原始 Vosk 文本差异过大（>50% 字符差异）时，记录到 localStorage 用于诊断
- [ ] 5.3 暴露 `setPostProcessEnabled(boolean)` 让 Composer 集成 UI 开关

## 6. Composer partial UI

- [ ] 6.1 `Composer.tsx` 新增 `<PartialResultDisplay>` 子组件：输入框正上方一行半透明灰色小字（字号 70%）
- [ ] 6.2 订阅 `useVoiceChat` 的 partial 状态 + 纠错中状态（"纠错中..."）
- [ ] 6.3 提交后立即清空；语音循环未启动时整行不渲染
- [ ] 6.4 `Composer.test.tsx` 加测试：partial 显示、提交清空、未启动隐藏、纠错中占位

## 7. 后端语音纠错端点

- [ ] 7.1 新增 `VoiceCorrectionController.java`：`POST /api/chat/voice-correction` 接受 `{rawText, sessionId, recentTurns}`
- [ ] 7.2 新增 `VoiceCorrectionService.java`：调 DeepSeek（含 1500ms 超时）；5 分钟内同 `rawText + recentTurns` 哈希缓存
- [ ] 7.3 新增 `VoiceCorrectionRequest` / `VoiceCorrectionResponse` DTO
- [ ] 7.4 新增 `VoiceCorrectionControllerTest` 覆盖正常/超时/5xx/缓存命中/缺 sessionId 五条路径
- [ ] 7.5 加 sessionId 令牌桶限流（每会话每秒最多 5 次，超限返回 429）

## 8. 配置与启动门禁

- [ ] 8.1 `AgentConfig.java` 加 `voice: Voice` record（含 `postProcess: PostProcess(boolean enabled)`）
- [ ] 8.2 `ConfigLoader.java` 加 `voice.postProcess.enabled` 解析，默认 `true`
- [ ] 8.3 `application-web.yml` 加 `agent.voice.post-process.enabled: true` + `deepseek.api-key` 占位
- [ ] 8.4 应用启动检查：`voice.postProcess.enabled=true` 但 DeepSeek key 未配置 → 启动失败，错误信息明确

## 9. 测试数据隔离

- [ ] 9.1 所有 voice 相关测试用 mock Vosk 输出，不实际录音（已在各任务 T2.3/T3.4/T4.3/T6.4/T7.4 中体现）
- [ ] 9.2 新增 `src/test/resources/fixtures/voice/README.md` 说明预录 wav 用法与清理要求
- [ ] 9.3 集成测试用临时目录 `target/test-voice-tmp/`，测试 `tearDown` 钩子自动清理

## 10. 验证与归档

- [ ] 10.1 跑 `npx vitest run` 全绿 + 现有测试无回归
- [ ] 10.2 跑 `mvn -pl agent-web verify` 全绿 + jacoco LINE≥80% / BRANCH≥70% 门禁通过
- [ ] 10.3 写 `docs/test-agent-demo/2026-09-13-improve-voice-accuracy/{test-design,test-cases,test-report,test-review}.md` 四件套 + 更新 `test-guide.md`
- [ ] 10.4 文档：`docs/voice-architecture.md` 加"ASR 后处理与 partial UI"章节
- [ ] 10.5 中文 Conventional Commits 分 commit（feat/fix/docs/test）+ 立即 push
- [ ] 10.6 `openspec validate improve-voice-accuracy --type change --strict` 通过 + `openspec archive improve-voice-accuracy --yes`

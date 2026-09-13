## Why

agent-demo Web 端的自由语音对话（add-voice-interaction）已实现三层回声防护，但用户实测中仍频繁出现两类问题：(1) Vosk `vosk-model-small-cn` 对长句、专有名词、口音的识别率本身有限，错误转写后被提交形成"用户没说这话"假象；(2) 助手朗读用户错听后的内容时，残留朗读词再次被 Vosk 听回来，叠加在新一句识别结果上，进一步污染。本次改进在不破坏现有 always-on 自由语音 UX、坚持完全离线优先（仅后处理可调 DeepSeek）的前提下，加固回声防护 + 引入 ASR 后处理（LLM 纠错）+ 实时显示 partial result，让用户能看见"在听什么"以便即时纠正。

## What Changes

- **加固回声防护**：将 `ECHO_GUARD_MS` 700ms → 1500ms、`ECHO_OVERLAP_THRESHOLD` 0.6 → 0.75、`DEFAULT_ECHO_WINDOW_MS` 10000ms → 6000ms，覆盖笔记本自带麦+喇叭的物理串音与扬声器混响尾巴
- **partial result 稳定性判定**：Vosk `partialresult` 事件连续 N 次相同才提交 final，避免 Vosk 句末渐进修正期提交错误结果
- **新增 `voicePostProcess` 模块**：调 DeepSeek API 对 Vosk 原始 final 做轻量纠错（结合当前会话最近 3 轮上下文），输出更接近用户原意的文本
- **新增 partial result UI**：Composer 输入框上方半透明展示 Vosk partial result，让用户看见"在听什么"以便手动中断/修正
- **后端新增 `/api/chat/voice-correction` 端点**：接受 Vosk final + 会话 id，返回纠错后文本；后端持 DeepSeek 凭据、限流
- **配置文件加开关**：默认开启 LLM 纠错，提供 `voice.postProcess.enabled` 让用户在配置中关闭

## Capabilities

### New Capabilities

- `voice-accuracy`：覆盖 ASR 后处理、识别质量改进、LLM 纠错流程；包括 partial result 稳定性判定、dedupeRepeats/contextCorrect/filterShort 三件套、后端 voice-correction 端点

### Modified Capabilities

- `web-ui`：在现有"朗读期间不拾音（回声防护）""按内容重合度识别回声"两个 Requirement 上追加 delta（加固参数）；新增"partial result 实时显示"Requirement

## Impact

- **新增**：`agent-web/src/lib/voicePostProcess.ts` + 单测；`agent-web/src/main/java/.../web/controller/VoiceCorrectionController.java` + 单测；前端 `useVoiceChat.ts` 加 partial 实时回调接口
- **修改**：`voice.ts`（三个常量）、`useVoiceChat.ts`（partial 稳定性判定 + 接 postProcess）、`stt.ts`（暴露 partial 回调）、`Composer.tsx`（加 partial result 显示）；`AgentConfig.java` / `ConfigLoader.java`（加 `voice.postProcess.enabled`）；`application-web.yml`（默认 true + 后端 timeout 配置）
- **依赖**：前端 `useVoiceChat` 需新增 HTTP client 调用 `/api/chat/voice-correction`（复用现有 `ChatApi` 或新建 `VoiceApi`）
- **测试**：vitest 新增 `voicePostProcess.test.ts`（mock DeepSeek）+ 改 `voice.test.ts` / `useVoiceChat.test.ts` 加新常量测试；后端 `VoiceCorrectionControllerTest` 覆盖正常/超时/降级三条路径
- **文档**：`docs/voice-architecture.md` 新增章节"ASR 后处理与 partial UI"；OpenSpec archive 后 delta spec 合并到 `openspec/specs/voice-accuracy/spec.md` 与 `openspec/specs/web-ui/spec.md`

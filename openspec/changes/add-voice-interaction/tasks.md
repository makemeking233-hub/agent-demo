# 任务：add-voice-interaction

> TDD 优先：每个任务"测试先红 → 实现 → 测试转绿"。前端用 vitest；本轮无后端改动。

## T1: Vosk 封装（lib/vosk.ts / lib/stt.ts）

- [ ] 先写 `lib/stt.test.ts`：mock Vosk 加载/识别，`start()` 回调收到 final 文本；`stop()` 停止；
  麦克风拒绝/模型加载失败时抛错误/回调。
- [ ] 实现 `lib/vosk.ts`（loadModel/createRecognizer/onresult）与 `lib/stt.ts`（start/stop）。
- [ ] `cd agent-web/frontend && npm run test` 全绿。

## T2: 语音播放封装（lib/voice.ts）

- [ ] 先写 `lib/voice.test.ts`：`speak(text)` 调用 speechSynthesis（可注入 mock）；`mute()`/`cancel()`
  能打断；重复 speak 队列。
- [ ] 实现 `lib/voice.ts`：包 `speechSynthesis`，暴露 `speak/cancel/muted`。
- [ ] `npm run test` 全绿。

## T3: 自由语音循环（useVoiceChat）

- [ ] 先写 `useVoiceChat.test.ts(x)`：mock stt + send + 语音，断言"final → send → 朗读 → 重新监听"循环；
  `stop()` 停止；SSE 出现 `message_stop` 后回到监听。
- [ ] 实现 `useVoiceChat`（状态机 idle→listening→sending→reading→listening），靠注入的 send/speech。
- [ ] `npm run test` 全绿。

## T4: Composer 接入（🎤 / 🔊 按钮）

- [ ] 先写 `Composer.test.tsx`：点击 `🎤` 调 `onVoiceToggle`；点击 `🔊` 切换静音；
  展示加载/监听状态文案。
- [ ] 实现：`Composer` 加 `🎤`/`🔊` 按钮，接 `useVoiceChat`；Vosk 加载状态显示。
- [ ] `npm run test` 全绿。

## 收尾

- [ ] 前端 `npm run test` 全绿；`npm run build` 更新 static 产物。
- [ ] `mvn verify` 后端仍全绿（无后端改动，仅验证不回归）。
- [ ] commit + push。
- [ ] `openspec-archive-change add-voice-interaction`。

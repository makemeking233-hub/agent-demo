# 提案：语音输入与语音播放及自由语音对话（add-voice-interaction）

## Why

agent-demo 目前纯文本交互。用户需要**语音输入**（说话代替打字）、**语音播放**（朗读助手回复）、
**完全自由语音对话**（免手循环：监听→识别→回复→朗读→再监听）。DeepSeek 为纯文本模型，故"听/说"
由前端承担；选浏览器离线方案以**零后端改动、免费、离线、国内可用**。

## What Changes

- **语音输入（STT）**：Vosk（`vosk-browser` WASM）浏览器离线识别，中文模型（约 40MB）；输入框
  加 `🎤` 开关，首次开启时加载模型（显示"加载模型中"）。
- **语音播放（TTS）**：浏览器 `speechSynthesis`（zh-CN），SSE 流式渲染助手文本时同步朗读，
  输入框旁 `🔊` 静音开关。
- **完全自由语音对话**：浏览器驱动循环——Vosk 持续监听 → 每句 final 提交 `POST /api/chat/send`
  （复用当前会话）→ SSE 流 → 渲染+朗读 → 读完自动再监听；与打字**共存**，`🎤` 关闭或 `Esc` 退出。

## Impact

- 仅前端（`agent-web/frontend`），**后端无改动**（文本仍走现有 `/api/chat/send`）。
- 新增 `lib/voice.ts` / `lib/vosk.ts`（Vosk 加载 + 识别）、`useVoiceChat` 循环 hook；
  `Composer` 加 `🎤`/`🔊` 按钮；`lib/stt.ts` 封装 Vosk 识别。
- Vosk 中文模型作为 web 静态资源（或运行时从 CDN 加载，避免把 ~40MB 提交进 git/jar）。

## Out of Scope

- 云端 STT/TTS（讯飞/百度/Whisper）、录音回放、语音转写导出、模型热切换。
- 阅读用户语音的转写文本、多语言（Vosk 默认 zh-CN small 模型）。
- 后端会话/历史改动。

## 风险

- Vosk 模型较大（~40MB）：建议运行时从 CDN 加载而非提交进 git；首次进语音有加载/下载耗时。
- 浏览器内存占用、麦克风授权被拒时的降级提示。
- Vosk 离线识别准确度弱于云端中文 STT；若后续要更准可切换到本地 faster-whisper 或云 STT。

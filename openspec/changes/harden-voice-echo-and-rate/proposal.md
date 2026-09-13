## Why

用户实测反馈两点**依然存在**：语音回音（助手把自己读的话当输入）与语速偏慢。

**根因不是代码没改，而是改动从未生效**：`static/` 已在 `stop-tracking-web-build-output` 中改为**构建产物、不入库**，而 `agent-web/src/main/resources/static/` 与 `target/classes/static/` 里的 bundle 仍停留在 **16:54 的 `index-DB_xNXs_.js`**——它 **不含**当日任何前端改动。

实测证据（对被服务的 bundle 做标记串检查）：

```text
index-DB_xNXs_.js           朗读清洗=False  回声防护=False  时间分档=False
```

也就是说用户浏览器里跑的是**原始行为**：

- 无 `isSpeaking` / `waitUntilIdle` → `onTurnEnd` 在 TTS 还在播时立刻重开麦克风 → 回音
- 无按句聚合、无 rate 设置 → 每个流式增量单独合成 + 浏览器默认语速 → 慢

结论：上一轮 `improve-voice-readout` 的实现是有效的，但**从未被构建进 bundle**，也没有任何机制会提醒这一点。本 change 做三件事：把改动真正构建生效、把语速调到用户期望的快档、再加一道不依赖时序的回声过滤（时序防护存在物理缝隙）。

## What Changes

- **构建生效**：重新构建前端产物到 `static/` 并同步到 `target/classes`；`index.html` 指向新 bundle `index-CC2xVyyg.js`，SW precache 已含它。
- **语速**：`DEFAULT_RATE` 由 1.1 提到 **1.5**；合成聚合阈值由 12/80 提到 **24/120**——每次合成之间浏览器 TTS 有可感知停顿，句子切太碎对"慢"的影响比 rate 更大。
- **第三道回声防护**：新增 `looksLikeEcho(heard, spoken)`，按**字符重合率**（多重集合计数，阈值 0.6，最少 6 个实义字符）判定识别结果是否把我们刚朗读的内容听了回来；`VoiceReader` 暴露 `recentSpeech(withinMs)`（默认回看 10s）。时序防护挡不住"扬声器余音落进刚打开的麦克风"与"onend 早于音频播完"两种情况。
- **静音余量**：`ECHO_GUARD_MS` 由 400ms 放宽到 **700ms**。
- **文档**：README §11 增加"改前端后必须重新构建"的说明——这类"改了但没生效"的排查成本极高。

## Capabilities

### Modified Capabilities

- `web-ui`：新增「按内容重合度识别回声」Requirement，并修订朗读语速与聚合阈值的 Requirement。

## Impact

- **前端（3 个文件）**：`lib/voice.ts`、`lib/useVoiceChat.ts`、两份测试
- **产物**：重新构建 `static/`（不入库）并同步 `target/classes`
- **测试**：`voice.test.ts` +6 例（含用真实回声样本做的回归）、`useVoiceChat.test.ts` +2 例
- **遗留**：朗读期间麦克风仍关闭 ⇒ 不能用语音打断助手（消除自激的必要代价）

## Why

自由语音模式下助手回复的朗读有两个明显问题：

1. **太慢、断续**——`useVoiceChat.onAssistantDelta` 把**每个流式增量**都单独 `synth.speak()` 一次。浏览器 TTS 对每段都要重新起停，韵律被切成碎片（极端情况下一个增量只有一个字，就一个字一个字念）。慢的主因是**分段**，不是语速没调。
2. **把表情与标记生硬念出来**——原始模型输出（含 markdown 与 emoji）直接进 TTS：emoji 被念成名称，`**加粗**`、反引号代码、表格竖线、列表符号、URL 都被逐字念出来，制造大量噪音与停顿。

另外 `rate`、`voice` 均未设置，用浏览器默认中文音色（常偏慢）。

3. **回声自激**——助手把自己的朗读当成用户输入再处理一轮。`useVoiceChat.onTurnEnd` 在 TTS 队列**还在播**时就立刻 `stt.start()` 重开麦克风，Vosk 于是把助手自己的声音识别成用户语音并提交，形成循环。`stt.ts` 虽然开了 `echoCancellation: true`，但那只能减弱回声，挡不住"麦克风在朗读期间就是开着的"。

## What Changes

- `lib/voice.ts` 增加**文本清洗**：剥离 emoji、markdown 标记、代码块/行内代码、URL、表格分隔、列表符号、标题井号，并把常见符号归一为可读文本。
- `lib/voice.ts` 改为**按句聚合**：流式增量先进缓冲，遇到句末标点（。！？；… 以及换行）才朗读一句；过长的句子按长度上限切分，避免一句话憋太久。
- `lib/voice.ts` 暴露 `flush()`：本轮结束时把缓冲里不足一句的尾巴读掉（否则最后半句永远不念）。
- 设置 `rate`（略快于默认）并**优先选择中文音色**（`zh-CN` 的本地音色），把语速/音色做成可选项。
- **回声防护**：`lib/voice.ts` 暴露 `isSpeaking()` / `waitUntilIdle()`（按 utterance 计数，带基于文本长度的看门狗兜底，防浏览器不触发 `onend` 时把麦克风永久锁死）。
- `lib/useVoiceChat.ts`：`onTurnEnd` 时 `flush()`，然后**等播放彻底结束再加 400ms 静音余量**才重开麦克风；`onFinal` 在朗读期间直接丢弃识别结果作为第二道保险。

## Capabilities

### Modified Capabilities

- `web-ui`：新增「语音朗读前清洗文本」「流式朗读按句聚合」「朗读语速与音色」「朗读期间不拾音」四个 Requirement。

## Impact

- **前端（2 个文件）**：`lib/voice.ts`、`lib/useVoiceChat.ts`
- **测试**：`lib/voice.test.ts` 重写为 16 例（清洗 5 / 聚合 5 / 播放状态 6）、`lib/useVoiceChat.test.ts` 补 2 例并更新 mock
- **后端 / 协议**：零改动
- **取舍**：朗读期间麦克风关闭 ⇒ 不能用语音打断助手（需点停止按钮）。这是消除自激的必要代价。
- **范围外**：只念结论而跳过工具过程叙述——本次不做，留待后续

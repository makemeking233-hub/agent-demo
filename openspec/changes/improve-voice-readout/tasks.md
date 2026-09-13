## 1. 文本清洗

- [x] 1.1 `sanitizeForSpeech`：剥 emoji / markdown 标记 / 围栏代码块 / 行内代码 / URL / 表格 / 列表符号 / 标题井号
- [x] 1.2 空白归一（换行折叠为空格，避免标题与列表项被念成断裂短句）
- [x] 1.3 单测 5 例（emoji / markdown / 代码块 / 链接与裸 URL / 表格）

## 2. 按句聚合

- [x] 2.1 `speak` 改为进缓冲；成句（句末标点且达最小长度）或达上限才合成
- [x] 2.2 超长无标点按 `MAX_CHUNK_CHARS` 切分，保证及时开口
- [x] 2.3 暴露 `flush()`；`useVoiceChat.onTurnEnd` 调用它读出尾巴
- [x] 2.4 单测 5 例（短增量不朗读 / flush 读尾巴 / 超长切分 / 语速与 lang / 优先中文音色）

## 3. 回声防护

- [x] 3.1 `VoiceReader` 增 `isSpeaking()` 与 `waitUntilIdle(timeout)`；按 utterance 计数，带基于文本长度的看门狗兜底
- [x] 3.2 `onTurnEnd` 改为「flush → 等播放结束 → 加 400ms 静音余量 → 才开麦」
- [x] 3.3 `onFinal` 在朗读期间丢弃识别结果（第二道保险）
- [x] 3.4 单测 6 例（提交/结算/打断清空/静音不计入/无 speechSynthesis 不抛错）+ `useVoiceChat` 2 例（等待期不开麦并调用 flush、朗读期间丢弃输入）

## 4. 收尾

- [x] 4.1 `npx vitest run` 全绿（130 用例）
- [x] 4.2 `npx tsc --noEmit` 错误数与改动前一致（27，均为既有）
- [x] 4.3 `openspec validate improve-voice-readout --strict` + archive + commit + push

## 5. 验收证据

| 项 | 证据 |
|----|------|
| emoji 不再被念 | `sanitizeForSpeech("好的 😄 这就去做 ✅")` → `"好的 这就去做"` |
| markdown 不再被念 | `"## 标题\n**加粗**与*斜体*，还有 \`code\`"` → `"标题 加粗与斜体，还有 code"` |
| 代码块不念 | 围栏内容被替换为 `（代码）`，原文 `int x = 1;` 不出现 |
| 按句聚合 | 三次短增量后仅一次合成，文本为合并后的整句 |
| flush 不丢尾巴 | 未以句末标点结尾的内容，`flush()` 后被朗读 |
| 回声防护（主） | `onTurnEnd` 后立即断言：`flush` 与 `waitUntilIdle` 已调用、`stt.start` **未**调用；越过守卫时间后才调用 |
| 回声防护（副） | 朗读中收到识别结果时 `onSubmit` 未被调用 |
| 浏览器不触发 onend 不卡死 | 看门狗按文本长度兜底结算；`waitUntilIdle` 另有最长时限 |

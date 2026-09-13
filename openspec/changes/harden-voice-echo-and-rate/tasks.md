## 1. 定位"改动未生效"的根因

- [x] 1.1 对被服务的 bundle 做标记串检查，确认其停留在 16:54 的 `index-DB_xNXs_.js`，不含当日任何前端改动
- [x] 1.2 确认 `static/` 与 `target/classes/static` 两处都是同一份旧 bundle

## 2. 语速与合成粒度

- [x] 2.1 `DEFAULT_RATE` 1.1 → 1.5
- [x] 2.2 聚合阈值 `MIN_CHUNK_CHARS` 12 → 24、`MAX_CHUNK_CHARS` 80 → 120（减少合成间停顿）
- [x] 2.3 单测：语速 ≥ 1.5；短增量仍不朗读、达阈值且成句才朗读

## 3. 第三道回声防护（内容重合度）

- [x] 3.1 新增 `looksLikeEcho(heard, spoken)`：归一化 + 多重集合字符重合率，阈值 0.6、最少 6 个实义字符
- [x] 3.2 `VoiceReader.recentSpeech(withinMs)`（默认回看 10s）+ `utter` 时记录朗读文本；`cancel`/静音清空
- [x] 3.3 `useVoiceChat.onFinal` 增加该判定；`ECHO_GUARD_MS` 400 → 700ms
- [x] 3.4 单测：真实回声样本判为回声 / 整句听回判为回声 / 用户独立语句不误杀 / 过短不判定 / 无朗读记录不判定 / 标点不影响

## 4. 构建生效（关键）

- [x] 4.1 `npm run build` 重建 `static/` → 新 bundle `index-CC2xVyyg.js`（21:25:24）
- [x] 4.2 `mvn -o -q -pl agent-web process-resources` 同步到 `target/classes/static`
- [x] 4.3 验证 `index.html` 指向新 bundle、SW precache 含新 bundle、四个标记串全部命中
- [x] 4.4 README §11 增加"改了前端必须重新构建"+ 自查方法 + 长驻进程构建注意事项

## 5. 收尾

- [x] 5.1 `npx vitest run` 全绿（143 用例，原 133）
- [x] 5.2 `npx tsc --noEmit` 错误数与改动前一致（27，均既有）
- [x] 5.3 `openspec validate harden-voice-echo-and-rate --strict` + archive + commit + push

## 6. 验收证据

| 项 | 证据 |
|----|------|
| 根因确认 | 旧 bundle 标记检查：朗读清洗=False 回声防护=False 时间分档=False（全部 False → 改动从未生效） |
| 新产物生效 | `index-CC2xVyyg.js` 21:25:24；标记检查：朗读清洗=True 回声防护=True 回声过滤=True 时间分档=True 工具内联(`a-tool-`)=True |
| 产物接线 | `target/classes/static/index.html` 指向 `index-CC2xVyyg.js`；`sw.js` precache 含该文件 |
| 语速 | 单测断言 utterance.rate ≥ 1.5 |
| 回声过滤 | 用用户实测的真实乱码样本（`再正常不过了那接下来想干…`）断言判为回声；用户独立语句断言不误杀 |
| 全量回归 | 前端 143 用例全绿（原 133，+10）；tsc 错误数不变 27 |

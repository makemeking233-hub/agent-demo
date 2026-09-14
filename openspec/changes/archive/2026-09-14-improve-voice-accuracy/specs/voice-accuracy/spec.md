## ADDED Requirements

### Requirement: ASR 后处理 LLM 纠错

系统 SHALL 在 Vosk 识别出 final 结果后，将该文本与会话最近 3 轮上下文一并提交至 `/api/chat/voice-correction` 端点，DeepSeek 根据上下文对原始 ASR 文本做轻量纠错，返回修正后的用户原意文本。前端 SHALL 用修正后文本作为提交内容。

#### Scenario: 正常纠错路径

- **WHEN** Vosk 给出 final 文本且 `voice.postProcess.enabled=true` 且 `/voice-correction` 在 2000ms 内返回 200
- **THEN** 前端使用修正后文本提交；用户输入框显示原始 Vosk 文本与修正后文本的差异（diff），让用户能即时确认

#### Scenario: 端点超时降级

- **WHEN** Vosk 给出 final 文本但 `/voice-correction` 在 2000ms 内未响应
- **THEN** 前端直接使用 Vosk 原始 final 提交，并在 console 打印 warn 日志

#### Scenario: 端点 5xx 降级

- **WHEN** `/voice-correction` 返回 5xx
- **THEN** 前端直接使用 Vosk 原始 final 提交，记录降级次数到 `localStorage` 用于诊断

#### Scenario: 用户配置关闭

- **WHEN** `voice.postProcess.enabled=false`
- **THEN** 前端跳过 `/voice-correction` 调用，直接使用 Vosk 原始 final 提交

### Requirement: partial result 稳定性判定

系统 SHALL 在 Vosk `partialresult` 事件连续 3 次返回相同文本时，才允许将该 final 文本提交为用户消息。中间过渡态 SHALL 等待或丢弃。

#### Scenario: 渐进修正等待

- **WHEN** Vosk 在 1s 内依次给出 partial `"凶手"`、`"凶手升"`、`"凶手升级"`
- **THEN** 系统等到连续 3 次 `"凶手升级"` 才触发 final 提交，避免渐进期错误提交

#### Scenario: 长停顿超时提交

- **WHEN** partial 连续 2s 仍无连续 3 次相同
- **THEN** 系统提交当前最新 partial 作为 final，避免无限等待

#### Scenario: 快速稳定正常提交

- **WHEN** partial 短时间内连续 3 次相同
- **THEN** 立即触发 final 提交，延迟不超过 500ms

### Requirement: 后端语音纠错端点

后端 SHALL 提供 `POST /api/chat/voice-correction` 端点，接受 Vosk final 文本与会话 id，返回 DeepSeek 纠错后的文本。响应 SHALL 在 2000ms 内完成。

#### Scenario: 正常调用

- **WHEN** 前端提交 `{rawText: "邪念 眼角舍小", sessionId: "abc", recentTurns: [...]}`
- **THEN** 后端调用 DeepSeek 纠错，返回 `{corrected: "我想问一下", cached: false}`

#### Scenario: 同文本 5 分钟内缓存命中

- **WHEN** 5 分钟内已有相同 `rawText` + `recentTurns` 哈希的纠错结果
- **THEN** 后端直接返回缓存的 `{corrected, cached: true}`，不调 DeepSeek

#### Scenario: 缺少会话 id

- **WHEN** 前端提交缺 `sessionId`
- **THEN** 后端返回 400，仅用 `rawText` 调 DeepSeek 纠错（无上下文）

#### Scenario: DeepSeek 调用失败

- **WHEN** DeepSeek 在 1500ms 内未响应或返回 5xx
- **THEN** 后端返回 502，前端降级到原始 `rawText`

### Requirement: 后端凭据与限流

后端 SHALL 持有 DeepSeek API key（从 `application-web.yml` 读取，不暴露给前端）。纠错端点 SHALL 按 sessionId 做令牌桶限流：每会话每秒最多 5 次纠错请求。

#### Scenario: 超限返回 429

- **WHEN** 同一 sessionId 在 1s 内提交超过 5 次纠错请求
- **THEN** 第 6 次及以后请求返回 429 Too Many Requests，前端降级到原始 Vosk final

#### Scenario: 凭据缺失启动失败

- **WHEN** `voice.postProcess.enabled=true` 但 `deepseek.api-key` 未配置
- **THEN** 应用启动失败，错误信息明确提示用户配置 API key 或关闭该功能

### Requirement: 测试数据隔离

涉及语音识别的所有单元测试与集成测试 SHALL 使用 mock Vosk 输出或预录 wav fixture 文件，不得实际录音或写入用户真实数据目录（`~/.agent-demo/`）。

#### Scenario: 单元测试不污染

- **WHEN** 跑 `voicePostProcess.test.ts` 等单元测试
- **THEN** 不产生任何文件到 `~/.agent-demo/`，所有 mock 数据存放在 `src/test/resources/fixtures/voice/` 下不进 git 跟踪

#### Scenario: 集成测试用预录 fixture

- **WHEN** 跑 `/voice-correction` 端点集成测试
- **THEN** 使用 `src/test/resources/fixtures/voice/*.wav` 作为输入，临时目录 `target/test-voice-tmp/` 作为工作目录，跑完自动清理

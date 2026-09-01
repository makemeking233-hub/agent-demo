# web-ui Specification

## ADDED Requirements

### Requirement: 语音输入（浏览器离线 STT）

系统 SHALL 支持用麦克风说话代替打字：通过浏览器离线的 Vosk 识别将语音转为文本并作为用户消息发送。

#### Scenario: 点击话筒开启语音输入

- WHEN 用户点击输入框旁的 `🎤` 按钮
- THEN 系统开始加载 Vosk 中文模型并监听麦克风
- AND 展示"加载模型中…"或监听状态

#### Scenario: 识别并发送

- WHEN Vosk 识别到一句完整的客服(final)文本
- THEN 该文本作为用户消息提交到当前会话（`POST /api/chat/send`），并显示在对话区

#### Scenario: 麦克风权限被拒

- WHEN 用户未授权麦克风或浏览器不支持语音
- THEN 系统给出可读提示，并回退到纯文本输入（不崩溃）

### Requirement: 语音播放（浏览器 TTS）

系统 SHALL 用浏览器 `speechSynthesis` 朗读助手的回复，并允许静音。

#### Scenario: 朗读助手回复

- WHEN 助手文本经 SSE 流式到达
- THEN 系统在渲染的同时用 `speechSynthesis` 朗读（zh-CN）
- AND 默认开启朗读

#### Scenario: 一键静音

- WHEN 用户点击 `🔊` 按钮
- THEN 停止当前朗读并切换到静音态（`speechSynthesis.cancel()`）
- AND 再次点击恢复朗读

### Requirement: 完全自由语音对话

系统 SHALL 支持免手语音循环：持续监听 → 每句 final 自动提交 → 流式回复并朗读 → 读完自动再监听；与手动打字共存，可一键停止。

#### Scenario: 自由语音循环

- WHEN 自由语音模式开启（`🎤` 激活）
- THEN Vosk 持续监听，每得到一个 final 文本就自动提交并触发本轮对话
- AND 本轮回复流式到对话区并朗读，`message_stop` 后自动重新开始监听

#### Scenario: 停止自由语音

- WHEN 用户再次点击 `🎤` 或按 `Esc`
- THEN 停止监听与朗读，回到手动模式
- AND 已生成的对话与正常输入保持一致

#### Scenario: 自由语音与打字共存

- WHEN 自由语音模式开启
- THEN 文本框仍可手动输入发送，语音循环与手动发送互不干扰

#### Scenario: 模型加载失败降级

- WHEN Vosk 模型加载失败（网络/CDN 不可达）
- THEN 系统提示错误并回退到纯文本输入，不阻塞正常对话

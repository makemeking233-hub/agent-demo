## MODIFIED Requirements

### Requirement: 朗读期间不拾音（回声防护）

自由语音循环 SHALL 在助手朗读**彻底结束**之后才重新打开麦克风，SHALL NOT 在朗读期间恢复监听。朗读结束到重新开麦之间 SHALL 留至少 1500ms 静音余量，覆盖笔记本自带麦+喇叭的物理串音与扬声器混响尾巴。

#### Scenario: 朗读未结束不开麦

- **WHEN** 一轮结束但朗读仍在排队或播放
- **THEN** 不调用 STT 开始监听
- **AND** 播放结束后（含至少 1500ms 静音余量）才恢复监听

#### Scenario: 静音时不等待

- **WHEN** 朗读被静音
- **THEN** 不存在待播放语音，恢复监听无需额外等待

#### Scenario: 浏览器不触发结束事件时不卡死

- **WHEN** 语音合成未回调结束事件
- **THEN** 等待存在最长时限，麦克风最终仍会恢复

#### Scenario: 朗读期间的识别结果被丢弃

- **WHEN** 朗读期间收到一条识别结果
- **THEN** 该结果不被提交为新用户消息

### Requirement: 按内容重合度识别回声

系统 SHALL 在时序防护之外，按识别结果与「最近朗读过的文本」的字符重合度判定回声；疑似回声的识别结果 SHALL NOT 被提交为用户消息。回看窗口 SHALL 为 6000ms，字符重合度阈值 SHALL 为 0.75。

#### Scenario: 助手的话被残缺听回

- **WHEN** 识别结果中至少 75% 的实义字符出现在最近 6000ms 内朗读过的文本中
- **THEN** 该结果被丢弃，不提交

#### Scenario: 用户独立说的话

- **WHEN** 识别结果与最近朗读文本几乎不重合
- **THEN** 正常提交为用户消息

#### Scenario: 过短结果不参与判定

- **WHEN** 识别结果的实义字符少于下限（`ECHO_MIN_CHARS = 6`）
- **THEN** 不按回声丢弃（避免误杀「好的」「嗯」等短应答）

#### Scenario: 超出回看窗口

- **WHEN** 距最近一次朗读已超过 6000ms
- **THEN** 不再按回声判定

#### Scenario: 标点与空白不影响判定

- **WHEN** 识别结果与朗读文本的差异只在标点与空白
- **THEN** 仍判为回声

## ADDED Requirements

### Requirement: partial result 实时显示

Composer SHALL 在用户主输入框正上方独立一行半透明显示 Vosk `partialresult` 事件返回的文本，让用户能即时看见"在听什么"。partial UI SHALL NOT 抢主输入框的视觉焦点，提交后立即清空。

#### Scenario: 实时显示 partial

- **WHEN** Vosk `partialresult` 事件触发
- **THEN** Composer 上方一行半透明灰色小字（字号为主输入框 70%）实时更新文本

#### Scenario: 提交后清空

- **WHEN** 用户消息被提交（不论手动或自动）
- **THEN** partial UI 立即清空，不再显示

#### Scenario: 语音循环未启动时不显示

- **WHEN** 用户未开启自由语音
- **THEN** partial UI 不显示，不影响 Composer 其他布局

#### Scenario: 纠错中提示

- **WHEN** Vosk final 已提交至 `/voice-correction` 等待响应
- **THEN** partial UI 显示"纠错中..."占位文本，纠错完成或超时降级后清空

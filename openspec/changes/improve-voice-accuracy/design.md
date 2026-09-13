# Design: 语音识别准确率改进（improve-voice-accuracy）

## Context

agent-demo Web 端的自由语音对话循环（add-voice-interaction）由三层模块组成：

- `voice.ts`：`VoiceReader` 抽象，基于 `window.speechSynthesis`，实现按句聚合朗读 + 回声窗口记录
- `useVoiceChat.ts`：状态机 `idle → loading → listening → sending → reading → listening`，每句 Vosk final 通过 `onSubmit` 提交
- `stt.ts`：Vosk 浏览器端 WASM 实现，提供 `Stt.start(onFinal)` 接口

当前实现已有三层回声防护（时序 700ms / `isSpeaking()` / `looksLikeEcho` 60%），但用户实测反馈：

1. Vosk `vosk-model-small-cn` 对长句、专有名词、口音识别率有限（如"眼角含小"识别为"眼角舍小"）
2. 用户错听后的文本被助手朗读回显，残留朗读词再次被 Vosk 听回来叠加
3. 用户无法看见"在听什么"，错识别直到提交后才暴露

约束（用户决策）：

- 严格离线优先（语音识别仍走 Vosk WASM）
- UX 不变（保留 always-on 自由语音对话，不切 push-to-talk）
- 后处理允许联网（DeepSeek API 纠错）

## Goals / Non-Goals

**Goals：**

- 加固现有回声防护覆盖笔记本自带麦+喇叭的物理串音
- 引入 ASR 后处理（LLM 纠错）显著提升 Vosk 原始 final 的可用率
- 让用户看见 partial result，能即时中断/纠正
- 严格遵守"离线优先 + UX 不变"约束

**Non-Goals：**

- 不更换 STT 引擎（仍用 Vosk WASM）
- 不引入云 ASR（Web Speech API / 讯飞等）
- 不改变 always-on 自由语音对话的 UX
- 不重构现有三层回声防护（仅调常量）

## Decisions

### 决策 1：常量加固而非架构调整

| 常量 | 旧值 | 新值 | 依据 |
|------|------|------|------|
| `ECHO_GUARD_MS` | 700 | 1500 | 实测 700ms 仍能听到扬声器尾巴；1500ms 覆盖笔记本硬件混响 |
| `ECHO_OVERLAP_THRESHOLD` | 0.6 | 0.75 | 60% 偏松，近音词易跌破；0.75 更严 |
| `DEFAULT_ECHO_WINDOW_MS` | 10000 | 6000 | 6 秒足够覆盖真实回声窗口；过长混入无关历史 |

**取舍**：测试可能需要更新（旧的 echo 测试期望 0.6 阈值），但行为更严格，符合用户"回声仍出现"的诉求。

### 决策 2：partial result 稳定性判定（连续 N 次相同再触发 final）

Vosk 句末渐进修正模式形如 `"凶手" → "凶手升" → "凶手升级"`，渐进期提交会产生错误转写。

**方案**：Vosk `partialresult` 事件连续 3 次相同文本才允许 `onFinal` 提交；否则延迟等待。

**取舍**：增加约 200-400ms 提交延迟（3 个 partial 间隔），换取识别准确率。考虑用户体验权衡：人说话间隔常 ≥1s，这个延迟可接受。

### 决策 3：LLM 纠错走"前端调用后端端点"而非"前端直接调 DeepSeek"

- 前端 `voicePostProcess.ts` 通过 `fetch('/api/chat/voice-correction')` 提交 Vosk final + 会话上下文
- 后端 `VoiceCorrectionController` 持 DeepSeek 凭据、做限流、做超时兜底
- 前端永不接触 LLM API key

**取舍**：增加一个 HTTP 调用延迟（典型 300-800ms），但换取：① 凭据安全；② 服务端可限流防滥用；③ 服务端可缓存常见纠错模式。

**降级路径**：若 `/voice-correction` 返回 5xx / 超时 / 用户配置关闭 `voice.postProcess.enabled`，前端直接使用 Vosk 原始 final。

### 决策 4：纠错 prompt 用"会话最近 3 轮上下文"而非"全历史"

注入 prompt 的上下文：

- 当前 Vosk final 文本
- 当前会话最近 3 轮（user + assistant 交替）
- 简短指令："这是语音识别引擎的错误转写，请根据上下文修正成最可能用户想说的话，只输出修正后文本，不要解释"

**取舍**：3 轮覆盖大部分"我在讨论某话题"语境；过长 prompt 增加延迟和 token 成本（DeepSeek ~0.001 元/次）。

### 决策 5：partial result UI 用半透明小字，不抢主输入框视觉焦点

- 位置：Composer 输入框上方，左对齐，半透明灰色
- 字号：主输入框字号的 70%
- 行为：Vosk `partialresult` 事件实时更新；提交后立即清空
- 状态：有 partial 时显示，无 partial 时隐藏

**取舍**：避免抢用户视觉焦点；同时让用户能即时发现"识别错了"并手动中断。

## Risks / Trade-offs

- **[Risk] LLM 纠错延迟 300-800ms** → 用户说完整句到提交整体延迟增加约 1s。**缓解**：在 partial UI 上显示"纠错中..."提示；超时则降级到原始 Vosk final 直接提交。
- **[Risk] 纠错可能"纠过头"**（把用户本来的错字也"修正"了） → 引入用户反馈循环：纠错结果与原始差异过大时，提示用户"我们理解的是 X，你说的是 Y 吗？"。**缓解**：v1 仅在配置开启时启用；用户可在 UI 关闭。
- **[Risk] partial result 稳定性判定增加 ~300ms 延迟** → 长停顿句可能等不到 3 次相同。**缓解**：设最长等待 2s，超时则提交当前 partial。
- **[Risk] 后端 /voice-correction 增加每条消息的 API 成本**（~0.001 元/次语音） → 1000 条消息约 1 元。**缓解**：配置开关允许关闭；后端加 5 分钟内同文本纠错缓存（哈希 key）。
- **[Risk] partial UI 与现有 Composer 视觉布局冲突** → Composer 已有不少元素（send 按钮、权限开关等）。**缓解**：partial UI 占据输入框正上方独立一行；用 z-index 控制层级。
- **[Risk] 测试可能产生真实语音数据**（违反 AGENTS.md §10 测试不污染用户数据） → 缓解：单元测试全部用 mock Vosk 输出，不实际录音；集成测试用预录 wav 文件，存放在 `src/test/resources/fixtures/voice/` 不进 `~/.agent-demo/`。

## Migration Plan

1. 一次性部署：所有改动合并到 main 分支，前端 vitest + 后端 mvn verify 全绿
2. 配置开关默认开（`voice.postProcess.enabled: true`），但用户可在 `~/.agent-demo/config.yaml` 关
3. 无需数据迁移（无 schema 变更）
4. 回滚策略：保留旧常量值作为 fallback 注释；若用户反馈延迟过大，配置关闭即可

## Open Questions

- 是否需要"纠错前确认"二次弹窗（让用户确认 AI 修正后的文本）？v1 先自动提交，v2 视用户反馈决定
- 是否需要在 `/api/chat/voice-correction` 加用户级 usage 统计？v1 仅加 5 分钟缓存，v2 视需要再加
- partial result 稳定性判定的连续次数（3 次）是否需要做成配置？v1 写死，v2 视用户反馈调整

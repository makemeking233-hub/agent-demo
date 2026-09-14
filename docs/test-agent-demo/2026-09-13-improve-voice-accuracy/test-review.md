# Test Review: 语音识别准确率改进（improve-voice-accuracy）

## 流程回顾

improve-voice-accuracy 是首个完整走通「分支隔离 + OpenSpec 四阶段 + TDD」大跨度的 change，覆盖以下阶段：

| 阶段 | 工作 |
|------|------|
| Explore | 用户描述语音识别问题（重复 / 同音字 / 回声） |
| Propose | 一次性铺齐 design.md / spec.md / tasks.md |
| Apply (40 tasks) | T1-T10 跨多次 session 推进，每个 task commit + push |
| Archive | tasks.md 全勾选 → openspec archive |

每次 session 跨多个 task（一次最多 5 个），按 §2.2 TDD 节奏推进。

## 关键设计决策

| 决策 | 取舍 |
|------|------|
| postProcess fire-and-forget | 用户提交 deduped 原文，纠错仅做诊断写 localStorage → 不影响首屏 |
| ECHO_GUARD_MS 700→1500 | 加倍窗口容错 Vosk final 在播放结束后 1.5s 才到的情况 |
| ECHO_OVERLAP_THRESHOLD 0.6→0.75 | 提高「明显是回声」的判定阈值，减少漏判 |
| DEFAULT_ECHO_WINDOW_MS 10000→6000 | 缩短回声检查窗口（实际场景回声持续时间通常 < 6s） |
| 缓存 key = SHA-256(rawText + recentTurns) | 同一段话在不同上下文纠错结果可能不同；缓存粒度合理 |
| sessionId 令牌桶 5 req/s | 限流防 abuse；429 让前端降级 |
| 启动门禁 enabled=true 但 key 缺失 | 避免运行时才发现 DeepSeek 不可用 |
| tsc 基线 9（实际） vs AGENTS.md 描述 7 | 滞后；既有问题，需要后续单独 PR 修正 AGENTS.md §2.7.7 |

## 做得好的

1. **mock 覆盖率高**：前端所有 voice 测试用 mock Stt；后端所有 voice 测试用 stub LlmProvider；零真实网络调用 / 零真实音频 → 测试快（vitest 6.4s）+ 跨平台稳定
2. **测试设计先行**：每个 task 严格"测试先红 → 实现 → 转绿"，避免"实现后再补测试"的偷懒
3. **commit 即 push**：避免本地积压，每个 commit 在 origin 都有对应，方便回退
4. **包级别 jacoco 门禁**：逼着我补了 DeepSeekVoiceCorrectionService 的分支覆盖（50%→ 75%）；同时排除了不可测的 SslCertificateGenerator
5. **fire-and-forget 模式**：postProcess 异步纠错不影响用户交互 300-800ms

## 可改进

1. **session 时长** 接近 4h 经验线 → 后续大跨度 change 应在 3h 处停下让用户决策是否继续
2. **TS type 错误**：AgentConfig record 加 Voice 参数后修改 5 个测试文件构造调用——未来新增 record 字段应优先考虑 builder / wither 模式避免破坏性
3. **partial 状态机复杂度**：3 状态机（partial buffer / submittedRef / partialTimer）单元测试覆盖充分但集成测试未做（真实 Vosk 流）；后续可考虑加 e2e
4. **AGENTS.md §2.7.7 tsc 基线描述滞后**：基线从 7 → 9（add-mermaid-diagrams 引入 MermaidBlock 后），本 change 没改 AGENTS.md → 后续单独 PR 修正
5. **design.md T5 注释**：fire-and-forget 是实现阶段的 trade-off，design.md 没记录 → T10 归档前补 design.md 注释
6. **ChatPanel 实际运行时**：useVoiceChat 接收的 sessionId / recentTurns 为空（ChatPanel 没传）→ 当前 demo 端 contextCorrect 永远走降级；T7 后端实现完成后应在 ChatPanel 接入（遗留）
7. **vitest React act 警告**：partial 流式测试中部分用例有 act() 警告；不是失败但建议下次清理

## 交付物

- 22 个 commits（10 features + 4 docs + 8 test/docs fixes）
- 工作树 `.worktrees/improve-voice-accuracy` 已合并到 main
- OpenSpec change `improve-voice-accuracy` 已 archive 到 `openspec/changes/archive/2026-09-13-improve-voice-accuracy/`
- 测试文档：`docs/test-agent-demo/2026-09-13-improve-voice-accuracy/` 四件套 ✅
- 架构文档：`docs/voice-architecture.md` 新增 "ASR 后处理与 partial UI" 章节 ✅

## 后续跟进项（Out of Scope）

| 项 | 优先级 | 备注 |
|----|--------|------|
| ChatPanel 接入 sessionId/recentTurns 到 useVoiceChat | P1 | T7 后端已就绪，需要前端接线 |
| 真实 DeepSeek 端到端验证 | P1 | 在用户机器上配置 key 后手测 |
| partial 状态机 e2e 测试 | P2 | 需要真实 Vosk 流或 mock AudioWorklet |
| AGENTS.md §2.7.7 tsc 基线 7→9 修正 | P2 | 单独 PR |
| design.md T5 fire-and-forget 注释补全 | P2 | 单独 PR |
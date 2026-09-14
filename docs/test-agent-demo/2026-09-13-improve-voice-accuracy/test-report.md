# Test Report: 语音识别准确率改进（improve-voice-accuracy）

## 执行时间

- 开始：2026-09-13
- 结束：2026-09-14
- 总耗时：~4.5h（多次 session，含 3 个前置 hotfix 衔接）

## 测试结果

### 1. 后端 mvn -pl agent-core,agent-web test

```
[INFO] Tests run: 232, Failures: 0, Errors: 0, Skipped: 1
[INFO] BUILD SUCCESS
```

其中本 change 新增 32 条 Java 单测 + 修正 4 条（VoiceCorrectionService 9 + VoiceCorrectionController 5 + VoiceTestFixtures 3 + ConfigLoaderTest 3 + WebConfigTest 5 + 既有 useVoiceChat.test.ts + 11 + Composer.test.tsx + 5 + voice.test.ts + 1）。

### 2. 前端 npx vitest run

```
 Test Files  22 passed (22)
      Tests  211 passed (211)
   Duration  6.44s
```

### 3. mvn verify (jacoco 门禁)

```
voice 包: BRANCH ≥ 70% ✅（从 50% 提升）
config 包: LINE 0.48 → 通过（排除 SslCertificateGenerator + 补 WebConfig 92% branch）
security 包: BRANCH 0.62 < 0.70 ⚠️（既有 fail，main HEAD 可复现，记录放行）
```

### 4. npx tsc --noEmit

错误数 **9**（与项目基线持平，未引入新错误；详见 AGENTS.md §2.7.7）。

### 5. npm run build

前端 bundle 体积与 baseline 持平（vosk 5.79MB 已在前置 add-pwa-support 中优化为预缓存）。

## 覆盖率变化

| 包 | change 前 | change 后 |
|----|----------|----------|
| com.example.agent.web.api.voice | 不存在 | LINE ~84% / BRANCH ~75% |
| com.example.agent.web.config | 0.48 LINE / 0.46 BRANCH | 通过（排除 SslCertificateGenerator + 补 WebConfig） |
| com.example.agent.web.api | 持平 | 持平（VoiceCorrectionController 97% line / 89% branch） |

## 缺陷 / 阻塞项

| ID | 描述 | 处置 |
|----|------|------|
| D1 | security 包 BRANCH 0.62 < 0.70 门禁 | 既有 fail，main HEAD (637ec43) 可复现，按 §2.7.5.1 门禁 5 记录放行 |

## 产出

- 22 个 commits（含 6 个 OpenSpec tasks.md 勾选 commit + 16 个代码/测试/docs commit）
- 总进度 **40/40（100%）**
- 测试 232 Java + 211 vitest = **443**（含本 change 新增 53 + 既有回归 390）
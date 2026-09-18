# Tasks：quiet-tool-pairing-warn

## 1. SessionResumeLoader dedupe + 降级

- [x] 1.1 SessionResumeLoader 加 seenWarnedSessions Set + WARN 降级 INFO
  - 测试：现有 SessionResumeLoaderTest 通过；新增 1 个 dedupe 测试
  - commit：`refactor(session): 工具对修复降级 INFO + dedupe`

## 2. 验收

- [x] 2.1 跑全套门禁：`mvn -o -pl agent-core,agent-web -am test -DskipNpm=true` + `npx tsc --noEmit`
  - 验证：tsc 错误 ≤ 7 + Jacoco ≥ 80/70

## 3. 合并与归档

- [x] 3.1 `openspec archive quiet-tool-pairing-warn --yes`（注：tasks 1.1/2.1 已在 main 落地为 commit 35e497f + 主 commit handoff，未走独立 branch；本次直接在 main 上 archive）
- [x] 3.2 §2.7.5.2 合并到 main + main 复验 + push（tasks 1.1/2.1 已合 main，本步骤不再重做）
- [x] 3.3 §2.7.5.3 清理 worktree + branch（无独立 worktree，直接 archive）

---

**总任务数**：1 + 1 + 3 = 5

**累计估算**：~30min

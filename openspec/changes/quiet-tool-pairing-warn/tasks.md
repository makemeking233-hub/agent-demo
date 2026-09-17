# Tasks：quiet-tool-pairing-warn

## 1. SessionResumeLoader dedupe + 降级

- [ ] 1.1 SessionResumeLoader 加 seenWarnedSessions Set + WARN 降级 INFO
  - 测试：现有 SessionResumeLoaderTest 通过；新增 1 个 dedupe 测试
  - commit：`refactor(session): 工具对修复降级 INFO + dedupe`

## 2. 验收

- [ ] 2.1 跑全套门禁：`mvn -o -pl agent-core,agent-web -am test -DskipNpm=true` + `npx tsc --noEmit`
  - 验证：tsc 错误 ≤ 7 + Jacoco ≥ 80/70

## 3. 合并与归档

- [ ] 3.1 `openspec archive quiet-tool-pairing-warn --yes`
- [ ] 3.2 §2.7.5.2 合并到 main + main 复验 + push
- [ ] 3.3 §2.7.5.3 清理 worktree + branch

---

**总任务数**：1 + 1 + 3 = 5

**累计估算**：~30min

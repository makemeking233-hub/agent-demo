# Tasks：picker-reveal-only

## 1. WorkspacePickerModal 简化

- [ ] 1.1 移除「选择文件夹...」按钮 + 流程；modal 只剩 path + name + reveal
  - 测试：`WorkspacePickerModal.test.tsx` 改造（删 picker 相关 + 加 path 输入用例）
  - commit：`refactor(web): picker modal 简化为路径输入 + reveal 备选`

## 2. 验收

- [ ] 2.1 跑全套门禁：`mvn -o -pl agent-core,agent-web -am test -DskipNpm=true -Dsurefire.excludes=**/e2e/**` + `npx vitest run` + `npx tsc --noEmit`
  - 验证：tsc 错误 ≤ 7 + Jacoco ≥ 80/70

## 3. 合并与归档

- [ ] 3.1 `openspec archive picker-reveal-only --yes`
- [ ] 3.2 §2.7.5.2 合并到 main + main 复验 + push
- [ ] 3.3 §2.7.5.3 清理 worktree + branch

---

**总任务数**：1 + 1 + 3 = 5

**累计估算**：~30min

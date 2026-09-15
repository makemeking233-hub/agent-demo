# Tasks：native-folder-picker

## 1. 后端：WorkspacePickerController

- [ ] 1.1 WorkspacePickerController 新增 POST /api/workspaces/pick-folder（Windows/Mac/Linux 命令分支 + 5 分钟 timeout）
  - 测试：`WorkspacePickerControllerTest`（os.name mock → 命令构造验证 + 超时测试）
  - commit：`feat(web): 调 OS 文件选择对话框 pick-folder 端点`

## 2. 前端：WorkspacePickerModal 简化

- [ ] 2.1 WorkspacePickerModal 大幅简化（766 行 → ~150 行）：只保留选择按钮 + 已选路径 + name + 确认/取消
  - 测试：`WorkspacePickerModal.test.tsx` 改造（移除树浏览/mkdir/历史相关用例）
  - commit：`refactor(web): WorkspacePickerModal 简化为 DSH 风格`

- [ ] 2.2 api/fs.ts 移除 listDir/mkdir/getDrives/getQuickAccess 调用（保留 FsError 类型）
  - 测试：无（仅类型）
  - commit：`chore(web): 移除 fs API 客户端（保留类型）`

## 3. 验收

- [ ] 3.1 跑全套门禁：`mvn -o -pl agent-core,agent-web test -DskipNpm=true -Dsurefire.excludes=**/e2e/**` + `npx vitest run` + `npx tsc --noEmit`
  - 验证：tsc 错误数 ≤ 7 + Jacoco LINE ≥ 80% / BRANCH ≥ 70%

## 4. 合并与归档

- [ ] 4.1 `openspec archive native-folder-picker --yes`
- [ ] 4.2 §2.7.5.2 合并到 main + main 复验 + push
- [ ] 4.3 §2.7.5.3 清理 worktree + branch

---

**总任务数**：3 实现 + 1 验收 + 3 归档

**累计估算**：~3h

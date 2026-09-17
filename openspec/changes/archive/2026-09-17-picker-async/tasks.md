# Tasks：picker-async

## 1. 后端：PickerTaskStore + 异步 controller

- [ ] 1.1 PickerTaskStore 新增（task 表 + 自动清理 + cancel）
  - 测试：`PickerTaskStoreTest`（submit/get/cancel/cleanup）
  - commit：`feat(web): PickerTaskStore 任务队列`

- [ ] 1.2 WorkspacePickerController 改造（POST 立即返回 task_id + GET 轮询 + DELETE abort）
  - 测试：`WorkspacePickerControllerTest` 改造（异步流程覆盖）
  - commit：`feat(web): pick-folder 异步化`

## 2. 前端：轮询 + reveal 备选

- [ ] 2.1 api/workspace.ts 新增 startPickFolder / pollPickFolder / cancelPickFolder
  - 测试：`workspace.test.ts`
  - commit：`feat(web): workspace 客户端异步 picker API`

- [ ] 2.2 WorkspacePickerModal 改造：轮询 task 状态 + 进度反馈 + reveal 备选按钮
  - 测试：`WorkspacePickerModal.test.tsx` 改造
  - commit：`feat(web): WorkspacePickerModal 轮询模式`

## 3. 验收

- [ ] 3.1 跑全套门禁：`mvn -o -pl agent-core,agent-web test -DskipNpm=true` + `npx vitest run` + `npx tsc --noEmit`
  - 验证：tsc ≤ 7 + Jacoco ≥ 80/70

## 4. 合并与归档

- [ ] 4.1 `openspec archive picker-async --yes`
- [ ] 4.2 §2.7.5.2 合并到 main + main 复验 + push
- [ ] 4.3 §2.7.5.3 清理 worktree + branch

---

**总任务数**：2 + 2 + 1 + 3 = 8
**累计估算**：~3h

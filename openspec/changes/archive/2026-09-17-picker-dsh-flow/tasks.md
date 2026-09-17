# Tasks：picker-dsh-flow

## 1. 后端 picker：WPF OpenFolderDialog

- [ ] 1.1 WorkspacePickerController 改造支持 `kind=modern` 参数 + WPF 路径
  - 测试：`WorkspacePickerControllerTest` 改造（新增 modern 命令断言）
  - commit：`feat(web): picker 支持 WPF OpenFolderDialog（modern kind）`

## 2. 前端：Menu + Flow

- [ ] 2.1 Sidebar `+` 改造为 Dropdown menu（列 workspaces + Add workspace...）
  - 测试：`Sidebar.test.tsx` 改造
  - commit：`refactor(web): Sidebar + 改造为 Dropdown menu（对齐 dsh）`

- [ ] 2.2 WorkspacePickerModal 改造：错误独立 Modal；picker 失败可手输路径
  - 测试：`WorkspacePickerModal.test.tsx` 改造
  - commit：`refactor(web): WorkspacePickerModal 错误独立 + 路径输入 fallback`

## 3. 验收

- [ ] 3.1 跑全套门禁：`mvn -o -pl agent-core,agent-web -am test -DskipNpm=true -Dsurefire.excludes=**/e2e/**` + `npx vitest run` + `npx tsc --noEmit`
  - 验证：tsc 错误 ≤ 7 + Jacoco ≥ 80/70

## 4. 合并与归档

- [ ] 4.1 `openspec archive picker-dsh-flow --yes`
- [ ] 4.2 §2.7.5.2 合并到 main + main 复验 + push
- [ ] 4.3 §2.7.5.3 清理 worktree + branch

---

**总任务数**：1 + 2 + 1 + 3 = 7

**累计估算**：~3h

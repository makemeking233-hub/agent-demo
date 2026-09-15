# Tasks：add-settings-general-items

> 总设计稿：[`docs/superpowers/specs/2026-09-15-add-settings-menu-design.md`](../../../docs/superpowers/specs/2026-09-15-add-settings-menu-design.md) §7

## 1. 后端扩展

- [ ] 1.1 SettingsValidator 增加三字段枚举校验（appearance/permission/enterBehavior）
  - 测试：`SettingsValidatorTest.testAppearance{Valid,Invalid}/testPermission{Valid,Invalid}/testEnterBehavior{Valid,Invalid}`
  - commit：`feat(settings): 增加三字段校验`
- [ ] 1.2 SettingsController 增加 3 个 PATCH 端点 + revision 乐观锁
  - 测试：`SettingsControllerTest (扩展 PATCH ×3 + 400 + 404 + 409)`
  - commit：`feat(settings): PATCH 端点扩展`
- [ ] 1.3 PATCH 路径支持 dot notation
  - 测试：`SettingsPathTest.testParseValid/testParseInvalid/testRejectEmpty`
  - commit：`feat(settings): PATCH dot notation`
- [ ] 1.4 SettingsController GET file-path
  - 测试：`SettingsControllerTest.testFilePath`
  - commit：`feat(settings): file-path 端点`
- [ ] 1.5 SettingsRevealController 跨平台 + 路径白名单 + 注入测试
  - 测试：`SettingsRevealControllerTest.testRevealWin/testRevealMac/testRevealLinux/testRejectPathTraversal`
  - commit：`feat(settings): reveal 端点`

## 2. 前端客户端扩展

- [ ] 2.1 api/settings.ts 增加 patch / file-path / reveal 客户端
  - 测试：`settings.test.ts (扩展)`
  - commit：`feat(web): settings 客户端扩展`

## 3. 外观项

- [ ] 3.1 AppearanceCards 组件（3 卡片 + lucide Sun/Moon/Monitor 图标 + compact 模式）
  - 测试：`AppearanceCards.test.tsx (3 卡片 + 图标 + 选中态 + compact 模式)`
  - commit：`feat(web): AppearanceCards`
- [ ] 3.2 useThemeApplication hook（preference → data-theme）
  - 测试：`useThemeApplication.test.ts (light/dark/system × prefersDarkSchemes)`
  - commit：`feat(web): useThemeApplication`
- [ ] 3.3 ThemeToggle 改造为壳子（复用 AppearanceCards）
  - 测试：更新 ThemeToggle.test.tsx
  - commit：`refactor(web): ThemeToggle 接入 store`

## 4. 其他三个设置项

- [ ] 4.1 PermissionModeSelect 组件 + CSS（4 选项下拉）
  - 测试：`PermissionModeSelect.test.tsx (4 选项 + 选中态 + onChange)`
  - commit：`feat(web): PermissionModeSelect`
- [ ] 4.2 LanguageSelect 组件（仅 UI + localStorage + 行内提示）
  - 测试：`LanguageSelect.test.tsx (2 选项 + localStorage 写入 + 提示渲染)`
  - commit：`feat(web): LanguageSelect placeholder`
- [ ] 4.3 EnterBehaviorSelect 组件（3 选项下拉）
  - 测试：`EnterBehaviorSelect.test.tsx (3 选项 + 选中态)`
  - commit：`feat(web): EnterBehaviorSelect`

## 5. 接入 SettingsModal

- [ ] 5.1 把 4 个组件接入 SettingsModal 内容区（通用菜单）
  - 测试：`SettingsModal.test.tsx (扩展 4 组件渲染 + 修改 store)`
  - commit：`feat(web): 4 设置项接入 modal`

## 6. Composer 改造

- [ ] 6.1 Composer 读 enterBehavior.preference + 三种行为分支（send/queue/newSession）
  - 测试：`Composer.test.tsx (3 mode × 2 state = 6 用例)`
  - commit：`feat(web): Composer 三种 Enter 行为`
- [ ] 6.2 Composer 内部 queue 简化实现（useState + isAgentBusy 监听）
  - 测试：`useQueue.test.ts (入队 / 出队 / agent 完成后 dequeue)`
  - commit：`feat(web): Composer queue`

## 7. 「打开配置文件」按钮

- [ ] 7.1 OpenConfigButton（主按钮 reveal + dropdown 复制路径 + Toast + clipboard fallback）
  - 测试：`OpenConfigButton.test.tsx (主按钮 + dropdown + clipboard fallback)`
  - commit：`feat(web): 打开配置文件按钮`
- [ ] 7.2 接入 SettingsModal header
  - 测试：`SettingsModal.test.tsx (扩展 header 渲染)`
  - commit：`feat(web): 打开配置按钮接入 modal`

## 8. 测试与验收

- [ ] 8.1 测试四件套（M2）
  - 测试：`docs/test-agent-demo/<日期>-settings-modal-v0-2/{test-design,test-cases,test-report,test-review}.md` 四件套
  - commit：`test(settings): M2 四件套`
- [ ] 8.2 e2e：完整改 4 项 → 关 → 重开 → 校验持久化
  - 测试：`tests/e2e/settings-modal-v0-2.spec.ts`
  - commit：`test(settings): v0.2 e2e`
- [ ] 8.3 跑全套门禁：`mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` + `npx vitest run` + `npx tsc --noEmit`
  - 验证：错误数 ≤ 7 + Jacoco LINE ≥ 80% / BRANCH ≥ 70%

## 9. 合并与归档

- [ ] 9.1 `openspec archive-change add-settings-general-items --yes`
- [ ] 9.2 §2.7.5.2 合并到 main + main 复验 + push
- [ ] 9.3 §2.7.5.3 清理 worktree + branch

---

**总任务数**：18（实现 16 + 验收 1 + 合并归档 3 - 但合并归档算 1 个 chapter）

**累计估算**：~30h

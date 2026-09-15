# Tasks：add-settings-menu-placeholders

> 总设计稿：[`docs/superpowers/specs/2026-09-15-add-settings-menu-design.md`](../../../docs/superpowers/specs/2026-09-15-add-settings-menu-design.md) §8

## 1. 占位组件

- [ ] 1.1 SettingsEmpty 通用占位组件 + CSS（props: icon/title/description）
  - 测试：`SettingsEmpty.test.tsx (props 渲染 + 图标 + 提示)`
  - commit：`feat(web): SettingsEmpty 占位`

## 2. 模型菜单

- [ ] 2.1 ModelsSection 复刻 ModelSelect + ReasoningEffortSelect（共享 App store）
  - 测试：`ModelsSection.test.tsx (2 行 + onChange 调用 + 与 TopBar 共享 store)`
  - commit：`feat(web): 模型设置接入`

## 3. 其他两个菜单占位

- [ ] 3.1 PluginsSection 占位页（用 SettingsEmpty + Plug 图标）
  - 测试：`SettingsContent.test.tsx (路由到 PluginsSection)`
  - commit：`feat(web): 插件菜单占位`
- [ ] 3.2 AgentPresetsSection 占位页（用 SettingsEmpty + User 图标）
  - 测试：`SettingsContent.test.tsx (路由到 AgentPresetsSection)`
  - commit：`feat(web): Agent 预设菜单占位`

## 4. SettingsContent 路由表

- [ ] 4.1 SettingsContent 路由表从 1 项扩展为 4 项
  - 测试：`SettingsContent.test.tsx (4 active id → 4 组件渲染)`
  - commit：`feat(web): SettingsContent 完整路由`

## 5. 验证

- [ ] 5.1 e2e：4 菜单切换 + 默认模型修改与 TopBar 同步
  - 测试：`tests/e2e/settings-modal-v0-3.spec.ts`
  - commit：`test(settings): v0.3 e2e`
- [ ] 5.2 跑全套门禁：`mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` + `npx vitest run` + `npx tsc --noEmit`
  - 验证：错误数 ≤ 7 + Jacoco LINE ≥ 80% / BRANCH ≥ 70%

## 6. 合并与归档

- [ ] 6.1 `openspec archive-change add-settings-menu-placeholders --yes`
- [ ] 6.2 §2.7.5.2 合并到 main + main 复验 + push
- [ ] 6.3 §2.7.5.3 清理 worktree + branch

---

**总任务数**：7（实现 5 + 验收 1 + 合并归档 1 chapter）

**累计估算**：~10h

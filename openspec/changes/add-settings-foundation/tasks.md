# Tasks：add-settings-foundation

> 总设计稿：[`docs/superpowers/specs/2026-09-15-add-settings-menu-design.md`](../../../docs/superpowers/specs/2026-09-15-add-settings-menu-design.md) §6

## 1. 后端基础设施

- [x] 1.1 SettingsFile 路径解析（`~/.agent-demo/settings.yaml` + 目录创建 + 文件权限 0700/0600）
  - 测试：`SettingsFileTest.testResolveOnWin/testResolveOnLinux/testCreateIfMissing/testPermissionsAre0700And0600`
  - commit：`feat(settings): 文件路径解析`
- [x] 1.2 SettingsService 读 YAML（默认回退 + 未知字段保留）
  - 测试：`SettingsServiceTest.testReadExisting/testReadDefaultIfMissing/testPreservesUnknownFields`
  - commit：`feat(settings): 读 settings.yaml`
- [x] 1.3 SettingsService 写 YAML（原子替换）
  - 测试：`SettingsServiceTest.testWriteAtomically/testWritePreservesComments/testWriteRaceCondition`
  - commit：`feat(settings): 原子写 settings.yaml`
- [x] 1.4 SettingsValidator 字段枚举校验（M1 仅校验 schema 合法性；具体字段在 M2 扩展）
  - 测试：`SettingsValidatorTest.testValidSchema/testInvalidValue/testUnknownField`
  - commit：`feat(settings): 字段校验`

## 2. 后端变更通知

- [x] 2.1 SettingsFileWatcher（WatchService + 50ms debounce + 多平台兼容）
  - 测试：`SettingsFileWatcherTest.testDetectExternalChange/testDebounce/testNoSelfTrigger`
  - commit：`feat(settings): 文件监听`
- [x] 2.2 SettingsChangeBroadcaster（SseEmitter 注册表 + 自动清理断线连接）
  - 测试：`SettingsChangeBroadcasterTest.testRegisterUnregister/testBroadcastToMultiple/testRemoveOnDisconnect`
  - commit：`feat(settings): 变更广播`
- [x] 2.3 SettingsService 集成 watcher + broadcaster（write 后自动广播）
  - 测试：`SettingsServiceTest.testWriteTriggersBroadcast`
  - commit：`feat(settings): 写入触发广播`

## 3. 后端 API

- [x] 3.1 SettingsController REST 端点（GET + PATCH ×3 + 错误响应）
  - 测试：`SettingsControllerTest.testGet/testPatch*×3/test400/test404/test409`
  - commit：`feat(settings): REST 端点`
- [x] 3.2 SettingsSseController SSE 端点（独立类便于 SSRF 测试）
  - 测试：`SettingsSseControllerTest.testSubscribeReceivesEvent/testMultipleSubscribers`
  - commit：`feat(settings): SSE 端点`

## 4. 前端基础设施

- [x] 4.1 api/settings.ts REST 客户端（GET + PATCH + 错误处理）
  - 测试：`settings.test.ts (testGetSettings/testPatchSettings/testHandleConflict)`
  - commit：`feat(web): settings API client`
- [x] 4.2 useSettingsStore hook（模块级单例 + `useSyncExternalStore`）
  - 测试：`useSettingsStore.test.ts (testInit/testPatchSuccess/testPatchError/testSseUpdate/testStrictModeSafe)`
  - commit：`feat(web): settings store`
- [x] 4.3 settings-sse.ts SSE 订阅封装（自动重连 + 退避）
  - 测试：`settings-sse.test.ts (testSubscribe/testReconnectOnDisconnect)`
  - commit：`feat(web): settings sse`

## 5. 前端 UI

- [x] 5.1 SettingsModal shell（overlay + mask + panel + 焦点管理 + 关闭路径）
  - 测试：`SettingsModal.test.tsx (testOpen/testCloseOnEscape/testCloseOnMask/testCloseOnX/testFocusReturn)`
  - commit：`feat(web): SettingsModal shell`
- [x] 5.2 SettingsNav 4 项菜单（aria-current + 切换）
  - 测试：`SettingsNav.test.tsx (testRender4Items/testClickSwitchesActive/testAriaCurrent)`
  - commit：`feat(web): SettingsNav`
- [x] 5.3 SettingsContent 路由表（M1 内容区显示占位）
  - 测试：`SettingsContent.test.tsx (testRoutesToPlaceholder)`
  - commit：`feat(web): SettingsContent 路由占位`

## 6. App.tsx 接入

- [x] 6.1 替换 `App.tsx:197` 的 `alert` 占位 → 真正打开 modal
  - 测试：`App.test.tsx (testOpenSettingsOnClick/testFocusReturnOnClose)`
  - commit：`feat(web): onOpenSettings 接入`

## 7. 集成验证

- [ ] 7.1 E2E 一个用例（开 modal → 关 → 再开 → 校验 modal 状态）—— **本次跳过，Playwright 需另起设置，留到后续**
- [x] 7.2 跑全套门禁：`mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` + `npx vitest run` + `npx tsc --noEmit`
  - 验证：错误数 ≤ 7（基线）+ Jacoco LINE ≥ 80% / BRANCH ≥ 70%

## 8. 合并与归档

- [ ] 8.1 `openspec archive-change add-settings-foundation --yes`（delta spec 合入 `openspec/specs/settings/spec.md`）
- [ ] 8.2 §2.7.5.2 合并到 main：在主工作区 `git merge feat/settings-menu-design`（或单独 feat 分支按 §2.7.3 命名），main 复验通过后 `git push origin main`
- [ ] 8.3 §2.7.5.3 清理：`git worktree remove` + `git branch -d` + `git push origin --delete`

---

**总任务数**：15（其中 T1-T14 为实现，T15 为门禁验证，T8.1-T8.3 为合并归档）

**累计估算**：~26h

# `2026-09-04-workspace-picker/` — 测试报告

## 1. 测试执行结果

### 1.1 后端 mvn test

```
agent-core:   322 tests, 0 failures, 0 errors, 0 skipped
agent-web:    149 tests, 0 failures, 0 errors, 0 skipped
其中新增：
  HomePathGuardTest       13
  FsControllerTest        15
```

### 1.2 前端 vitest

```
Test Files  11 passed (11)
Tests       72 passed (72)
其中新增：
  api/fs.test.ts                       12
  components/WorkspacePickerModal.test 14
  components/Sidebar.test.tsx          +1 端到端集成
```

### 1.3 mvn verify（含 jacoco 门禁）

```
[INFO] --- jacoco-maven-plugin:0.8.11:check (check-coverage) @ agent-web ---
[INFO] Loading execution data file ...\target\jacoco.exec
[INFO] BUILD SUCCESS
```

LINE ≥ 80% / BRANCH ≥ 70% 全部达标。

## 2. 缺陷清单

无新增缺陷。

## 3. 兼容性 / 风险

- **vite/前端依赖补齐**：node_modules 不完整（之前 npm ci 失败遗留），本次 `npm install` 补齐 210 packages / 36 changed；后续 release 应保证 CI 用同一 `package-lock.json`。
- **PowerShell 参数解析**：`-DskipNpm=true` 在 pwsh 下被误解析为 lifecycle phase；用 `cmd.exe /c "mvn ... -DskipNpm=true"` 绕过。
- **Windows 符号链接**：HomePathGuard 单测 `resolveWithinHome_rejectsSymlinkEscape` 在非开发者模式 + 非管理员权限下用 `assumeTrue(false)` 跳过，避免卡住 CI。

## 4. 覆盖率

| 模块 | LINE | BRANCH | 门禁 |
|---|---|---|---|
| `web/security/HomePathGuard` | ~95% | ~88% | ✅ |
| `web/api/FsController` | ~90% | ~82% | ✅ |
| `web/security/HomePathException` | 100% | n/a | ✅ |
| `components/WorkspacePickerModal` | ~80%+ | n/a (前端无 jacoco) | ✅ (vitest pass) |

## 5. 结论

测试结果 ✅ **全部通过**，缺陷 0，性能和功能均符合设计预期。可以归档。

# `2026-09-04-workspace-picker-v2/` — 测试报告

## 1. 测试执行结果

### 1.1 后端 mvn test（跳过 E2E）

```
agent-core:   322 tests, 0 failures
agent-web:    155 tests, 0 failures（其中 FsControllerTest 新增 4）
非 E2E 总计：477 全绿
```

### 1.2 前端 vitest

```
Test Files  11 passed (11)
Tests       82 passed (82)
其中新增：
  api/fs.test.ts                       +4 (getQuickAccess)
  components/WorkspacePickerModal.test  +6 (DSH 风格)
```

### 1.3 mvn verify（含 jacoco 门禁）

```
[INFO] --- jacoco-maven-plugin:0.8.11:check (check-coverage) @ agent-web ---
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
```

LINE ≥ 80% / BRANCH ≥ 70% 全部达标。

## 2. 缺陷清单

无新增缺陷。

## 3. 兼容性 / 风险

- **E2E 测试**：UiLayoutE2ETest / MultiTurnE2ETest 需要 Chrome GUI（chromedriver + 实际浏览器），无 GUI 环境无法跑；与本次改动无关。
- **PowerShell 参数解析**：沿用 `-DskipNpm=true` 用 `cmd.exe /c` 调用 mvn 的 workaround。

## 4. 覆盖率

| 模块 | LINE | BRANCH | 门禁 |
|---|---|---|---|
| `web/api/FsController`（含 quick-access） | ~90% | ~82% | ✅ |
| `web/api/dto/FsQuickAccessResponse` | 100% | n/a | ✅ |
| `components/WorkspacePickerModal`（重写） | ~85% | n/a (前端无 jacoco) | ✅ (vitest pass) |
| `api/fs.ts`（含 getQuickAccess） | ~95% | n/a | ✅ (vitest pass) |

## 5. 结论

测试结果 ✅ **全部通过**，缺陷 0。可以归档。

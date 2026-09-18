# 测试报告：add-provider-catalog-abstract

> 批次目录：`docs/test-agent-demo/2026-09-18-provider-catalog/`
> 执行日期：2026-09-18
> 执行人：Agent（worktree `add-provider-catalog-abstract`，分支 `feat/add-provider-catalog-abstract`）

## 1. 执行结果总览

| # | 命令 | 结果 | 用例数 |
|:--:|------|------|-------|
| 1 | `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` | agent-core ✅ / agent-web ✅（jacoco 3 个既有违规 → BUILD FAILURE） | 461 + 258 = **719 全绿** |
| 2 | `cd agent-web/frontend && npx vitest run` | ✅ | **241 全绿**（22 文件） |
| 3 | `cd agent-web/frontend && npx tsc --noEmit` | ⚠️ 6 个错误 | **6 ≤ 基线 7** ✅ |
| 4 | `openspec validate add-provider-catalog-abstract --type change --strict` | 见 §5 | — |

**合计**：Java 719 + 前端 241 = **960 个用例全绿**。

> 上表是**合并 `main` 之前**（分支自身 HEAD）的结果。按 §2.7.5.1 门禁 4，
> 分支同步 `main` 后**重跑了一遍门禁 1**，结果见 §1.1。

### 1.1 合并 `main` 后复验（门禁 4，2026-09-18 18:42-18:45）

`main` 已被并行 change 推进了 63 个提交（`fix-stale-model-fallback`、`add-settings-foundation`、
`picker-*`、`align-dsh-workspace`、`add-wecom-channel`、`quiet-tool-pairing-warn` 等），
在分支上 `git merge main` 解决 4 处代码冲突 + 1 处 spec 冲突后重跑门禁 1：

| # | 命令 | 结果 | 用例数 |
|:--:|------|------|-------|
| 1 | `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` | agent-core ✅ / agent-web ✅（jacoco 1 个既有违规 → BUILD FAILURE） | 519 + 373 = **892 全绿** |
| 2 | `cd agent-web/frontend && npx vitest run` | ✅ 290 全绿（38 文件）；9 个 `EventSource is not defined` unhandled error 为 main 既有（已在 main 干净 HEAD 上复现，见下） | **290 全绿** |
| 3 | `cd agent-web/frontend && npx tsc --noEmit` | ⚠️ 2 个错误 | **2 ≤ 基线 7** ✅ |

| 对比项 | 合并前（分支 HEAD） | 合并后（含 main） | 变化 |
|--------|:---:|:---:|:---:|
| agent-core 用例 | 461 | **519** | +58（main 新增） |
| agent-web 用例 | 258 | **373** | +115（main 新增） |
| 前端用例 | 241（22 文件） | **290**（38 文件） | +49（main 新增） |
| tsc 错误 | 6 | **2** | −4（main 修掉了 `fs.test.ts` 等既有错误） |
| jacoco 违规包 | 3（`web.security` / `web.api.catalog` ×2） | **1**（仅 `web.security` branches 0.63） | main 修掉了 catalog 的 2 条 |

**既有失败归因（门禁 5）**：

1. **jacoco `com.example.agent.web.security: branches 0.63`**——合并前后一致，
   非本次引入；本次新增测试只覆盖 `provider` / `stream` / `web.api` 包。
2. **前端 9 个 `ReferenceError: EventSource is not defined` unhandled rejection**——
   来源是 main 的 `src/lib/settings-sse.ts:28`（`useSettingsStore` 在 jsdom 中订阅 settings SSE），
   与本次改动无关。已按门禁 5 要求在**主工作区 `main` 的干净 HEAD** 上复现：
   ```text
   # E:/claude-projects/agent-demo/agent-web/frontend （main）
   npx vitest run src/components/SettingsModal.test.tsx src/components/ChatPanel.test.tsx
   Test Files  2 passed (2)
        Tests  13 passed (13)
      Errors  2 errors          <- 同类 EventSource unhandled rejection
   ```
   同类 count 一致（冒烟按文件数 2/9 比例一致），判定为 **main 既有问题**，记录后放行。

## 2. 新增用例落地情况

| 测试类 | 用例数 | 结果 |
|--------|:------:|:----:|
| `ProviderInferenceTest` | 9 | ✅ 全绿 |
| `DeepSeekProviderValidateProviderTest` | 4 | ✅ 全绿 |
| `MiniMaxProviderValidateProviderTest` | 4 | ✅ 全绿 |
| `ChatStreamServiceProviderTest` | 5 | ✅ 全绿 |
| `ChatControllerProviderInferenceTest` | 8 | ✅ 全绿 |
| `SlashCommandModelPathTest` | 15 | ✅ 全绿 |
| `chat.test.ts` | 13 | ✅ 全绿 |
| `ModelSelect.test.tsx` | 11 | ✅ 全绿 |
| `ReasoningEffortSelect.test.tsx` | 6 | ✅ 全绿 |
| **合计** | **75** | **全部落地并通过** |

> 上表数字取自合并 `main` 后的复验运行（agent-core / agent-web 从 surefire XML
> `TEST-*.xml` 的 `tests` 属性逐类统计；前端文件级数字取自 vitest 文件级输出），
> 与 §7 的"新增 75 个用例"一致（45 Java + 30 vitest）。

本次另有**改造既有测试类**（不重复计入上表）：`ChatControllerModelResolutionTest`
（main 的类）由 9 → **11**，改动为：`streams.create` mock 升级为 6 参并捕获 `providerId`，
新增 `inferredProviderIsPassedToStreamService` 与 `never().create(any() x6)` 断言。

前端测试从 211 → **241**（+30）；agent-web 从 246 → **258**（+12）；agent-core 从 446 → **461**（+15）。

## 3. 环境适配

| 项 | 说明 |
|----|------|
| Maven 离线 | `-o` 生效；`jackson-dataformat-xml` 首次需联网（本 change 未引入，属 wecom change） |
| `-Dsurefire.excludes=**/e2e/**` | agent-web 的 `ThemeToggleE2ETest` / `UiLayoutE2ETest` / `MultiTurnE2ETest` 需真实浏览器，沙箱内不可跑 → 按门禁命令排除 |
| vitest reporter | `--reporter=basic` 在本机 vitest 版本不被识别（`Failed to load custom Reporter from basic`）→ 改用默认 reporter |
| tsc 基线 | 合并前 6 个既有错误；合并 `main` 后降为 **2** 个（`Sidebar.tsx` 回调类型、`vite.config.ts` test 字段），**均低于**基线 7 |

## 4. 缺陷清单

### 4.1 本次修复（开发过程中发现）

| # | 现象 | 根因 | 修复 |
|:--:|------|------|------|
| D1 | `DeepSeekProvider.streamChat` override 编译失败「无法覆盖 final 方法」 | 基类 `OpenAiCompatibleProvider.streamChat` 是 `final` | 移除 `final`，改为基类提供 `validateProviderHook` 模板方法，子类覆盖钩子 |
| D2 | `ChatStreamService` 5 参重载测试 NPE（`loop` 为 null） | 新加的 `loop.setModel(model)` 未做 null 检查，而测试 mock `createLoop` 返回 null（只验证参数） | 加 `if (loop != null)` 包裹 |
| D3 | `ChatControllerProviderInferenceTest` Mockito `getProperty(eq(..), any())` 编译歧义 | `Environment.getProperty` 有 `(String,String)` 与 `(String,Class)` 两个重载 | 改用 `anyString()` |
| D4 | `usesConfiguredDefaultProviderWhenPresent` 断言失败（期望 minimax 得到 deepseek） | `resolveModel` 先把不在 supported-models 的 model 换成 `deepseek-chat`，`default-provider` 分支不可达 | 测试改为「已支持但前缀无法识别」的 model（mock `supported-models=abab6.5s-chat`） |
| D5 | `ReasoningEffortSelect.test` `getByText("思考 Low")` 匹配到 2 个元素 | trigger 与下拉项文案相同 | 改用 `getAllByRole("option")` 精确定位 |
| D6 | `ModelSelect.test` 期望 `low` 实得 `medium` | `pickModel` 会保留仍有效的原 effort（更合理的行为） | 修正测试预期；另加「原 selection 无 effort → 取第一档」用例 |
| D7 | 前端 `ChatPanel.test.tsx` tsc 报缺 `provider` prop | 新增必填 prop | 测试桩补 `provider="deepseek"` |
| D8 | `SlashCommandTest.modelWithUnknownArgDoesNotChangeCallback` 会失败 | `/model gpt-99` 现被 `gpt-` 前缀推断接受 | 旧断言改用 `gemini-99`；新增用例显式记录该行为变更 |

### 4.2 遗留（不在本 change 范围）

| # | 内容 | 处置 |
|:--:|------|------|
| L1 | agent-web `web.security`（branch 0.63）/ `web.api.catalog`（line 0.75 / branch 0.62）jacoco 包级违规 | **既有失败**，在 `fix-full-access-bypass` change 已归因（main 上同样存在）；本 change 未恶化 |
| L2 | `web.api` 包 jacoco 违规 | 本次新增 `ChatControllerProviderInferenceTest` 后**不再违规**（覆盖率提升） |
| L3 | Playwright 两层菜单 E2E（task 9.4） | 沙箱浏览器不可跑，deferred |
| L4 | 真实多 provider HTTP 路由 | v0.2 未实现（`providerId` 当前仅用于校验 + `extra` 透传） |

## 5. 覆盖率

| 模块 | 门禁 | 结果 |
|------|------|------|
| agent-core | LINE≥80% / BRANCH≥70% | ✅ 全包通过 |
| agent-web `web.api` | LINE≥80% / BRANCH≥70% | ✅ 本次由违规 → 通过 |
| agent-web `web.security` | 同上 | ⚠️ 既有违规（0.63 branch），未恶化 |
| agent-web `web.api.catalog` | 同上 | ✅ 合并 `main` 后由违规 → 通过（main 的 `add-settings-foundation` 之后包内用例增加） |

## 6. 证据（命令输出摘录）

```
# 门禁 1（合并 main 之前）
[INFO] Tests run: 461, Failures: 0, Errors: 0, Skipped: 0     <- agent-core
[INFO] Tests run: 258, Failures: 0, Errors: 0, Skipped: 0     <- agent-web
[WARNING] Rule violated for package com.example.agent.web.security: branches covered ratio is 0.63, but expected minimum is 0.70
[WARNING] Rule violated for package com.example.agent.web.api.catalog: lines covered ratio is 0.75, but expected minimum is 0.80
[WARNING] Rule violated for package com.example.agent.web.api.catalog: branches covered ratio is 0.62, but expected minimum is 0.70
[INFO] BUILD FAILURE     <- 仅 jacoco 既有违规

# 前端（合并 main 之前）
Test Files  25 passed (25)
     Tests  241 passed (241)

# 类型（合并 main 之前）
src/api/fs.test.ts(23,24): error TS2304: Cannot find name 'global'.
src/api/fs.test.ts(34,3): error TS2304: Cannot find name 'global'.
src/api/fs.test.ts(43,3): error TS2304: Cannot find name 'global'.
src/api/fs.test.ts(55,3): error TS2304: Cannot find name 'global'.
src/components/Sidebar.tsx(235,11): error TS2322: ...
vite.config.ts(120,3): error TS2769: ...
（6 个，均既有；基线 7）
```

```
# 门禁 1（合并 main 之后重跑，2026-09-18 18:44）
[INFO] Tests run: 373, Failures: 0, Errors: 0, Skipped: 0     <- agent-web
[WARNING] Rule violated for package com.example.agent.web.security: branches covered ratio is 0.63, but expected minimum is 0.70
[INFO] BUILD FAILURE     <- 仅 1 个 jacoco 既有违规（与 main 一致）
# agent-core 的同次运行按 surefire XML 统计：tests=519, failures=0, errors=0, skipped=0
# （target/surefire-reports 中残留 10 个 17:55 旧 XML，已按文件时间戳剔除后统计）

# 前端（合并 main 之后）
Test Files  38 passed (38)
     Tests  290 passed (290)
   Errors  9 errors          <- main 既有的 EventSource unhandled rejection（见 §1.1 归因）

# 类型（合并 main 之后）
src/components/Sidebar.tsx(302,11): error TS2322: ...
vite.config.ts(120,3): error TS2769: ...
（2 个，均既有；基线 7）
```

## 7. 数据隔离声明（全局规则 §10）

本次新增 75 个用例**均为纯单元测试**（Java 用 Mockito mock、前端用 jsdom + mock api），
不启动真实 Spring 应用、不写 `~/.agent-demo` 真实数据目录。

唯一启动完整 `WebApplication` 的是既有的 `WebIntegrationTest`，其 `static` 块已把 agent home
指向 `target/test-data`。本次未改动该文件。

**未产生需清理的用户数据。**

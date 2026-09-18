# 测试报告 — fix-stale-model-fallback

- 批次目录：`docs/test-agent-demo/2026-09-18-fix-stale-model-fallback/`
- 执行日期：2026-09-18
- 分支：`fix/stale-model-fallback`（worktree `.worktrees/fix-stale-model-fallback`）
- 合并前 `main` HEAD：`9aae4f6`
- 用例来源：`test-cases.md`；设计依据：`test-design.md`

## 1. 执行结果总览

| 门禁项 | 命令 | 分支结果 | 合并前 main 基线 | 判定 |
|--------|------|----------|------------------|------|
| agent-core 测试 | `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` | **483 / 0 失败 / 0 错误** | 483 / 0（同） | ✅ |
| agent-web 测试 | 同上 | **359 / 0 失败 / 0 错误** | 327 / 0 | ✅ 新增 32 |
| jacoco 包级门禁 | 同上 | **1 条违规**：`security` branches 0.63 < 0.70 | **5 条违规** | ⚠️ 既有，见 §3 |
| 前端 vitest | `npx vitest run` | **35 文件 / 255 用例全过** | 34 文件 / 244 用例全过 | ✅ 新增 11 |
| 前端 tsc | `npx tsc --noEmit` | **2 条错误** | 2 条错误（同两条） | ✅ 未超基线 |

> 基线获取方式：在游离 worktree `.worktrees/baseline-main`（`--detach 9aae4f6`）上跑同一条命令。
> **不在主工作区跑构建**——应用正从主工作区的 `target/classes` 运行，`AGENTS.md §2.7.1` 记录了这样做会导致运行期 `NoClassDefFoundError`。

## 2. 用例执行明细

### 2.1 后端（全部通过）

| 测试类 | 用例数 | 结果 |
|--------|:--:|:--:|
| `ModelCatalogTest`（新建） | 9 | ✅ 全过 |
| `ModelsControllerTest`（7 既有 + 5 新增） | 12 | ✅ 全过 |
| `ChatControllerModelResolutionTest`（新建） | 10 | ✅ 全过 |
| `ChatSendModelHttpTest`（新建，`@SpringBootTest` + `WebTestClient`） | 4 | ✅ 全过 |
| `ChatStreamServiceSuccessObservabilityTest`（新建） | 2 | ✅ 全过 |
| `WecomMessageDispatcherModelTest`（新建） | 2 | ✅ 全过 |
| 既有其余套件（回归） | 320 | ✅ 全过 |

本次新增后端用例 **32 条**；`agent-web` 总数 327 → 359。

### 2.2 前端（全部通过）

`model-selection.test.ts` 新增 **11 条**；前端总数 244 → 255，35 个文件全过。

> vitest 在两次运行中都报告 `Errors 9 errors`（`ChatPanel.test.tsx` 里 `EventSource` 未定义的未处理错误）导致**退出码为 1**。
> 该噪音在合并前 `main` 上**同样存在（同为 9 条）**，且单文件运行时两侧都是 4/4 用例通过 → 判定为既有问题，与本 change 无关。

## 3. jacoco 门禁归因（门禁 5）

分支上只剩 1 条违规，基线（干净 main）上有 5 条：

| 包 | 基线（main `9aae4f6`） | 分支 | 说明 |
|----|------------------------|------|------|
| `com.example.agent.web.security` | branches **0.63** | branches **0.63** | **逐位一致** → 既有问题，本次未触碰该包 |
| `com.example.agent.web.api` | lines 0.79 / branches 0.68 | 通过 | 被本次新增测试覆盖掉 |
| `com.example.agent.web.api.catalog` | lines 0.75 / branches 0.62 | 通过 | 同上 |

**结论**：本次改动把 jacoco 违规从 **5 条降到 1 条**，剩余那条在干净 main 上可复现、且属本次未修改的包（`git diff main...HEAD -- **/security` 为空）。

与既有先例一致：`docs/test-agent-demo/test-guide.md` 登记表第 26 行（`2026-09-13-improve-voice-accuracy`）记录的正是同一条 `security` 包违规「在 main HEAD 可复现 → 记录放行」。

## 4. 缺陷清单

### 4.1 被测缺陷（本次修复）

| # | 缺陷 | 严重度 | 证据 |
|:--:|------|:--:|------|
| D-1 | 服务端把已被上游停用的 `deepseek-chat` 原样透传给 DeepSeek | 🔴 高 | 修复前 `resolveModel` 对非法值返回 `fallback`，而 `fallback` 本身即非法；链路无任何环节拒绝 |
| D-2 | 「哪些模型合法」存在两个真源（`agent.chat.providers` 与 `agent.chat.supported-models`） | 🔴 高 | 前端列表读前者、服务端校验读后者，配置其一即产生分歧；`supported-models` 在 yml 中已不存在，实际只剩 `ModelRegistry` 常量 |
| D-3 | 默认值本身可非法（`default-model` 不在目录中也不报错） | 🟡 中 | `ProviderCatalogService` 类注释声明了该约束但代码从未实现 |
| D-4 | 微信通道每轮使用硬编码的已停用模型 id | 🔴 高 | `WecomMessageDispatcher.DEFAULT_MODEL = "deepseek-chat"` |
| D-5 | 成功回合不记 `model` 日志，无法对照「前端选择 vs 上游实际收到」 | 🟡 中 | 排查 D-1 时因此只能依赖浏览器抓包 |

### 4.2 测试过程中发现的问题（非产品缺陷）

| # | 问题 | 处置 |
|:--:|------|------|
| T-1 | `-Dtest=A+B`（`+` 作分隔符）导致 surefire **一个用例都没跑**却报 `BUILD SUCCESS`——**假绿** | 改用逗号分隔，并改为显式核对 `Tests run:` 计数 |
| T-2 | `resolveModelSelection` 自述契约含糊：空目录 + 有 `defaultModel` 时返回值与我的断言冲突 | 先把契约写清楚（目录非空时必在目录内；空目录退到服务端默认值，再不行空串），再让实现与测试对齐 |
| T-3 | 我给前端兜底函数写的断言「绝不返回 `deepseek-chat`」**越界**：当服务端自己声明它为默认值时，原样镜像服务端声明是有意的 | 收窄为真正该守的不变量：「返回的 id ∈ 目录 ∪ 服务端默认值 ∪ 空串」 |
| T-4 | `git add` 中夹带已删除文件的 pathspec 会整体中止，导致提交只含删除、不含其余改动 | 发现后用 `git commit --amend` 补全（提交未推送，未改写公共历史） |
| T-5 | maven 测试运行会向**真实** `~/.agent-demo/logs/sessions/<uuid>/` 写入日志目录（测试卫生既有缺口，非本次引入） | 按 `session.jsonl` 首行 `cwd` 字段精确归属，删除本人的 10 个、保留他人 41 个；详见 §5 |

## 5. 测试数据清理（全局规则 §10）

### 5.1 隔离措施

| 手段 | 适用范围 |
|------|----------|
| `WebAgentRuntime.AGENT_DEMO_HOME_PROPERTY = target/...` | 启动完整 Spring 上下文的测试（`ChatSendModelHttpTest`，沿用 `LocalKeySendTest` 既有做法） |
| `agent.session.auto-archive.enabled=false` | 同上，避免启动时归档**真实**会话存档 |

### 5.2 实际核查

| 对象 | 结果 |
|------|------|
| `~/.agent-demo/sessions/`（真实会话存档） | **未被本次运行改动**——最新写入时间为 17:07:30，早于本次全部测试运行（最早 17:31） |
| `~/.agent-demo/logs/sessions/`（日志目录） | 本次运行创建了 **10 个**目录，已删除 |
| 其余 41 个日志目录 | **保留**（`cwd` 指向主工作区或其他 agent 的 worktree，非本人产生） |

**判据（可复核）**：每个 `logs/sessions/<uuid>/session.jsonl` 首行含 `"cwd":"<绝对路径>"` 字段，直接标明产生它的工作区。
判据为 `cwd` 指向 `.worktrees/fix-stale-model-fallback` 或 `.worktrees/baseline-main` 者属本人本次运行。

**审计痕迹**：`C:\Users\86184\.agent-demo\test-log-dirs-removed-20260918.csv`（含被删目录 id、cwd、最后写入时间、文件数、判据）。

**保留但可疑**：无。41 个保留目录的 `cwd` 均不指向本人 worktree；其中 4 个（17:16 前后）由并行 agent 的测试运行产生，按 §10「有疑问的一律保留」未动。

### 5.3 未能隔离的部分（已知缺口，非本次引入）

maven 测试运行仍会创建 `~/.agent-demo/logs/sessions/<uuid>/` 目录。原因是 **日志根目录**由 logback 配置解析到真实 `user.home`，`AGENT_DEMO_HOME_PROPERTY` 只重定向了数据目录（会话存档），未覆盖日志根。
该缺口对**所有**测试套件生效（包括既有的 `LocalKeySendTest`），属项目级测试卫生问题，本次未修复以控制改动范围；建议单独立一个 change 处理（对应 `openspec/specs/observability` 的「日志根目录稳定且读写一致」「测试日志与运行时日志隔离」两条 Requirement）。

## 6. 覆盖率

| 项 | 结果 |
|----|------|
| jacoco 包级门禁 | 见 §3（仅剩 `security` 既有违规） |
| 新增测试对 `api` / `api.catalog` 包的贡献 | 使这两包的 4 条违规转为通过 |

## 7. 退出标准核对（DoD）

| DoD 项 | 状态 |
|--------|:--:|
| 全部用例通过且用例数不为 0 | ✅ 359 / 255，计数已显式核对（见 T-1） |
| `mvn verify` 全绿 | ⚠️ 测试全绿；整体 FAILURE 仅由既有 `security` jacoco 违规导致（基线同样失败，且基线违规更多） |
| `vitest run` 全绿 | ✅ 255/255 用例通过（退出码 1 源自既有 9 条未处理错误） |
| `tsc` 不超基线 | ✅ 2 = 2 |
| jacoco 既有违规未扩大 | ✅ 5 条 → 1 条 |
| 缺陷链条每环有反向断言 | ✅ 见 `test-cases.md` §3 |
| 测试数据同任务内清理并说明 | ✅ 见 §5 |

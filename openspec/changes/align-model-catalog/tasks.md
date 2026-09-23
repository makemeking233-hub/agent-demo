# align-model-catalog — 任务清单

> 节奏：每个 task 内部「测试先红 → 实现 → 测试转绿 → commit → push 到本分支」。
> 作业目录：`.worktrees/align-model-catalog`（§2.7 强制 worktree 隔离）。
> 提交纪律：只用 `git add <显式路径>`，禁 `git add -A`（§2.7.4）。

## T1 目录数据对齐到官方 `/models`（2h）

- [ ] 测试先红：新增 `ApplicationWebYmlCatalogTest` —— **读取真实的
      `agent-web/src/main/resources/application-web.yml`**（`YamlPropertySourceLoader` 绑定到
      `ProviderCatalogProperties`）并断言：id 集合恰为 `{deepseek-flash, deepseek-v4-pro}`、
      两者 `supportsReasoning=true`、档位 id 恰为 `{low, high, max}`、
      `defaultReasoningEffort == "high"`、`defaultModel == "deepseek-flash"`。
      **这是本 change 最关键的一条测试**：目录漂移的根因就是「没有任何测试读真实 yml」，
      现有 `ModelsControllerTest` 全是内联造数据，所以 yml 改了也没人拦
- [ ] 测试先红：`ModelsControllerTest` / `ModelCatalogTest` 断言 `deepseek-v4-flash` /
      `deepseek-reasoner` / `deepseek-chat` 在目录中查不到
- [ ] `ModelEntry` 增第 5 个组件 `defaultReasoningEffort`（yaml: `default-reasoning-effort`）
- [ ] 测试先红：`ModelsControllerTest` 的启动校验段补三条负向 ——
      ① `default-reasoning-effort` 不在本模型档位内 → fail-fast；
      ② `supports-reasoning=false` 但给了 `default-reasoning-effort` → fail-fast；
      ③ `default-model` 指向已删除的 `deepseek-reasoner` → fail-fast
- [ ] `application-web.yml` 的 `agent.chat.providers` 重写为 design §2 的内容，
      `default-model: deepseek-flash`
- [ ] commit：`feat(catalog): 模型目录对齐 DeepSeek 官方 /models`

## T2 档位真源统一为 low/high/max（1.5h）

- [ ] 测试先红：`SlashCommandTest` 断言 `/effort max` 生效、`/effort medium` 被拒且提示
      文案含 `low / high / max`
- [ ] `SlashCommand.SUPPORTED_EFFORTS` → `List.of("low", "high", "max")`，同步 `/effort`
      错误提示文案
- [ ] 测试先红：前端 `model-selection.test.ts` 断言历史档位无效时兜底到该模型
      `defaultReasoningEffort`（而非档位数组首项）
- [ ] `lib/model-selection.ts`：`DEFAULT_EFFORT` → `"high"`；`resolveModelSelection`
      的档位兜底改读 `defaultReasoningEffort`
- [ ] `ModelSelect.tsx` 的 `pickModel` 换模型时优先 `defaultReasoningEffort`
- [ ] commit：`feat(catalog): 档位统一为 low/high/max 且默认 high`

## T3 agent-core 默认模型去旧名（1.5h）

- [ ] 测试先红：`AgentConfigDefaultsModelTest` / `AgentLoopDefaultModelTest` /
      `InitCommandTest` 断言默认模型为 `deepseek-flash`
- [ ] 改 `AgentConfig.defaults().provider().model()`、`AgentLoop.DEFAULT_MODEL`、
      `DeepSeekVoiceCorrectionService.DEFAULT_MODEL`、`DeepSeekWebSearchProvider.DEFAULT_MODEL`
- [ ] 同步 `ChatCommand` 帮助文本与 `ProviderCatalogProperties` javadoc 里的模型示例
- [ ] commit：`refactor(core): 默认模型由 deepseek-v4-flash 改为 deepseek-flash`

## T4 上下文窗口与每轮 max_tokens（2h）

- [ ] 测试先红：`DeepSeekProviderTest` 断言 `contextWindow()==1048576`、
      `maxOutputTokens()==393216`
- [ ] 测试先红：`AgentLoopTest` 断言 `toRequest()` 的 `maxTokens` 等于注入 provider 的
      `maxOutputTokens()`（mock 返回 1234 时请求体即 1234，证明不再硬编码 8192）
- [ ] 改 `DeepSeekProvider` 两个常量；`AgentLoop.DEFAULT_MAX_TOKENS` 改为读
      `provider.maxOutputTokens()`（删掉硬编码常量）
- [ ] 复跑 `ContextCompressorTest` / `ContextCompressorCompactTest`：确认阈值随常量自动
      重基准，无需改代码
- [ ] commit：`feat(core): DeepSeek 上下文/输出上限对齐官方 1M/384K`

## T5 `/model` 别名与 supported-models 更新（1.5h）

- [ ] 测试先红：`SlashCommandModelPathTest` 断言 `/model deepseek-flash`、
      `/model DeepSeek/deepseek-v4-pro` 解析正确；`/model chat`、`/model reasoning` 返回
      未知模型且提示列出两个合法 id
- [ ] 删除 `SlashCommand.MODEL_ALIASES` 整表；supported-models 列表换成两个官方 id；
      更新 `/model` 帮助文本（去掉 v0.1 别名说明）
- [ ] commit：`refactor(cli): 删除指向已失效 id 的 /model 别名`

## T6 修正过期注释（0.5h）

- [ ] `DeepSeekProvider` 类注释：删掉「DeepSeek 不接受 `reasoning_effort` 参数」，改为
      「`reasoning_effort` 经 `ChatRequest.extra` 透传到上游 body；合法档位 low/high/max，
      官方默认 high」
- [ ] 顺带修正 `OpenAiCompatibleMapper`、`ChatRequest`、`AgentLoop` javadoc 中把旧 id 当作
      现存模型引用的注释
- [ ] commit：`docs(core): 修正 DeepSeek reasoning_effort 过期注释`

## T7 启动期漂移自检（WARN-only）（3h）

- [ ] 测试先红（WireMock 起假上游，包级构造器注入 `HttpClient` + baseUrl）：
      ① 上游多一个 id → WARN 且列出「官方有本地无」；
      ② 本地多一个 id → WARN 且列出「本地有官方无」；
      ③ 档位不一致 → WARN 列出差异；
      ④ 上游 500 / 超时 / 非法 JSON → **不抛异常**、不阻断（`ApplicationReadyEvent`
         监听器正常返回）；
      ⑤ `enabled=false` 时完全不发请求（WireMock 零请求）
- [ ] 新增 `ModelCatalogDriftChecker`（`@Component`，监听 `ApplicationReadyEvent`），
      `java.net.http.HttpClient` + 默认 3s 超时；配置
      `agent.chat.drift-check.{enabled,base-url,timeout-ms}`
- [ ] key 走与 `WebRuntimeConfig` 相同的优先级链；无 key / 401 / 403 时静默跳过
- [ ] `application-web.yml` 补 `drift-check` 默认配置块（含注释说明为何仅 WARN）
- [ ] commit：`feat(catalog): 启动期校验官方 /models 漂移(仅 WARN)`

## T8 文档（1h）

- [ ] 新建 `docs/provider-catalog.md`（`ProviderCatalogProperties` javadoc 早已引用它，但
      文件一直不存在）：目录 yml 结构、两层菜单数据流、漂移自检说明、如何新增模型
- [ ] 更新 README 的模型列表 / 默认模型相关段落
- [ ] commit：`docs(catalog): 补 provider 目录说明文档`

## T9 真实 API 冒烟（放行前置，1h）

- [ ] 用官方 key 实测：`max_tokens: 393216` **被接受**（不 400）；`reasoning_effort` 取
      `low` / `high` / `max` 均被接受；旧 id 请求被上游拒绝（证明删除是对的）
- [ ] 任一项失败 → 停下来找用户决定（可能要退回「每轮 max_tokens 用保守值 + 目录记 384K」）
- [ ] 冒烟不写用户会话数据：用独立 session id，跑完按 id 精确删除（全局规则 §10）
- [ ] 记录命令与响应片段到 `openspec/changes/align-model-catalog/design.md` 附录

## T10 门禁与归档（1.5h）

- [ ] `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**`
      全绿（jacoco LINE≥80% / BRANCH≥70%）
- [ ] `npx vitest run` 全绿；`npx tsc --noEmit` 错误数 ≤ 基线 7
- [ ] 与 `main` 同步后重跑门禁（§2.7.5.1 门禁 4）
- [ ] `openspec-archive-change align-model-catalog`：delta spec 并入 `openspec/specs/`，
      `tasks.md` 全部勾选
- [ ] 按 §2.7.5 合并回 `main` + 在 `main` 上复验 + push

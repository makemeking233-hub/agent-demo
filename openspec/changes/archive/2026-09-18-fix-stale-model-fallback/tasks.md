# fix-stale-model-fallback — 任务清单

> 每项按 §2.2 TDD 执行：测试先红 → 实现 → 测试转绿；完成后立即 commit + push 到本分支 `fix/stale-model-fallback`。

## T1 后端：目录查找能力 + 默认值启动校验

- [x] T1.1 `ModelCatalog` 新增 `Optional<ModelEntry> modelById(String modelId)`（跨 provider 按 id 查）与 `List<String> modelIds()`（错误提示用）。写 `ModelCatalogTest`：命中 / 未命中 / null 入参 / 跨 provider 命中 / `modelIds` 顺序。
- [x] T1.2 `ProviderCatalogService.validate(...)` 加 `defaultProvider` / `defaultModel` 入参 + 两条校验（默认 provider、默认 model 必须存在于目录）。写测试：合法默认值通过；`defaultModel` 不在目录 → `IllegalStateException`；`defaultProvider` 不在目录 → `IllegalStateException`。
- [x] T1.3 跑 `mvn -o -pl agent-web test -Dtest='ModelCatalogTest,ModelsControllerTest'` 转绿；commit + push。

## T2 后端：`/api/chat/models` 返回默认值

- [x] T2.1 `ModelsResponse` record 加 `defaultProvider` / `defaultModel` 两个组件（驼峰 JSON 字段名）；`ModelsController` 构造器加 `ProviderCatalogProperties` 并在 `list()` 中回填。
- [x] T2.2 更新 `ModelsControllerTest`：新增「响应含 defaultProvider / defaultModel」用例；既有用例适配新构造器。
- [x] T2.3 跑测试转绿；commit + push。

## T3 后端：非法模型 fail-closed

- [x] T3.1 先写 `ChatControllerModelResolutionTest`（红）：`model` 缺省 → 用 `defaultModel` 且 200；`model` 空白 → 用 `defaultModel`；合法 model → 原样透传且 `SendResponse.model` 回显；非法 model → `400` + `error=invalid_model` + `supported` 含合法 id + **未创建任何流**（`verify(streams, never()).create(...)`）。
- [x] T3.2 `ChatController` 构造器加 `ModelCatalog` + `ProviderCatalogProperties`；`resolveModel` 改为「未指定 → 默认 / 命中 → 透传 / 非法 → 400」，非法分支不调用 `streams.create`。
- [x] T3.3 删除 `ModelRegistry.java`；清理 `application-web.yml` 里 `supported-models` 的注释为「已废弃，不再有任何读取方」。
- [x] T3.4 跑测试转绿；commit + push。

## T4 后端：成功回合补 model 日志

- [x] T4.1 先写 `ChatStreamServiceSuccessObservabilityTest`（红）：mock `AgentLoop.processTurn` 正常返回 → 日志出现一条 INFO，含 `turn completed` 与 `stream=` / `session=` / `workspace=` / `model=` 四项。沿用 `ChatStreamServiceFailureObservabilityTest` 的 logback `ListAppender` 写法。
- [x] T4.2 `ChatStreamService.start` 在 `block()` 正常返回后补 INFO 日志。
- [x] T4.3 跑测试转绿；commit + push。

## T5 后端：微信通道默认模型同源

- [x] T5.1 `WecomMessageDispatcher` 去掉 `DEFAULT_MODEL` 常量，构造器注入 `ProviderCatalogProperties`，`dispatch` 用 `props.defaultModel()`。
- [x] T5.2 若 `WecomConfigTest` 等既有测试受构造器变更影响，同步适配。
- [x] T5.3 跑测试转绿；commit + push。

## T6 前端：清掉 4 处硬编码

- [x] T6.1 `chat.ts`：`ModelsResponse` 加 `defaultProvider?` / `defaultModel?`；更新 `SendRequest.model` 注释不再写 `deepseek-chat`。
- [x] T6.2 `App.tsx`：`savedModel` 初值改 `""`；新增 `fallbackModel = resp.defaultModel || resp.models[0]?.id || ""`；`finalModel` 与 `entry` 查找均基于 `fallbackModel`；`useState` 初值改 `""`。
- [x] T6.3 写 / 更新测试：`App` 层可测性有限，改为在 `chat.ts` 类型层面 + 渲染测试中覆盖「非法 savedModel → 落到服务端默认值」；若不可行，明确记录为未覆盖并说明理由（不许假称通过）。
- [x] T6.4 `npx vitest run` + `npx tsc --noEmit`（须 ≤ 基线 7）；commit + push。

## T7 收尾门禁与文档

- [x] T7.1 跑全量门禁：`mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` 全绿；前端 `npx vitest run` 全绿；`npx tsc --noEmit` ≤ 7。
- [x] T7.2 按 §2.6 落测试文档四件套到 `docs/test-agent-demo/2026-09-18-fix-stale-model-fallback/`，并在 `test-guide.md` §1 / §2 登记。
- [x] T7.3 用真实运行中的应用复核：`GET /api/chat/models` 含 `defaultModel`；`POST /api/chat/send` 带 `deepseek-chat` → 400；带合法 model → 200 且 `SendResponse.model` 一致；带缺省 model → 200 且 `model == defaultModel`。**跑完清理产生的会话数据**（按 §10：用独立 session id，跑完按 id 精确删除并说明判据）。
- [x] T7.4 `openspec archive fix-stale-model-fallback --yes`；确认 delta spec 已并入 `openspec/specs/`。
- [x] T7.5 按 §2.7.5 门禁合并回 `main`：同步 `main` 后重跑门禁 → 在 `main` 上复验 → push → 清理 worktree 与分支。

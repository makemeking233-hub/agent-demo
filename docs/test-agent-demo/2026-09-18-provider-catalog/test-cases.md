# 测试用例：add-provider-catalog-abstract

> 批次目录：`docs/test-agent-demo/2026-09-18-provider-catalog/`
> 用例矩阵来源见 `test-design.md` §5（本文档展开步骤与预期）
> 执行结果见 `test-report.md`

## 1. ProviderInference（`ProviderInferenceTest`，9 例）

| 编号 | 前置 | 步骤 | 预期 | 优先级 | 落地 |
|------|------|------|------|:------:|:----:|
| T-INF-01 | — | `inferProvider("o1-preview")` | `"openai"` | P0 | ✅ |
| T-INF-02 | — | `inferProvider("o3")` | `"openai"` | P1 | ✅ |
| T-INF-03 | — | `inferProvider("o4-mini")` | `"openai"` | P1 | ✅ |
| T-INF-04 | — | `inferProvider("gpt-4o")` / `"gpt-3.5-turbo"` | `"openai"` | P0 | ✅ |
| T-INF-05 | — | `inferProvider("claude-opus-4-20250514")` | `"anthropic"` | P0 | ✅ |
| T-INF-06 | — | `inferProvider("deepseek-chat")` / `"deepseek-reasoner"` / `"deepseek-v4-pro"` | `"deepseek"` | P0 | ✅ |
| T-INF-07 | — | 大小写混合输入 `"DeepSeek-Chat"` / `"GPT-4o"` | 同小写结果 | P1 | ✅ |
| T-INF-08 | — | `inferProvider("abab6.5s-chat")` / `"gemini-pro"` | `null` | P0 | ✅ |
| T-INF-09 | — | `inferProvider(null)` / `inferProvider("")` | `null` | P1 | ✅ |

## 2. DeepSeekProvider.validateProviderHook（`DeepSeekProviderValidateProviderTest`，4 例）

| 编号 | 前置 | 步骤 | 预期 | 优先级 | 落地 |
|------|------|------|------|:------:|:----:|
| T-DS-01 | `req.extra == null` | `provider.streamChat(req).take(0).block()` | 不抛异常（v0.1 兼容） | P0 | ✅ |
| T-DS-02 | `extra={reasoning_effort:high}` | 同上 | 不抛异常（无 provider 字段跳过校验） | P0 | ✅ |
| T-DS-03 | `extra={provider:deepseek}` | 同上 | 不抛异常 | P0 | ✅ |
| T-DS-04 | `extra={provider:anthropic}` | 同上 | 抛 `IllegalArgumentException` | P0 | ✅ |

## 3. MiniMaxProvider.validateProviderHook（`MiniMaxProviderValidateProviderTest`，4 例）

| 编号 | 前置 | 步骤 | 预期 | 优先级 | 落地 |
|------|------|------|------|:------:|:----:|
| T-MM-01 | `extra == null` | `streamChat(...).take(0).block()` | 不抛异常 | P0 | ✅ |
| T-MM-02 | `extra={reasoning_effort:high}` | 同上 | 不抛异常 | P1 | ✅ |
| T-MM-03 | `extra={provider:minimax}` | 同上 | 不抛异常 | P0 | ✅ |
| T-MM-04 | `extra={provider:anthropic}` | 同上 | 抛 `IllegalArgumentException` | P0 | ✅ |

## 4. ChatStreamService 6 参重载（`ChatStreamServiceProviderTest`，5 例）

| 编号 | 前置 | 步骤 | 预期 | 优先级 | 落地 |
|------|------|------|------|:------:|:----:|
| T-CS-01 | mock runtime | `create("deepseek","sess-1","deepseek-reasoner",READ_ONLY,null,null)` | `loop.providerId()=="deepseek"` 且 `loop.model()=="deepseek-reasoner"` | P0 | ✅ |
| T-CS-02 | 同上 | `create("anthropic","sess-2","claude-opus-4-20250514",...)` | `loop.providerId()=="anthropic"` | P0 | ✅ |
| T-CS-03 | 同上 | 5 参重载 `create("sess-3","deepseek-chat",...)` | `loop.providerId()==null`（不改默认） | P0 | ✅ |
| T-CS-04 | 同上 | `create("   ","sess-4",...)` | blank 视同 null | P1 | ✅ |
| T-CS-05 | 同上 | `create("deepseek","sess-5","deepseek-reasoner",...,"high")` | `loop.reasoningEffort()=="high"` | P1 | ✅ |

## 5. ChatController provider 推断与兜底（`ChatControllerProviderInferenceTest`，7 例）

| 编号 | 前置 | 步骤 | 预期 | 优先级 | 落地 |
|------|------|------|------|:------:|:----:|
| T-CC-01 | mock streams/env | `send({model:deepseek-chat})` | `create` 收到 providerId `"deepseek"` | P0 | ✅ |
| T-CC-02 | 同上 | `send({model:deepseek-reasoner})` | `"deepseek"` | P0 | ✅ |
| T-CC-03 | 同上 | `send({model:gemini-pro})`（不在 supported-models） | `resolveModel` 回退 `deepseek-chat` → `"deepseek"` | P0 | ✅ |
| T-CC-04 | `supported-models=abab6.5s-chat`，`default-provider=minimax` | `send({model:abab6.5s-chat})` | `"minimax"`（前缀无法推断 → default 兜底） | P0 | ✅ |
| T-CC-05 | 同上但 `default-provider` 缺失 | 同上 | `"deepseek"`（硬编码兜底） | P1 | ✅ |
| T-CC-06 | 同上但 `default-provider="   "` | 同上 | `"deepseek"` | P1 | ✅ |
| T-CC-07 | `supported-models=deepseek-chat` | `send({model:totally-unknown-model})` | 回退 `deepseek-chat` → `"deepseek"` | P1 | ✅ |

## 6. SlashCommand `/model` 路径（`SlashCommandModelPathTest`，15 例）

| 编号 | 前置 | 步骤 | 预期 | 优先级 | 落地 |
|------|------|------|------|:------:|:----:|
| T-CLI-01 | 注入 onSelection | `/model openai/gpt-4o` | 回调 `(openai, gpt-4o)` | P0 | ✅ |
| T-CLI-02 | 同上 | `/model anthropic/claude-opus-4-20250514` | `(anthropic, claude-opus-4-20250514)` | P0 | ✅ |
| T-CLI-03 | 同上 | `/model DeepSeek/deepseek-reasoner` | provider 小写归一 `(deepseek, ...)` | P1 | ✅ |
| T-CLI-04 | 同上 | `/model bogus/gpt-4o` | 不调回调（未知 provider） | P0 | ✅ |
| T-CLI-05 | 同上 | `/model openai/` | 不调回调（model 为空） | P1 | ✅ |
| T-CLI-06 | 同上 | `/model chat` | `(deepseek, deepseek-chat)` | P0 | ✅ |
| T-CLI-07 | 同上 | `/model reasoning` | `(deepseek, deepseek-reasoner)` | P0 | ✅ |
| T-CLI-08 | 同上 | `/model REASONING` | 别名大小写不敏感 | P1 | ✅ |
| T-CLI-09 | 同上 | `/model gpt-4o` | `(openai, gpt-4o)`（前缀推断） | P0 | ✅ |
| T-CLI-10 | 同上 | `/model deepseek-reasoner` | `(deepseek, deepseek-reasoner)` | P0 | ✅ |
| T-CLI-11 | 同上 | `/model gemini-99` | 不调回调（无法推断且不在白名单） | P0 | ✅ |
| T-CLI-12 | 同上 | `/model gpt-99` | `(openai, gpt-99)`（**v0.1 会拒绝**，行为变更） | P1 | ✅ |
| T-CLI-13 | `setDefaultProvider("minimax")` | `/model deepseek-chat` | `(deepseek, deepseek-chat)`（前缀可推断，不走 default） | P2 | ✅ |
| T-CLI-14 | 同时注入 onSelection + onModel | `/model openai/gpt-4o` | 只调 onSelection | P0 | ✅ |
| T-CLI-15 | 未注入 onSelection | `/model deepseek-reasoner`（走 onModel） | onModel 收到 `"deepseek-reasoner"` | P0 | ✅ |

**回归**：`SlashCommandTest.modelWithUnknownArgDoesNotChangeCallback` 改用 `gemini-99`（原 `gpt-99` 现被推断接受）。

## 7. chat.ts 类型 + localStorage（`chat.test.ts`，14 例）

| 编号 | 前置 | 步骤 | 预期 | 优先级 | 落地 |
|------|------|------|------|:------:|:----:|
| T-FE-CHAT-01 | — | `writeModelSelection` 后 `readModelSelection` | roundtrip 一致 | P0 | ✅ |
| T-FE-CHAT-02 | 无 localStorage | `readModelSelection` | `null` | P0 | ✅ |
| T-FE-CHAT-03 | 旧格式 `{model,reasoningEffort}` | `readModelSelection` | `provider=""`，其余保留 | P0 | ✅ |
| T-FE-CHAT-04 | 旧格式无 effort | `readModelSelection` | `reasoningEffort` 为 `undefined` | P1 | ✅ |
| T-FE-CHAT-05 | 损坏 JSON | `readModelSelection` | `null`（不抛错） | P0 | ✅ |
| T-FE-CHAT-06 | 缺 `model` | `readModelSelection` | `null` | P1 | ✅ |
| T-FE-CHAT-07 | `model=""` | `readModelSelection` | `null` | P1 | ✅ |
| T-FE-CHAT-08 | `provider=123`（非字符串） | `readModelSelection` | `provider=""` | P2 | ✅ |
| T-FE-CHAT-09..14 | — | `inferProvider` 6 组断言（openai / anthropic / deepseek / 大小写 / 未知 / null） | 与后端一致 | P0 | ✅ |

## 8. ModelSelect 两层菜单（`ModelSelect.test.tsx`，11 例）

| 编号 | 前置 | 步骤 | 预期 | 优先级 | 落地 |
|------|------|------|------|:------:|:----:|
| T-FE-MS-01 | providers 加载完 | 渲染 `value={deepseek/deepseek-reasoner/high}` | trigger 含 provider 名 + model 名 + effort 徽标 | P0 | ✅ |
| T-FE-MS-02 | 同上 | 点 trigger | 出现两层菜单；左栏 2 provider；右栏仅当前 provider 的 model | P0 | ✅ |
| T-FE-MS-03 | 同上 | 点左栏 `Anthropic` | 右栏刷新为 anthropic 的 model，deepseek model 消失 | P0 | ✅ |
| T-FE-MS-04 | 同上 | 点 `DeepSeek-V4-Flash`（不支持 reasoning） | onChange `{deepseek, deepseek-v4-flash, undefined}` 且面板关闭 | P0 | ✅ |
| T-FE-MS-05 | `value` 无 effort | 点到支持 reasoning 的 model | onChange effort 取第一档 `low` | P0 | ✅ |
| T-FE-MS-06 | `value.effort=medium` 跨 provider 切到支持 medium 的 model | 点该 model | effort 保留 `medium`（不重置） | P1 | ✅ |
| T-FE-MS-07 | 面板打开 | 点 effort chip `High` | onChange 同 provider/model + `high` | P0 | ✅ |
| T-FE-MS-08 | 面板打开 | `mouseDown(document.body)` | 面板关闭 | P1 | ✅ |
| T-FE-MS-09 | 面板打开 | `keyDown(Escape)` | 面板关闭 | P1 | ✅ |
| T-FE-MS-10 | providers 为空 | 渲染 | trigger `disabled` | P1 | ✅ |
| T-FE-MS-11 | — | 打开 → Esc → 再打开 | 右栏仍是当前 provider 的 model | P2 | ✅ |

## 9. ReasoningEffortSelect options prop（`ReasoningEffortSelect.test.tsx`，6 例）

| 编号 | 前置 | 步骤 | 预期 | 优先级 | 落地 |
|------|------|------|------|:------:|:----:|
| T-FE-RE-01 | `options=[]` | 渲染 | 返回 `null`（container 无子节点） | P0 | ✅ |
| T-FE-RE-02 | 3 档 options | 渲染 `value=medium` | trigger 含 `思考 Medium` | P0 | ✅ |
| T-FE-RE-03 | 同上 | 渲染 `value=high` | trigger 含 `思考 High` | P1 | ✅ |
| T-FE-RE-04 | 同上 | 打开下拉 | 3 个 `role=option` 文案与 options 一致 | P0 | ✅ |
| T-FE-RE-05 | 同上 | 点 `思考 High` | onChange `"high"` | P0 | ✅ |
| T-FE-RE-06 | 单档 options | 渲染 | 仍显示 `思考 Medium` | P2 | ✅ |

## 10. 未落地用例（deferred）

| 编号 | 内容 | 原因 |
|------|------|------|
| T-E2E-01 | Playwright 两层菜单真实点击 E2E（task 9.4） | 沙箱内浏览器不可跑（同 agent-web `**/e2e/**` 既有失败） |
| T-E2E-02 | 真实多 provider HTTP 路由验证 | v0.2 未实现多 provider 路由（见 `provider-catalog.md` §10） |

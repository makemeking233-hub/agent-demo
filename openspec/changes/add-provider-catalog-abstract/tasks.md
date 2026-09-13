## 1. 后端 DTO 重构

- [ ] 1.1 `agent-web/.../api/dto/ProviderGroup.java` 新建 record:`id / name / models: List<ModelEntry>`
- [ ] 1.2 `agent-web/.../api/dto/ModelEntry.java` 新建 record:`id / name / supportsReasoning / reasoningEfforts: List<ReasoningEffort>`
- [ ] 1.3 `agent-web/.../api/dto/ReasoningEffort.java` 新建 record:`id / name / description: String?`(`@JsonInclude.NON_NULL`)
- [ ] 1.4 `agent-web/.../api/dto/ModelsResponse.java` 重写为 `List<ProviderGroup> providers`(移除 change A 的 `models: List<Model>` 平铺字段)
- [ ] 1.5 `agent-web/.../api/dto/SendRequest.java` 新增 `provider: String` 字段(`@JsonProperty("provider")`)
- [ ] 1.6 `agent-core/.../provider/ProviderRequest.java` 新增 `provider: String` 字段(与 model 并列)

## 2. 后端 ModelCatalog + ProviderCatalogService

- [ ] 2.1 `agent-web/.../api/catalog/ModelCatalog.java` 新建不可变类:`providers() / provider(id) / model(providerId, modelId) / supportedEfforts(providerId, modelId)`
- [ ] 2.2 `agent-web/.../api/catalog/ProviderCatalogProperties.java` 新建 `@ConfigurationProperties("agent.chat")` 配置类:`providers: List<ProviderGroup> + defaultProvider + defaultModel`(从 yaml 绑定)
- [ ] 2.3 `agent-web/.../api/catalog/ProviderCatalogService.java` 新建:`@PostConstruct` 从 `ProviderCatalogProperties` 构造 `ModelCatalog` 单例 bean;启动校验 `supportsReasoning=false` 必须 `reasoningEfforts=[]`,否则抛 `IllegalStateException`
- [ ] 2.4 `agent-web/.../api/ModelsController.java` 改注入 `ModelCatalog` 而非 `Environment`;返回 `ModelsResponse(catalog.providers())`
- [ ] 2.5 `agent-web/.../api/ModelsControllerTest.java` 重写测试:校验嵌套结构 + 启动校验 + trusted-host

## 3. 后端 application-web.yml 升级

- [ ] 3.1 `agent-web/src/main/resources/application-web.yml` 把 `agent.chat.supported-models: [deepseek-chat, deepseek-reasoner]` 替换为 `agent.chat.providers` 嵌套列表(deepseek / openai / anthropic 三 provider,各带 models)
- [ ] 3.2 yml 加 `agent.chat.default-provider: deepseek` + `agent.chat.default-model: deepseek-chat`
- [ ] 3.3 `ProviderCatalogService` 加 fallback 兼容旧 key `supported-models`(从 `[deepseek-chat, deepseek-reasoner]` 平铺转单 provider `deepseek`),标 `@Deprecated`,日志输出一次

## 4. 后端 AgentLoop setProvider + setSelection

- [ ] 4.1 `agent-core/.../core/AgentLoop.java` 新增 volatile `provider: String` 字段 + `setProvider(String)` 方法 + `setSelection(ModelSelection)` 复合方法 + `selection()` getter
- [ ] 4.2 `agent-core/.../core/ModelSelection.java` 新建 record:`provider / model / reasoningEffort`(与 dsh `ModelSelection` 对齐)
- [ ] 4.3 `AgentLoop` 构造 `ProviderRequest` 时合并 `provider + model + reasoningEffort` 三 volatile 字段
- [ ] 4.4 `agent-core/.../core/AgentLoopTest.java` 新增测试:`setSelection` 同时更新三字段 + 构造 ProviderRequest 时三字段都填充

## 5. 后端三 Provider 接受 provider 参数

- [ ] 5.1 `agent-core/.../provider/deepseek/DeepSeekProvider.java` 接受 `ProviderRequest.provider`(校验 == "deepseek",否则抛 `IllegalArgumentException`);baseURL 路由决策
- [ ] 5.2 `agent-core/.../provider/openai/OpenAiCompatibleProvider.java` 接受 `ProviderRequest.provider`;v0.1 仅路由 `openai` provider,其他抛 `UnsupportedOperationException`
- [ ] 5.3 `agent-core/.../provider/anthropic/AnthropicProvider.java` 接受 `ProviderRequest.provider`(校验 == "anthropic",否则抛)
- [ ] 5.4 三 Provider 各加单测:provider 校验 + provider 不匹配时抛异常

## 6. 后端 provider 推断

- [ ] 6.1 `agent-core/.../provider/ProviderInference.java` 新建工具类:`inferProvider(String model)`:根据前缀返回 provider(`o1` / `gpt-` → `openai`、`claude-` → `anthropic`、`deepseek-` → `deepseek`),无法推断返回 `null`
- [ ] 6.2 `agent-core/.../provider/ProviderInferenceTest.java` 测试各前缀正确推断
- [ ] 6.3 `ChatController.send` 调用 `ProviderInference.inferProvider(req.model())`,推断为 null 时从 yml `default-provider` 兜底
- [ ] 6.4 `ChatControllerTest` 测试推断逻辑 + fallback 路径

## 7. 后端 ChatStreamService + WebAgentRuntime 透传 provider

- [ ] 7.1 `agent-web/.../stream/ChatStreamService.java` 新增重载 `create(sessionId, provider, model, mode, workspace, reasoningEffort)` 把三参数传给 `WebAgentRuntime`
- [ ] 7.2 `agent-web/.../stream/WebAgentRuntime.java` 新增 `create(..., provider, model, reasoningEffort)` 方法,初始化 AgentLoop 后调 `loop.setSelection(new ModelSelection(provider, model, reasoningEffort))`
- [ ] 7.3 `agent-web/.../api/ChatController.java` 解析 `req.provider()` 后调用新重载;推断逻辑从 `ProviderInference` 取
- [ ] 7.4 `ChatControllerTest.java` / `ChatStreamServiceTest.java` 测试三参数透传

## 8. 前端类型升级

- [ ] 8.1 `agent-web/frontend/src/api/chat.ts` `ModelsResponse` 重写为 `providers: ProviderGroup[]`;删除 change A 的平铺 `models` 字段
- [ ] 8.2 `agent-web/frontend/src/api/types.ts`(新建)或 chat.ts 顶部:`ProviderGroup / ModelEntry / ReasoningEffort / ModelSelection` 四个 TS 类型定义
- [ ] 8.3 `chat.ts` `SendRequest` 加 `provider?: string` 字段;`ChatApi.send(req)` 透传 `req.provider`
- [ ] 8.4 `chat.ts` 加 `ModelSelection` 类型 + localStorage 读写工具函数 `readModelSelection() / writeModelSelection()`
- [ ] 8.5 `chat.ts` 现有单测重写:删除引用平铺 `models` 字段的测试,改为嵌套 `providers` 断言

## 9. 前端 ModelSelect 两层菜单升级

- [ ] 9.1 `agent-web/frontend/src/components/ModelSelect.tsx` 重写:接受 `value: ModelSelection` + `onChange: (next: ModelSelection) => void`;内部 state `selectedProvider` 局部;渲染 trigger + 两层面板(左 providers + 右 models);models 列表每项右侧嵌套 ReasoningEffortSelect
- [ ] 9.2 `agent-web/frontend/src/components/ModelSelect.module.css` 加 `.twoPanel / .providerList / .modelList / .active / .effortInline` 样式
- [ ] 9.3 `agent-web/frontend/src/components/ModelSelect.test.tsx` 重写:trigger 显示完整 ModelSelection(provider+model+effort)+ 两层菜单打开 + 切换 provider 刷新右面板 + 选 model 触发 onChange + effort 子下拉联动
- [ ] 9.4 `agent-web/frontend/src/components/ModelSelect.e2e.spec.ts`(playwright E2E):真实点击两层菜单切换 + 关闭弹层 + 重新打开保留状态

## 10. 前端 ReasoningEffortSelect prop 升级

- [ ] 10.1 `agent-web/frontend/src/components/ReasoningEffortSelect.tsx` 改 prop 接口:`options: ReasoningEffort[]`(替代 `model: ModelEntry`);从 options 渲染
- [ ] 10.2 `agent-web/frontend/src/components/ReasoningEffortSelect.module.css` 样式微调(无功能变更)
- [ ] 10.3 `agent-web/frontend/src/components/ReasoningEffortSelect.test.tsx` 重写:options 渲染 + value 对应 label + 空 options 隐藏

## 11. 前端 ChatPanel localStorage 升级

- [ ] 11.1 `agent-web/frontend/src/components/ChatPanel.tsx` state 从 `{model, reasoningEffort}` 改为 `ModelSelection`(含 provider)
- [ ] 11.2 `useEffect` 初始化:读 `readModelSelection()`,旧格式(无 provider)走推断 fallback,无效值 fallback 到 default
- [ ] 11.3 `setModelSelection` 调 `writeModelSelection(next)`
- [ ] 11.4 `startStream` 调 `api.send({content, session_id, permission_mode, provider, model, reasoning_effort})`
- [ ] 11.5 props 下发 TopBar(完整 ModelSelection)+ Composer(只下发 model.reasoningEfforts 数组)
- [ ] 11.6 `ChatPanel.test.tsx` 重写:测试新 schema localStorage + 旧格式 fallback + 校验失败 fallback + send 透传 provider

## 12. CLI /model 扩展 provider/model 路径

- [ ] 12.1 `agent-core/.../cli/SlashCommand.java` `/model` 命令改解析 `<provider>/<model>` 路径(用 `/` 分隔);无 provider 时从 `default-provider` 兜底
- [ ] 12.2 `/model reasoning` / `/model chat` 别名保留 + 内部映射到 `deepseek/deepseek-reasoner` / `deepseek/deepseek-chat`
- [ ] 12.3 `/model openai/o1` 完整路径测试 + `/model o1` 简写测试 + 别名向后兼容测试 + 非法 model 测试
- [ ] 12.4 `agent-core/.../cli/SlashCommandTest.java` 重写 `/model` 测试

## 13. 文档 + 四件套 + archive

- [ ] 13.1 `docs/provider-catalog.md` 新建(架构 + ModelSelection / ProviderGroup / ModelEntry / ReasoningEffort 数据形态 + 三 Provider baseURL 路由表 + dsh 兼容性 + change A → change B 迁移路径)
- [ ] 13.2 `docs/model-and-effort-dropdown.md` 升级:加注 "v0.2 起由 add-provider-catalog-abstract 升级为分层目录,本文档保留 v0.1 设计"
- [ ] 13.3 `docs/test-agent-demo/2026-09-13-add-provider-catalog-abstract/test-design.md` 写测试设计(BREAKING 迁移路径 + 两层菜单 UI E2E)
- [ ] 13.4 `docs/test-agent-demo/2026-09-13-add-provider-catalog-abstract/test-cases.md` 写用例表(覆盖所有 ADDED/MODIFIED Requirement)
- [ ] 13.5 `docs/test-agent-demo/2026-09-13-add-provider-catalog-abstract/test-report.md` 跑 `mvn verify` + `pnpm test` + `pnpm build` + playwright E2E 后写报告(jacoco LINE ≥80% / BRANCH ≥70% / 全绿)
- [ ] 13.6 `docs/test-agent-demo/2026-09-13-add-provider-catalog-abstract/test-review.md` 写复盘(BREAKING 迁移处理 / 两层菜单 UI 边界 / dsh 兼容性)
- [ ] 13.7 `docs/test-agent-demo/test-guide.md` §1 登记 + §2.11 详情
- [ ] 13.8 `openspec validate add-provider-catalog-abstract --type change --strict` 通过
- [ ] 13.9 commit(中文 Conventional Commits)+ push
- [ ] 13.10 `openspec archive add-provider-catalog-abstract --yes` 归档 + commit + push
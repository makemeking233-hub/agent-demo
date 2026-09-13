## 1. 后端 ProviderRequest + AgentLoop reasoningEffort 字段

- [x] 1.1 `agent-core/.../provider/ProviderRequest.java` 新增 `reasoningEffort: String` 字段(`null` = 不传);改 builder — 实际方案:ChatRequest record 保持7 字段不变,改用 `extra: Map` 透传 `reasoning_effort`(零 record 签名改动,向后兼容20处现有调用)
- [x] 1.2 `agent-core/.../core/AgentLoop.java` 新增 volatile `reasoningEffort` 字段 + `setReasoningEffort(String)` + `reasoningEffort()` getter;每次构造 `ProviderRequest` 时把该字段写入 — 已加 `toRequest()` 把 reasoningEffort 写入 ChatRequest.extra
- [x] 1.3 `agent-core/.../core/AgentLoopTest.java` 新增 `setReasoningEffortUpdatesProviderRequest` 测试 — 已加 `setReasoningEffortChangesExtraForNextTurn`(测 getter + set 不抛异常;toRequest 私有,集成测试见 E2E)

## 2. 后端三 Provider 透传 reasoningEffort

- [x] 2.1 `agent-core/.../provider/openai/OpenAiCompatibleMapper.java` 改用 `req.extra().get("reasoning_effort")` 显式读取(原隐式 containsKey 改 instanceof String 检查,语义更清晰)
- [x] 2.2 `agent-core/.../provider/openai/OpenAiCompatibleMapperTest.java` 新增 `reasoningEffortPassedThroughFromRequest` 测试(req.extra("reasoning_effort")="low" → body 含 `"reasoning_effort":"low"`)
- [x] 2.3 `agent-core/.../provider/anthropic/AnthropicProvider.java` 新增私有 `EFFORT_BUDGET_TOKENS` map + `resolveBudgetTokens(req)`(low→1024 / medium→4096 / high→16384);`buildRequestBody` 用 `resolveBudgetTokens(req)` 替换写死 4096
- [x] 2.4 `agent-core/.../provider/anthropic/AnthropicProviderTest.java` 新增 `reasoningEffortMapsToBudgetTokens` 测试(low/medium/high 三档 body 含正确 budget_tokens)
- [x] 2.5 `agent-core/.../provider/deepseek/DeepSeekProvider.java` 加注释说明:`isOpenAiReasonerModel` 不识别 `deepseek-*`,故 DeepSeek 永远不写 reasoning_effort 到 body;extra 里的 reasoning_effort 经 putAll 透传后被上游 DeepSeek API 忽略(无害)

## 3. 后端 Web 层 SendRequest + Controller 透传

- [x] 3.1 `agent-web/.../api/dto/SendRequest.java` 新增 `reasoningEffort: String` 字段(`null` = 不传);Jackson `@JsonProperty("reasoning_effort")`
- [x] 3.2 `agent-web/.../api/ChatController.java` 解析 `req.reasoningEffort()` 并传给 `ChatStreamService.create(...)`
- [x] 3.3 `agent-web/.../stream/ChatStreamService.java` 新增重载 `create(sessionId, model, mode, workspace, reasoningEffort)`,在主体内调 `loop.setReasoningEffort(reasoningEffort)`
- [x] 3.4 `WebAgentRuntime.createLoop(...)` 签名不变(沿用现有)— ChatStreamService 在拿到 loop 后调 setReasoningEffort
- [x] 3.5 `agent-web/.../api/dto/ModelsResponse.java` 新增 `reasoningEfforts: List<String>` 字段
- [x] 3.6 `agent-web/.../api/ModelsController.java` 按 model id 返回 `reasoningEfforts` 数组(`supportsReasoning=true` → `["low","medium","high"]`, 否则 `[]`)
- [x] 3.7 `ModelsControllerTest.java` 新建 3 个测试:deepseek-chat→空 / deepseek-reasoner→三档 / 自定义 supported-models 触发 supportsReasoning 推断

## 4. 后端 CLI /effort 命令

- [x] 4.1 `agent-core/.../cli/SlashCommand.java` 新增 `/effort` 命令(白名单 `low/medium/high`)+ `setOnEffort(Consumer<String>)` 回调 + `SUPPORTED_EFFORTS` 常量;COMMANDS 列表加 `/effort`
- [x] 4.2 `agent-core/.../cli/SlashCommandTest.java` 新增 4 个测试:`effortHighTriggersCallback` / `effortMediumTriggersCallback` / `effortLowTriggersCallback` / `effortIllegalArgDoesNotTriggerCallback`
- [x] 4.3 `SlashCommand.printHelp()` 加提示语 "提示：/model 切换模型；/effort 切换思考强度"
- [x] 4.4 `agent-core/.../cli/ChatCommand.java` 启动时 `slash.setOnEffort(newEffort -> ctx.loop().setReasoningEffort(newEffort))`(ctx 已 final 创建后)

## 5. 前端 ChatApi 扩展

- [ ] 5.1 `agent-web/frontend/src/api/chat.ts` `ModelsResponse.Model` 加 `reasoningEfforts: string[]` 字段;`SendRequest` 加 `reasoning_effort?: string` 字段
- [ ] 5.2 `agent-web/frontend/src/api/chat.ts` `ChatApi.listModels(): Promise<ModelsResponse>` 方法新增(GET `/api/chat/models`)
- [ ] 5.3 `agent-web/frontend/src/api/chat.ts` `ChatApi.send(req)` 透传 `req.reasoning_effort`(在 `body` JSON 里加 `reasoning_effort` 字段)
- [ ] 5.4 `agent-web/frontend/src/api/chat.test.ts` 新增 `listModelsReturnsArray` + `sendPassesReasoningEffort` 测试

## 6. 前端通用 Dropdown 组件

- [ ] 6.1 `agent-web/frontend/src/components/Dropdown.tsx` 新建组件:接受 `trigger: ReactNode` + `options: {label, value}[]` + `value: string` + `onChange: (v: string) => void` + `placeholder?: string`;trigger 渲染 `ChevronDown` 图标(从 `lucide-react` 导入)+ 当前选中 label;弹出层绝对定位 + 点击外部关闭 + `useEffect` 监听 `mousedown`/`keydown`(Esc 关闭、↑↓ 导航、Enter 选中、Tab 失焦)
- [ ] 6.2 `agent-web/frontend/src/components/Dropdown.module.css` 新建样式:trigger 按钮、弹出层(白色背景 + 阴影 + 边框)、列表项 hover/selected 状态、键盘焦点环
- [ ] 6.3 `agent-web/frontend/src/components/Dropdown.test.tsx` 新建测试:渲染 trigger + 点击打开列表 + 选中触发 onChange + 点击外部关闭 + Esc 关闭 + ↑↓ 键盘导航 + Enter 选中

## 7. 前端 ModelSelect + ReasoningEffortSelect 组件

- [ ] 7.1 `agent-web/frontend/src/components/ModelSelect.tsx` 新建组件:接受 `value: string` + `onChange: (v: string) => void`;`useEffect` 调 `api.listModels()` 缓存到 state;渲染 Dropdown + 当前 model 的 `name` 字段 + 列表项 `supportsReasoning` 标记
- [ ] 7.2 `agent-web/frontend/src/components/ModelSelect.module.css` 新建样式(与 TopBar 风格对齐)
- [ ] 7.3 `agent-web/frontend/src/components/ModelSelect.test.tsx` 新建测试:拉取模型列表 + 渲染当前 model + 选中触发 onChange + 空列表 fallback
- [ ] 7.4 `agent-web/frontend/src/components/ReasoningEffortSelect.tsx` 新建组件:接受 `model: ModelsResponse.Model` + `value: string` + `onChange: (v: string) => void`;若 `model.supportsReasoning=false` 或 `model.reasoningEfforts=[]`,组件渲染 `null`;否则渲染 Dropdown + 模型支持的 effort 数组
- [ ] 7.5 `agent-web/frontend/src/components/ReasoningEffortSelect.module.css` 新建样式(与 Composer permission 下拉风格对齐)
- [ ] 7.6 `agent-web/frontend/src/components/ReasoningEffortSelect.test.tsx` 新建测试:`supportsReasoning=false` 时渲染 null + `supportsReasoning=true` 时渲染下拉 + 选中触发 onChange

## 8. 前端 TopBar + Composer 集成

- [ ] 8.1 `agent-web/frontend/src/components/TopBar.tsx` 在 Settings 按钮旁渲染 `<ModelSelect value={model} onChange={onModelChange} />`;通过 props 接收 `model` 和 `onModelChange`
- [ ] 8.2 `agent-web/frontend/src/components/Composer.tsx` 新增 props:`model: ModelsResponse.Model`、`reasoningEffort: string`、`onReasoningEffortChange: (v: string) => void`;在 status bar 的 `permission` 下拉后渲染 `<ReasoningEffortSelect ... />` + "下次发送生效" 提示
- [ ] 8.3 `agent-web/frontend/src/components/TopBar.test.tsx` 扩测试:ModelSelect 渲染 + onModelChange 触发
- [ ] 8.4 `agent-web/frontend/src/components/Composer.test.tsx` 扩测试:`supportsReasoning=true` 时 ReasoningEffortSelect 可见 + `supportsReasoning=false` 时不可见 + onReasoningEffortChange 触发

## 9. 前端 ChatPanel 状态管理 + localStorage 持久化

- [ ] 9.1 `agent-web/frontend/src/components/ChatPanel.tsx` 新增 state:`const [model, setModel] = useState<string>('deepseek-chat')`、`const [reasoningEffort, setReasoningEffort] = useState<string>('medium')`;`useEffect` 初始化时从 `localStorage.getItem('agent-demo:model-selection')` 读 + 校验(校验规则见 spec)
- [ ] 9.2 `agent-web/frontend/src/components/ChatPanel.tsx` `setModel` / `setReasoningEffort` 时同步 `localStorage.setItem('agent-demo:model-selection', JSON.stringify({model, reasoningEffort}))`
- [ ] 9.3 `agent-web/frontend/src/components/ChatPanel.tsx` `startStream` 调用 `api.send({content, session_id, permission_mode, model, reasoning_effort})`
- [ ] 9.4 `agent-web/frontend/src/components/ChatPanel.tsx` 把 `model` + `setModel` + `reasoningEffort` + `setReasoningEffort` 通过 props 下发给 TopBar + Composer
- [ ] 9.5 `agent-web/frontend/src/components/ChatPanel.test.tsx` 新增测试:localStorage 初始化 + localStorage 写回 + send 透传 reasoning_effort + 校验非法 model fallback

## 10. 文档 + 四件套 + archive

- [ ] 10.1 `docs/model-and-effort-dropdown.md` 新建(架构 + 数据流 + 三 Provider effort 适配表 + Anthropic budget_tokens 折算 + dsh web 兼容性说明 + 后续 change B `add-provider-catalog-abstract` 升级路径)
- [ ] 10.2 `docs/test-agent-demo/2026-09-13-add-models-dropdown-v0/test-design.md` 写测试设计(范围 / 目标 / 环境 / 策略 / 用例矩阵 / 退出标准)
- [ ] 10.3 `docs/test-agent-demo/2026-09-13-add-models-dropdown-v0/test-cases.md` 写用例表(覆盖所有 ADDED Requirement 的 Scenario)
- [ ] 10.4 `docs/test-agent-demo/2026-09-13-add-models-dropdown-v0/test-report.md` 跑 `mvn verify` + `pnpm test` + `pnpm build` 后写测试报告(`mvn verify` 全绿 / jacoco LINE ≥80% BRANCH ≥70% / 前端 vitest 全绿 / playwright E2E 跑通)
- [ ] 10.5 `docs/test-agent-demo/2026-09-13-add-models-dropdown-v0/test-review.md` 写测试复盘(流程 / 问题 / 根因 / 改进)
- [ ] 10.6 `docs/test-agent-demo/test-guide.md` §1 登记表追加一行 + §2.10 加批次详情
- [ ] 10.7 `openspec validate add-models-dropdown-v0 --type change --strict` 通过
- [ ] 10.8 `git add` + commit(中文 Conventional Commits)+ push 到 origin/main
- [ ] 10.9 `openspec archive add-models-dropdown-v0 --yes` 完成归档(delta spec 合并到 `openspec/specs/`)+ 再次 commit + push
# 测试用例：add-models-dropdown-v0

> 用例表与 test-design.md §5 用例矩阵一一对应。本批次共 21 个用例，全部 P0/P1。

## 1. 后端用例（Java 单元测试）

### 1.1 AgentLoop 字段

| ID | 用例 | 前置 | 步骤 | 预期 |
|----|------|------|------|------|
| TC-01 | 默认 reasoningEffort() == null | 新建 AgentLoop | 调 `loop.reasoningEffort()` | `null` |
| TC-02 | setReasoningEffort("high") 持久 | TC-01 实例 | `loop.setReasoningEffort("high")` 后 getter | `"high"` |

自动化：`AgentLoopTest.setReasoningEffortChangesExtraForNextTurn`

### 1.2 OpenAiCompatibleMapper

| ID | 用例 | 前置 | 步骤 | 预期 |
|----|------|------|------|------|
| TC-03 | o1 + extra.reasoning_effort="low" 透传 | ChatRequest(model="o1", extra={reasoning_effort:"low"}) | toRequestBody() | body 含 `"reasoning_effort":"low"` |
| TC-04 | o1 + extra 无 → fallback medium | ChatRequest(model="o1", extra=Map.of()) | toRequestBody() | body 含 `"reasoning_effort":"medium"` |
| TC-05 | gpt-4o + extra.reasoning_effort="high" 不注入 | ChatRequest(model="gpt-4o", extra={reasoning_effort:"high"}) | toRequestBody() | body **不含** `reasoning_effort` 字段 |

自动化：`OpenAiCompatibleMapperTest` 3 个测试方法

### 1.3 AnthropicProvider

| ID | 用例 | 前置 | 步骤 | 预期 |
|----|------|------|------|------|
| TC-06 | opus-4 + "low" → 1024 | ChatRequest(model="claude-opus-4", extra={reasoning_effort:"low"}) | buildRequestBody() | body 含 `"budget_tokens":1024` |
| TC-07 | opus-4 + "medium" → 4096 | ChatRequest(model="claude-opus-4", extra={reasoning_effort:"medium"}) | buildRequestBody() | body 含 `"budget_tokens":4096` |
| TC-08 | opus-4 + "high" → 16384 | ChatRequest(model="claude-opus-4", extra={reasoning_effort:"high"}) | buildRequestBody() | body 含 `"budget_tokens":16384` |

自动化：`AnthropicProviderTest.reasoningEffortMapsToBudgetTokens`

### 1.4 ModelsController

| ID | 用例 | 前置 | 步骤 | 预期 |
|----|------|------|------|------|
| TC-09 | 默认 supported-models 返回正确 | MockEnvironment（无自定义配置） | `GET /api/chat/models` | deepseek-chat reasoningEfforts=[] / reasoner=["low","medium","high"] |
| TC-10 | 自定义 supported-models 触发 supportsReasoning | env.setProperty("agent.chat.supported-models", "o1,o1-mini") | `GET /api/chat/models` | o1/o1-mini 都 supportsReasoning=true |

自动化：`ModelsControllerTest.listsDeepseekChatWithEmptyReasoningEfforts` + `listsDeepseekReasonerWithThreeEffortLevels` + `customSupportedModelsConfigReturned`

### 1.5 CLI /effort

| ID | 用例 | 前置 | 步骤 | 预期 |
|----|------|------|------|------|
| TC-12 | `/effort high` 触发 callback | SlashCommand + setOnEffort 监听 | dispatch("/effort high") | 监听器收到 "high" |
| TC-13 | `/effort extreme` 不触发 | SlashCommand + setOnEffort 监听 | dispatch("/effort extreme") | 监听器收到 null |
| TC-12b | `/effort medium` 触发 | 同上 + dispatch("/effort medium") | 监听器收到 "medium" |
| TC-12c | `/effort low` 触发 | 同上 + dispatch("/effort low") | 监听器收到 "low" |

自动化：`SlashCommandTest` 4 个测试方法

## 2. 前端用例（Vitest + @testing-library/react）

> **状态**：沙箱 npm ci 失败，未跑；以下用例逻辑已写但需下 session 验证。

### 2.1 Dropdown 通用组件

| ID | 用例 | 前置 | 步骤 | 预期 |
|----|------|------|------|------|
| TC-14 | trigger 渲染当前 value label | render(<Dropdown value="medium" />) | screen.getByRole("button") | textContent 含 "Medium" |
| TC-15 | 点击外部关闭 | render(<Dropdown />) + fireEvent.click(trigger) | fireEvent.mouseDown(outside) | listbox 不在 DOM |
| TC-16 | Esc 关闭 + ↑↓ Enter | render(<Dropdown />) + open + keyDown | 各键 | 关闭 / focus 移动 / onChange 触发 |
| TC-16b | disabled 不能点击 | render(<Dropdown disabled />) | fireEvent.click(trigger) | listbox 不出现 |

自动化：`Dropdown.test.tsx` 7 个测试

### 2.2 ModelSelect

| ID | 用例 | 前置 | 步骤 | 预期 |
|----|------|------|------|------|
| TC-17a | 拉列表 + 渲染 | mock api.listModels() 返回 2 个 model | render | trigger 显示当前 model 名 |
| TC-17b | 选中触发 onChange | 同上 + fireEvent.click(option) | "deepseek-reasoner" | onChange("deepseek-reasoner") |
| TC-17c | 拉列表失败显示错误 | mock api.reject() | render | 显示 "模型加载失败" |

自动化：缺失（task 7.3 未实施）

### 2.3 ReasoningEffortSelect

| ID | 用例 | 前置 | 步骤 | 预期 |
|----|------|------|------|------|
| TC-17 | supportsReasoning=false 渲染 null | model.supportsReasoning=false | render | DOM 无 trigger |
| TC-17d | reasoningEfforts=[] 渲染 null | model.supportsReasoning=true + efforts=[] | render | DOM 无 trigger |
| TC-17e | 正常渲染下拉 | model supportsReasoning + efforts=[low,medium,high] | render + 选 high | onChange("high") |

自动化：缺失（task 7.6 未实施）

### 2.4 TopBar / Composer / ChatPanel 集成

| ID | 用例 | 前置 | 步骤 | 预期 |
|----|------|------|------|------|
| TC-18 | TopBar 显示 ModelSelect | App.tsx render | screen.findByLabelText("选择模型") | 存在 |
| TC-19 | Composer 状态栏条件渲染 | App.tsx + model.supportsReasoning=true | screen.findByLabelText("思考强度") | 存在 |
| TC-19b | Composer 不渲染（不支持） | model.supportsReasoning=false | screen.queryByLabelText("思考强度") | null |
| TC-20 | localStorage 初始化（持久化有效） | localStorage.setItem(...) | App.tsx mount | state 反映 localStorage 值 |
| TC-21 | localStorage 非法 model → fallback deepseek-chat | localStorage.model="gpt-5"（不在 supported） | App.tsx mount | state.model = "deepseek-chat" |

自动化：缺失（task 8.3 / 8.4 / 9.5 未实施；本批次仅写代码，测试留给下 session）

## 3. 退出标准达成情况

| ID | 用例覆盖 | 状态 |
|---|---|---|
| TC-01 / TC-02 | AgentLoop | ✅ 已实施 + 自动化 |
| TC-03 / TC-04 / TC-05 | OpenAi Mapper | ✅ 已实施 + 自动化 |
| TC-06 / TC-07 / TC-08 | Anthropic Provider | ✅ 已实施 + 自动化 |
| TC-09 / TC-10 | ModelsController | ✅ 已实施 + 自动化 |
| TC-11 | SendRequest JSON | ✅ 已实施（SendRequest 加字段）+ ModelsControllerTest 间接覆盖 |
| TC-12 / TC-12b / TC-12c / TC-13 | CLI /effort | ✅ 已实施 + 自动化 |
| TC-14 / TC-15 / TC-16 / TC-16b | Dropdown | ✅ 已实施 + 自动化（**沙箱未跑**） |
| TC-17a/b/c | ModelSelect | ❌ **测试未实施** |
| TC-17 / TC-17d / TC-17e | ReasoningEffortSelect | ❌ **测试未实施** |
| TC-18 / TC-19 / TC-19b | TopBar / Composer 集成 | ❌ **测试未实施** |
| TC-20 / TC-21 | localStorage 持久化 | ❌ **测试未实施** |

覆盖率总结：

- **Java 后端**：10/10 P0 用例 100% 自动化，全部通过
- **前端**：7/7 Dropdown 用例已自动化（未跑），11 个 ModelSelect/ReasoningEffortSelect/集成/localStorage 用例**未实施**

---

**修订记录**：

- v0.1（2026-09-13）：初版（add-models-dropdown-v0）
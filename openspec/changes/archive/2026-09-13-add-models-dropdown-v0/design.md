## Context

agent-demo 当前 Web UI(`apps/web/`)已有 `permission_mode` 下拉框挂在 Composer 状态栏,但**没有模型下拉框**;虽然后端 `SendRequest.model` 字段、`/api/chat/models` 端点、`AgentLoop.setModel()` 都已就绪(add-reasoning-thinking-streaming 已落地),**前端没接**。同时 OpenAI o1/o3 / Anthropic claude-4 thinking 等模型支持 `reasoning_effort` 参数控制思考强度,后端 `OpenAiCompatibleMapper` 硬编码注入 `"medium"`,用户无法配置。

本 change 把后端已经具备的能力完整暴露到 UI:前端下拉框选择 model + 可选 `reasoning_effort`,跨会话持久化,流中不切(发 send 前选好即可)。

后续 `add-provider-catalog-abstract` change 会做完整 provider 分层 catalog,本次先做"最小可行版本"对齐 dsh web 的 ModelSelect 体验。

## Goals / Non-Goals

**Goals:**
- 前端 TopBar 新增 ModelSelect 下拉框,从 `/api/chat/models` 拉列表,展示当前 model 名 + 点开显示全部模型
- 前端 Composer 状态栏新增 ReasoningEffortSelect 下拉框,仅当前 model `supportsReasoning=true` 时显示;三档 `low / medium / high`
- localStorage 持久化 `{model, reasoningEffort}`,新会话沿用上次值
- 后端 `SendRequest.reasoning_effort` 字段透传到 ProviderRequest
- `OpenAiCompatibleMapper` 原硬编码 `medium` 改为读取 `req.reasoningEffort()`(向后兼容 `null` = `medium`)
- `DeepSeekProvider` / `AnthropicProvider` 接受 `ProviderRequest.reasoningEffort`(Anthropic 折算 `thinking.budget_tokens`)
- `AgentLoop.setReasoningEffort()` + volatile `reasoningEffort` 字段 + 透传
- CLI 新增 `/effort <low|medium|high>` 命令;保留 `/model reasoning` 别名
- `ModelsResponse.Model` 新增 `reasoningEfforts: ["low","medium","high"]` 字段(`supportsReasoning=false` 时为 `[]`)

**Non-Goals:**
- 不重构现有 `ModelsResponse` 为 provider 分层目录(留给 change `add-provider-catalog-abstract`)
- 不做 provider 维度的 effort 等级动态化(本 change 统一三档固定值)
- 不支持流中切换 model / reasoningEffort(对齐用户 Q3 决策:send 前选好即可)
- 不写 JSONL message header 的 `reasoningEffort` 字段(对齐用户 Q9 决策:effort 是会话配置,不是消息属性)
- 不支持 reasoning_effort `minimal` / `xhigh` / `max` 等级(dsh pi-ai 有,但 v0.1 主流 provider 都没暴露)

## Decisions

### D1: 前端下拉位置 — TopBar(模型) + Composer(思考强度)

**理由**:对齐用户 Q4 决策。
- TopBar 全局常驻,适合放模型(全局可见、快速切会话)
- Composer 状态栏放思考强度(细粒度,跟现有 permission_mode 同一行)
- ModelSelect 切换 → Composer 联动 → 当前 model `supportsReasoning=false` 时 ReasoningEffortSelect 自动隐藏(动态渲染)

### D2: 通用 Dropdown 组件先行 — 自定义不引入新依赖

**理由**:对齐用户 Q5 决策,且项目无 Radix UI 依赖;复用项目已有的 `lucide-react`。
- 新建 `Dropdown.tsx` 组件:trigger 按钮 + 弹出列表(绝对定位)+ `ChevronDown` 图标 + 点击外部关闭 + 键盘 ↑↓ Enter Esc 导航
- 通过 props 接受 `trigger: ReactNode` + `options: {label, value}[]` + `value: string` + `onChange`
- v0.1 permission_mode 仍走 `<select>` 原生(避免一次性改 2 处 UI)

### D3: localStorage 持久化 key 形态

**理由**:跨会话沿用上次值,避免每次手动选。
- localStorage key: `agent-demo:model-selection`
- value JSON: `{"model":"deepseek-chat","reasoningEffort":"medium"}`(字段缺省时从 server default 兜底)
- ChatPanel 初始化时读 localStorage → 缺省走 server default → 监听下拉变化时 `setItem`
- 不做跨设备同步(纯本地)

### D4: reasoningEffort 默认值策略

**理由**:既要向后兼容(老调用方不传),又要给 UI 一个合理默认。
- 后端 `AgentLoop.reasoningEffort` 初始 `null`
- `OpenAiCompatibleMapper` 内部逻辑 `req.reasoningEffort() == null ? "medium" : req.reasoningEffort()`
- Anthropic 同理(null → 默认 budget_tokens 折算,OAI 风格 `medium` 不直接适用)
- 前端 UI:用户未手动选过 → 默认显示 `medium`(从 `ModelsResponse.Model.reasoningEfforts` 数组中间值)

### D5: Anthropic reasoningEffort 折算 budget_tokens

**理由**:Anthropic 不接 `reasoning_effort` 字符串,接 `thinking: {budget_tokens: int}`。
- low → 1024 token
- medium → 4096 token
- high → 16384 token
- AnthropicProvider 内部做此映射,顶层 API 仍传 `reasoningEffort: "low|medium|high"`(统一抽象)
- 折算常量放在 `AnthropicProvider.EffortBudgetTokens` 私有静态 map

### D6: 流中不切换 — 切了只对下次 send 生效

**理由**:对齐用户 Q3 决策;AgentLoop 状态机简化。
- 用户在下拉框换 model / effort → 只更新本地 state + 后端 AgentLoop volatile 字段,**不会**abort 当前 turn
- 当前 turn 内的 Provider 调用已经发出,中途换 model/effort 触发 Provider 状态不一致
- 提示 UI:Composer status bar 在下拉右侧显示"下次发送生效"小字

### D7: CLI /effort 命令与 /model 别名共存

**理由**:对齐用户 Q8 决策。
- `/effort <low|medium|high>`:切会话的 reasoningEffort(对齐 dsh)
- `/model <model_id>`:保留(单参数,切当前 model)
- `/model reasoning` / `/model chat`:保留别名(深友切换)
- 三者都通过 `MessageDelta` 推送切换成功提示(对齐现有 `/model` 实现)

### D8: 后端 schema 改动兼容性

**理由**:现有 web 客户端 / CLI 都不传 `reasoning_effort`,必须向后兼容。
- `SendRequest.reasoningEffort` 字段 `null` = 不传 → AgentLoop 保持 `null` → Provider 内部用 default
- `ModelsResponse.Model.reasoningEfforts` 新字段空数组 = 不暴露给前端下拉;前端不渲染
- `ProviderRequest.reasoningEffort` 新字段 `null` = Provider 内部 fallback;`OpenAiCompatibleMapper` 的 `reasoning_effort` 注入逻辑保持兼容

### D9: 不写 JSONL message header 的 reasoningEffort

**理由**:对齐用户 Q9 决策。
- `Message.Assistant` record 字段不变(`reasoningTokens` 保留)
- 不新增 `effort: String` 字段
- 如果未来需要"历史消息查看当时 effort",用 `SessionMetadata`(见 v0.2 候选)

## Risks / Trade-offs

- **[Risk] 三档固定 effort 等级 ≠ dsh web 的 7 档** → Mitigation: change `add-provider-catalog-abstract` 会做完整 provider 维度动态化;v0.1 主流 provider 都只暴露三档
- **[Risk] Anthropic reasoningEffort 折算 budget_tokens 是粗略估算** → Mitigation: 文档明确"折算表见 design D5";后续可加 provider 自定义预算配置
- **[Risk] TopBar ModelSelect 跟 Composer ReasoningEffortSelect 状态不同步** → Mitigation: ChatPanel 用单一 state 源(`useState<ModelSelection>`),通过 props 下发,避免两份 state
- **[Risk] localStorage 数据被污染导致 UI 错乱** → Mitigation: 启动时读 localStorage 时校验字段,无效值 fallback 到 server default;`reasoningEffort` 必须 ∈ `models[i].reasoningEfforts`,否则忽略
- **[Risk] Dropdown 通用组件首次写可能漏边界 case(点击外部、Esc、Tab 失焦)** → Mitigation: 写 Dropdown.test.tsx 覆盖 4 种边界,套用 `useEffect` `addEventListener` `mousedown` + `keydown`
- **[Risk] change A 完成后,change B 重构 ModelsResponse 时会涉及前端同步改动** → Mitigation: change A 已经在 ModelsResponse.Model 上预留 `reasoningEfforts` 数组字段,change B 只需把它升级为嵌套对象数组,不破坏现有消费方
- **[Trade-off] 流中不切 model — 用户切了不立即生效** → Mitigation: UI 明确提示"下次发送生效";后续 v0.2 可考虑"切了自动 abort 当前 turn 重新发起"
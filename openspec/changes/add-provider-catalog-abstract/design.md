## Context

change `add-models-dropdown-v0` 已落地最简版本,提供前端 ModelSelect + ReasoningEffortSelect,但其数据 schema(`ModelsResponse.models[]` 平铺)和服务端 ProviderRequest(model 单一字段,不带 provider)无法扩展更多 provider。本 change 升级到**完整 provider 分层目录**,对齐 dsh `ModelSelection` 接口,后续 v0.2+ 加 provider 无需改前端 UI。

dsh pi-ai 已有的相关类型(参考对齐,非复用):
- `ModelSelection = {provider: string, model: string, reasoningEffort?: string}`
- `ModelReasoningEffort = {id: string, name: string, description?: string}`
- `getSupportedThinkingLevels(model)` 动态返回支持的 level 数组

## Goals / Non-Goals

**Goals:**
- 后端 DTO 全面对齐 dsh `ModelSelection` / `ModelReasoningEffort` 形态
- 后端 `ModelCatalog` 抽象按 provider 分层(provider → models[] → reasoningEfforts[])
- 后端 `ProviderRequest` 加 `provider` 字段;`AgentLoop.setProvider()` 拆出
- 三 Provider(DeepSeek/OpenAI/Anthropic)都接受 `provider + model + reasoningEffort` 三参数
- 前端 ModelSelect 升级为**两层菜单**(DSH 风格 trigger + 两级面板)
- 前端 ReasoningEffortSelect 改为从 model 动态数组渲染(替代 change A 硬编码三档)
- 前端 localStorage schema 升级为 `ModelSelection` 形态
- CLI `/model <provider>/<model>` 支持完整路径
- `application-web.yml` 配置支持嵌套 providers 列表

**Non-Goals:**
- 不实现 pi-ai 的 `THINKING_LEVELS` 全集(`off / minimal / low / medium / high / xhigh / max`)——v0.1 主流 provider 实际只暴露三档,本 change 沿用三档;后续加 provider 时再补
- 不做 server-side persistence(用户的 modelSelection 仍只存 localStorage,不下发到后端持久化)
- 不做 session-level modelSelection 共享(每个 web tab 独立;Session 切换只切 sessionId 不切 modelSelection)
- 不改 change A 已经写好的 ReasoningEffortSelect 组件内部逻辑(只升级 prop 形态:`value: string` + `options: ReasoningEffort[]` 替代 `value + model.reasoningEfforts: string[]`)

## Decisions

### D1: 后端 ModelSelection DTO 完全对齐 dsh

**理由**:对齐 dsh web 接口形态,后续集成 pi-ai 库可直接复用。
```typescript
// agent-web/.../api/dto/ModelsResponse.java
record ModelsResponse(List<ProviderGroup> providers) {}

record ProviderGroup(
    @JsonProperty("id") String id,           // "deepseek" | "openai" | "anthropic"
    @JsonProperty("name") String name,       // "DeepSeek" | "OpenAI" | "Anthropic"
    @JsonProperty("models") List<ModelEntry> models) {}

record ModelEntry(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("supportsReasoning") boolean supportsReasoning,
    @JsonProperty("reasoningEfforts") List<ReasoningEffort> reasoningEfforts) {}

record ReasoningEffort(
    @JsonProperty("id") String id,           // "low" | "medium" | "high"
    @JsonProperty("name") String name,       // "Low" | "Medium" | "High"
    @JsonProperty("description") String description) {}
```

### D2: ModelCatalog 抽象从 yaml 启动时构建

**理由**:避免运行时反射 / 重新解析 yaml;启动时构建不可变 `ModelCatalog` 实例缓存。
```java
// agent-web/.../api/catalog/ModelCatalog.java
public final class ModelCatalog {
    private final List<ProviderGroup> providers;
    public ModelCatalog(List<ProviderGroup> providers) { ... }
    public List<ProviderGroup> providers() { ... }
    public Optional<ProviderGroup> provider(String id) { ... }
    public Optional<ModelEntry> model(String providerId, String modelId) { ... }
    public List<ReasoningEffort> supportedEfforts(String providerId, String modelId) { ... }
}
```
- 由 `ProviderCatalogService` 启动时从 yaml 读 `agent.chat.providers` 列表,反序列化为 `ProviderGroup[]`,包裹为不可变 `ModelCatalog`
- `ModelsController` 直接返回 `catalog.providers()`,不做运行时反射

### D3: application-web.yml 配置结构升级

**理由**:嵌套结构比平铺 string 列表可读性更好,且支持 per-model reasoningEfforts 配置。
```yaml
agent:
  chat:
    default-provider: deepseek
    default-model: deepseek-chat
    providers:
      - id: deepseek
        name: DeepSeek
        models:
          - id: deepseek-chat
            name: DeepSeek Chat
            supports-reasoning: false
            reasoning-efforts: []
          - id: deepseek-reasoner
            name: DeepSeek Reasoner
            supports-reasoning: true
            reasoning-efforts: []
      - id: openai
        name: OpenAI
        models:
          - id: o1
            name: o1
            supports-reasoning: true
            reasoning-efforts:
              - { id: low, name: Low }
              - { id: medium, name: Medium }
              - { id: high, name: High }
```
- 旧 key `supported-models: [deepseek-chat, deepseek-reasoner]` 保留兼容(若 `providers` 缺省时从旧 key 平铺转换,标记 `@Deprecated`)

### D4: ProviderRequest provider 字段

**理由**:Provider 路由决策需要 provider(虽然 v0.1 大部分请求都路由到同一个 baseURL,但 Anthropic / OpenAI baseURL 不同,需要 provider 区分)。
```java
// agent-core/.../provider/ProviderRequest.java
public record ProviderRequest(
    String provider,        // 新增: "deepseek" | "openai" | "anthropic"
    String model,
    String reasoningEffort, // 来自 change A
    Map<String, Object> extra,
    AbortSignal abort) {}
```

### D5: AgentLoop 拆 setProvider + setModel

**理由**:对齐 dsh `ModelSelection = {provider, model}` 形态,切换模型时可以一并切换 provider(例如从 deepseek-chat 切到 o1 时,provider 从 deepseek → openai)。
- `setProvider(String newProvider)` + `setModel(String newModel)` 两个方法独立 volatile
- `AgentLoop.setSelection(ModelSelection sel)` 复合方法(可选,前端调一次同时切两个)
- 每次构造 `ProviderRequest` 时合并 `provider + model + reasoningEffort` 三 volatile 字段

### D6: 前端两层菜单 UI 架构(对齐 dsh web)

**理由**:DSH web `ModelSelect` 实现就是 trigger + 两级面板。
```typescript
// agent-web/frontend/src/components/ModelSelect.tsx
<Dropdown trigger={<TriggerButton model={currentModel.name} />}>
  <div className={styles.twoPanel}>
    {/* 左面板:provider 列表 */}
    <ul className={styles.providerList}>
      {providers.map(p => (
        <li key={p.id} className={p.id === currentProvider ? styles.active : ''}
            onClick={() => setSelectedProvider(p.id)}>
          {p.name}
        </li>
      ))}
    </ul>
    {/* 右面板:当前 provider 的 models + reasoningEfforts 联动 */}
    <ul className={styles.modelList}>
      {selectedProvider.models.map(m => (
        <li key={m.id}>
          <span onClick={() => selectModel(m)}>{m.name}</span>
          {m.supportsReasoning && m.reasoningEfforts.length > 0 && (
            <ReasoningEffortSelect value={effort} options={m.reasoningEfforts}
                                    onChange={selectEffort} />
          )}
        </li>
      ))}
    </ul>
  </div>
</Dropdown>
```
- 组件接受 `value: ModelSelection` + `onChange: (next: ModelSelection) => void`
- 内部维护 `selectedProvider` 局部 state;切换 provider 时自动聚焦到该 provider 的第一个 model

### D7: ReasoningEffortSelect prop 升级

**理由**:从 change A 的硬编码 `["low","medium","high"]` 升级为动态 `ReasoningEffort[]`。
- `change A` 形态:`<ReasoningEffortSelect model={...} value={effort} onChange={setEffort} />`,内部读 `model.reasoningEfforts: string[]`
- `change B` 形态:接收 `options: ReasoningEffort[]`(替代读 model),从 caller 传入(Dropdown 用法统一)
- 仍支持 `supportsReasoning=false` 时返回 `null`

### D8: localStorage schema 升级为 ModelSelection

**理由**:对齐 DTO + 后续跨设备同步更容易。
- key 不变 `agent-demo:model-selection`
- value 形态:`{"provider":"deepseek","model":"deepseek-chat","reasoningEffort":"medium"}`(改 provider 字段)
- 校验规则:三字段都必须在 catalog 中存在,否则 fallback 到 server default
- 读 change A 旧格式 `{"model":"...","reasoningEffort":"..."}`(无 provider 字段)时,从 `default-provider` 兜底

### D9: BREAKING schema 迁移路径

**理由**:虽然破坏现有 schema,但通过 `agent-web` 同一 session 同步升级避免长期不一致。
- `ModelsResponse` 字段 `models[]` → `providers[]`(breaking)
- `application-web.yml` `supported-models` → `providers`(breaking)
- 前端 ModelSelect 组件 prop 接口变
- CLI `/model` 接受新路径 `/model <provider>/<model>`(旧 `/model <modelId>` 仍兼容,从 `default-provider` 补 provider)

## Risks / Trade-offs

- **[Risk] BREAKING schema 改动让外部 consumer 不可用** → Mitigation:change A 上线时间不长,目前无外部 consumer;本次同 session 前后端同步升级
- **[Risk] ProviderCatalogService 启动时解析 yaml 失败导致 web 起不来** → Mitigation:在 `application-web.yml` 用 `@ConfigurationProperties` + `bootstrap.yml` 校验,启动失败时 fail-fast + 显式错误
- **[Risk] 两层菜单 UI 在小屏幕 TopBar 上排版困难** → Mitigation:弹层面板宽度固定 480px,TopBar trigger 按钮仅显示 `Model.name`,不全显示 provider 名
- **[Risk] ReasoningEffortSelect prop 升级破坏 change A 单测** → Mitigation:同时改 test 文件,移除 change A 的硬编码 `["low","medium","high"]` 引用,改用 `[{id:"low",name:"Low"},...]` mock
- **[Risk] ModelEntry.reasoningEfforts 数组配置写错(provider 给 deepseek-reasoner 也填了 efforts 但 DeepSeek 不接 reasoning_effort 参数)** → Mitigation:`ProviderCatalogService` 启动时校验:`supportsReasoning=false` 必须 `reasoningEfforts=[]`;违反抛 startup error
- **[Risk] 三 Provider 改接受 provider 参数后,旧调用方不传 provider 会 NPE** → Mitigation:`AgentLoop.provider` volatile 字段初始值 = `default-provider` yaml 配置;`ProviderRequest.provider` 缺省时从 `model` 名称推断(例如 `deepseek-chat` → `deepseek`)
- **[Trade-off] change B 工作量比 change A 大** → Mitigation:change A 已经把 UI 基础组件 Dropdown / Composer / TopBar 集成好,change B 主要是数据 schema + 两层菜单 UI 升级,预估 600-900 行代码 / 7-9 个 task
# provider-catalog — delta spec

## MODIFIED Requirements

### Requirement: ModelEntry DTO

后端 SHALL 定义 `ModelEntry` record,字段:`id: String`、`name: String`、`supportsReasoning: boolean`、`reasoningEfforts: List<ReasoningEffort>`(空数组 = 不支持 reasoning effort 选项)、`defaultReasoningEffort: String?`(本模型默认档位 id;缺省 null)。

`defaultReasoningEffort` SHALL 仅在 `supportsReasoning=true` 时有意义,且非空时必须命中本模型 `reasoningEfforts` 中某个 `id`。

#### Scenario: ModelEntry reasoningEfforts 数组空表示不支持

- **WHEN** `ModelEntry(id="legacy-text-model", supportsReasoning=false, reasoningEfforts=[], defaultReasoningEffort=null)`
- **THEN** 序列化为 `{"id":"legacy-text-model","name":"...","supportsReasoning":false,"reasoningEfforts":[]}`
- **AND** 前端 ReasoningEffortSelect 接收此 model 时返回 null

#### Scenario: 默认档位随目录下发

- **WHEN** provider `deepseek` 的 model `deepseek-flash` 配置
  `reasoning-efforts: [{id:low},{id:high},{id:max}]` 且 `default-reasoning-effort: high`
- **THEN** `GET /api/chat/models` 中该 model 的 `defaultReasoningEffort` 为 `"high"`
- **AND** 前端在历史档位失效时选 `high` 而非档位数组首项 `low`

#### Scenario: 未配置默认档位时不输出该字段

- **WHEN** 某 model 只配 `reasoning-efforts` 不配 `default-reasoning-effort`
- **THEN** JSON 中 `defaultReasoningEffort` 缺省(`@JsonInclude.NON_NULL`)
- **AND** 前端退回 `reasoningEfforts[0].id`

## ADDED Requirements

### Requirement: DeepSeek 官方目录基线

`application-web.yml` 的 `agent.chat.providers` SHALL 以 DeepSeek 官方
`GET https://api.deepseek.com/models` 为基线,当前 SHALL 恰好包含下列 2 个模型,且每个模型的
档位 SHALL 为 `low` / `high` / `max`(默认 `high`):

| id | name | 能力 |
|---|---|---|
| `deepseek-flash` | `DeepSeek-V4.1-Flash` | 支持 reasoning,三档,默认 high |
| `deepseek-v4-pro` | `DeepSeek-V4-Pro` | 支持 reasoning,三档,默认 high |

目录 SHALL NOT 再包含已失效的 `deepseek-v4-flash`、项目虚构的 `deepseek-reasoner`,或历史
停用 id `deepseek-chat`。`agent.chat.default-model` SHALL 为 `deepseek-flash`。

#### Scenario: 目录只含官方两个模型

- **WHEN** 调用 `GET /api/chat/models`
- **THEN** `providers[0].models` 的 id 集合恰为 `{deepseek-flash, deepseek-v4-pro}`
- **AND** `defaultProvider` 为 `deepseek`、`defaultModel` 为 `deepseek-flash`
- **AND** 两个模型的 `reasoningEfforts` 的 id 集合均为 `{low, high, max}`

#### Scenario: 旧 id 不再是合法模型

- **WHEN** 客户端发 `{"content":"hi","model":"deepseek-reasoner"}`(旧 id)
- **THEN** 后端返回 400,响应含 `requested: deepseek-reasoner` 与可用的 model id 列表
- **AND** 不创建任何流、不向上游发请求

#### Scenario: 官方档位中没有 medium

- **WHEN** 客户端发 `{"reasoning_effort":"medium"}`
- **THEN** 目录中不存在 id 为 `medium` 的档位
- **AND** CLI `/effort medium` 被拒并提示 `low / high / max`

#### Scenario: 真实 yml 内容被测试守护

- **WHEN** 自动化测试读取**真实的** `agent-web/src/main/resources/application-web.yml`
      并绑定为 `ProviderCatalogProperties`
- **THEN** 断言 model id 集合恰为 `{deepseek-flash, deepseek-v4-pro}`、档位恰为
       `{low, high, max}`、`defaultModel` 为 `deepseek-flash`
- **AND** 任何人改动该 yml 使其偏离基线时,门禁变红(不允许只用内联构造的假数据测目录)
- **AND** 理由:目录历史上正是因为没有测试读真实 yml 而静默漂移

### Requirement: 目录配置自洽校验

`ProviderCatalogService` SHALL 在启动时校验目录配置自洽,任一项不满足 SHALL 抛
`IllegalStateException` 使 Spring 启动失败(Fail-Closed),错误消息 SHALL 含字段路径。

校验项:
- `supports-reasoning=false` 时 `reasoning-efforts` 必须为空
- `reasoning-efforts` 各项 `id` 不得为空
- `default-reasoning-effort` 非空时必须命中本模型 `reasoning-efforts` 的 id
- `default-provider` / `default-model` 必须能在目录中匹配到

> 本校验针对**本地配置自相矛盾**,与「官方目录漂移自检」(仅 WARN)性质不同,处置也不同。

#### Scenario: 默认档位不在档位列表内

- **WHEN** yml 配 `reasoning-efforts: [{id:low},{id:high}]` 但 `default-reasoning-effort: max`
- **THEN** 启动抛 `IllegalStateException`
- **AND** 错误消息含 `provider[deepseek].models[deepseek-flash]` 与 `default-reasoning-effort=max`

#### Scenario: 不支持 reasoning 却指定默认档位

- **WHEN** yml 配 `supports-reasoning: false` + `reasoning-efforts: []` + `default-reasoning-effort: high`
- **THEN** 启动抛 `IllegalStateException`
- **AND** 错误消息指出 `supports-reasoning=false` 时不得指定默认档位

#### Scenario: 默认模型指向已删除的旧 id

- **WHEN** yml 配 `default-model: deepseek-reasoner`(不在目录中)
- **THEN** 启动抛 `IllegalStateException`
- **AND** 错误消息含 `default-model` 与目录中现有的 model id 列表

### Requirement: 模型上下文窗口与输出上限

`DeepSeekProvider` SHALL 声明与官方 `/models` 一致的上下文窗口与最大输出:
`contextWindow() == 1_048_576`、`maxOutputTokens() == 393_216`。

`ContextCompressor` 的压缩阈值 SHALL 按
`contextWindow() - maxOutputTokens() - autoCompactBuffer` 动态计算,SHALL NOT 硬编码窗口大小,
以便常量变更后自动重基准。

#### Scenario: DeepSeek 常量对齐官方

- **WHEN** 读取 `DeepSeekProvider.contextWindow()` 与 `maxOutputTokens()`
- **THEN** 分别为 `1_048_576` 与 `393_216`

#### Scenario: 压缩阈值随常量自动重基准

- **WHEN** 注入的 provider `contextWindow()=1_048_576`、`maxOutputTokens()=393_216`、
      `autoCompactBuffer=20_000`
- **THEN** 压缩阈值为 `635_360`,无需修改 `ContextCompressor` 代码
- **AND** 历史估算 token 未达阈值时不触发压缩

### Requirement: 每轮 max_tokens 派生自 provider

`AgentLoop` 构造每轮 `ChatRequest` 时,`maxTokens` SHALL 取注入 provider 的
`maxOutputTokens()`,SHALL NOT 使用类内硬编码常量。provider 报出的上限变更后,下一轮请求
SHALL 立即跟随,不需要改 `AgentLoop`。

#### Scenario: 请求体使用 provider 上限

- **WHEN** 注入的 provider `maxOutputTokens()` 返回 `393_216`
- **THEN** `toRequest()` 构造的 `ChatRequest.maxTokens()` 为 `393_216`

#### Scenario: 换 provider 后自动跟随

- **WHEN** 注入的 provider `maxOutputTokens()` 返回 `1_234`
- **THEN** `ChatRequest.maxTokens()` 为 `1_234`(证明不是硬编码 8192)

#### Scenario: 上限变更不需要改 AgentLoop

- **WHEN** 只修改 provider 实现里的上限常量并重启
- **THEN** 每轮请求的 `max_tokens` 随之变化
- **AND** `AgentLoop` 源码无任何模型上限数字

### Requirement: 官方目录漂移自检

后端 SHALL 在应用就绪时(不早于 `ModelCatalog` 加载完成)拉取 DeepSeek 官方
`GET {base-url}/models`,与本地上文目录比对 id 集合与档位集合;不一致时 SHALL 输出一条 WARN
日志并列出双向差异。该自检 SHALL NOT 阻断启动。

自检 SHALL 在下列情况静默跳过(不 WARN、不抛异常):未配置 API key、HTTP 401/403、连接超时、
DNS 失败、上游 5xx。上游返回非法 JSON 时 SHALL 输出 WARN 一行后继续启动。

自检 SHALL 可通过 `agent.chat.drift-check.enabled=false` 整体关闭;默认开启。请求超时
SHALL 可配(`agent.chat.drift-check.timeout-ms`,默认 3000)。

#### Scenario: 官方新增模型时告警但不阻断

- **WHEN** 官方 `/models` 返回 `deepseek-flash`、`deepseek-v4-pro`、`deepseek-v5`
- **THEN** 日志输出 WARN,含「官方有本地无: deepseek-v5」
- **AND** Spring 启动**成功**,`/api/chat/models` 仍返回本地目录(2 个模型)
- **AND** 未自动修改任何配置

#### Scenario: 本地多出已下线模型时告警

- **WHEN** 本地目录含 `deepseek-v4-pro` 而官方只返回 `deepseek-flash`
- **THEN** 日志输出 WARN,含「本地有官方无: deepseek-v4-pro」
- **AND** 启动成功

#### Scenario: 档位集合漂移时告警

- **WHEN** 官方某模型档位为 `{low, high}` 而本地配置为 `{low, high, max}`
- **THEN** 日志输出 WARN,含该 model id 与两侧档位集合

#### Scenario: 上游不可达时静默跳过

- **WHEN** 自检请求超时 / 返回 500 / 无 API key
- **THEN** 不输出 WARN、不抛异常
- **AND** Spring 启动成功

#### Scenario: 关闭开关后不发请求

- **WHEN** `agent.chat.drift-check.enabled=false`
- **THEN** 自检完全不发 HTTP 请求
- **AND** 无任何自检相关日志

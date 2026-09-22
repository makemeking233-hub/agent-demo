## MODIFIED Requirements

### Requirement: Memory 作用域索引与召回

系统 SHALL 让每条记忆条目携带其所属 scope，并在解析索引、执行召回、序列化索引时按 scope 区分处理。系统 SHALL 将召回结果（而非全量索引）注入 system prompt 的记忆段，并在字面重叠命中不足时使用 sideQuery 语义补充。

系统 SHALL 按**当前轮次的用户提问**执行召回，即在每一轮请求组装时以该轮的 user 消息作为 query 生成记忆段，而非在进程启动时一次性生成后复用。system prompt 中除记忆段以外的部分（身份、行为规范、运行时存储、附加指引）SHALL 在启动期生成一次并可跨轮复用。

系统 SHALL 提供配置开关以关闭上述动态召回行为；关闭时系统 SHALL 回退为「启动期注入各 scope 全量索引（截断至 200 行 / 25KB）」的兼容行为。

#### Scenario: 条目携带 scope

- **WHEN** 一条记忆被解析为 `MemoryEntry`
- **THEN** 该条目包含其来源 scope（USER / PROJECT / LOCAL），跨 scope 的条目不混淆

#### Scenario: 召回限定 scope

- **WHEN** 对某个 scope 执行召回查询
- **THEN** 仅在该 scope 的候选条目内评分，不跨 scope 混合；各 scope 独立返回命中的条目

#### Scenario: 召回结果注入记忆段

- **WHEN** 组装 system prompt 记忆段且某 scope 存在候选条目
- **THEN** 记忆段包含该 scope 下召回命中的条目（标题、描述、scope 与路径），而非全量索引文本

#### Scenario: 无命中降级

- **WHEN** 某 scope 的候选条目未命中任何召回
- **THEN** 记忆段为该 scope 显示空占位（不报错、不注入空列表），其余 scope 正常

#### Scenario: 按当轮提问召回

- **WHEN** 用户在第 N 轮输入一条与前一轮语义不同的消息
- **THEN** 该轮请求的 system prompt 记忆段依据第 N 轮的消息召回，与该消息相关的条目被注入；不因复用启动期结果而遗漏

#### Scenario: 同一轮工具迭代不重复召回

- **WHEN** 同一轮对话因工具调用触发多次请求组装
- **THEN** 该轮内仅在首次组装时执行一次召回，后续请求复用同一记忆段，不重复解析索引、不重复发起 sideQuery 调用

#### Scenario: 动态召回可关闭并回退

- **WHEN** 配置 `memory.dynamicRetrieval=false`
- **THEN** 系统不执行按轮召回，改为在启动期注入各 scope 的全量索引（截断至 200 行 / 25KB），行为与关闭前保持一致

### Requirement: sideQuery 语义召回补充

系统 SHALL 在字面 token 重叠召回命中不足时，复用当前 LLM provider 发起一次轻量模型调用，从候选条目中挑选与查询最相关的 K 条作为补充，与字面结果并集去重后注入记忆段。

上述调用 SHALL 发生在**每一轮请求组装时**（由该轮的 user 消息触发），使该补充行为在实际运行路径上可达。系统 SHALL 保证同一轮内至多发起一次该调用。

#### Scenario: 字面命中充足时不调用 provider

- **WHEN** 字面 token 重叠召回命中的条目数已达到所需上限
- **THEN** 不发起 sideQuery 的 LLM 调用，直接使用字面召回结果

#### Scenario: 字面命中不足时发起 sideQuery

- **WHEN** 候选条目数超过阈值且字面命中数低于下限
- **THEN** 复用当前 provider 调用一次选择，返回补充条目，并与字面结果并集去重

#### Scenario: sideQuery 可关闭

- **WHEN** 配置 `memory.sideQuery.enabled=false`
- **THEN** 不发起 sideQuery 调用，仅依赖字面 token 重叠召回

#### Scenario: sideQuery 失败静默降级

- **WHEN** sideQuery 的 provider 调用失败或超时
- **THEN** 记录 WARN 并仅用字面召回结果，不阻断记忆段生成

#### Scenario: 召回链路在生产装配路径上可达

- **WHEN** 通过 agent 装配入口（`AgentLoopFactory.buildLoop`）构建 agent 并发起一轮对话，且该轮 user 消息与某条记忆条目相关
- **THEN** 该轮请求的 system prompt 记忆段包含召回命中的条目（表现为带 `(relevant)` 标记的 scope 小节），而非全量索引文本

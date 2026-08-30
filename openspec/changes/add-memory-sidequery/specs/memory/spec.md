# memory Specification (delta)

> 本文件是 `add-memory-sidequery` 的 delta spec。在 archive 时合并到 `openspec/specs/memory/spec.md`。

## MODIFIED Requirements

### Requirement: Memory 作用域索引与召回

系统 SHALL 让每条记忆条目携带其所属 scope，并在解析索引、执行召回、序列化索引时按 scope 区分处理。系统 SHALL 将召回结果（而非全量索引）注入 system prompt 的记忆段，并在字面字面重叠命中不足时使用 sideQuery 语义补充。

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

## ADDED Requirements

### Requirement: sideQuery 语义召回补充

系统 SHALL 在字面 token 重叠召回命中不足时，复用当前 LLM provider 发起一次轻量模型调用，从候选条目中挑选与查询最相关的 K 条作为补充，与字面结果并集去重后注入记忆段。

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

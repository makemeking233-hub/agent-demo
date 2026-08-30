# memory Specification (delta)

> 本文件是 `add-memory-three-scope` 的 delta spec。在 archive 时合并到 `openspec/specs/memory/spec.md`。

## ADDED Requirements

### Requirement: 分层长期记忆（三 scope）

系统 SHALL 支持长期记忆按作用域（scope）分层组织与注入，提供 USER / PROJECT / LOCAL 三类作用域：

- **USER**：跨项目全局，持久化到 `~/.agent-demo/memory/`。
- **PROJECT**：项目专属，持久化到项目仓库下 `.agent-demo/memory/`。
- **LOCAL**：仅当前会话有效，不入磁盘，会话结束即丢弃。

系统 SHALL 在组装 system prompt 时按 scope 优先级合并三类的记忆索引与召回内容，并在记忆段中标注各 scope 的存放路径，供模型正确写入。

#### Scenario: USER scope 读取与注入

- **WHEN** 会话启动且存在 `~/.agent-demo/memory/MEMORY.md`
- **THEN** system prompt 的记忆段包含 USER scope 的索引内容（`MEMORY.md`），并标注其存放路径

#### Scenario: PROJECT scope 读取与注入

- **WHEN** 会话工作目录（cwd）对应的 `.agent-demo/memory/MEMORY.md` 存在
- **THEN** system prompt 的记忆段同时包含 PROJECT scope 的索引内容

#### Scenario: 工作目录无 PROJECT 记忆不报错

- **WHEN** 当前工作目录下不存在 `.agent-demo/memory/`
- **THEN** 记忆段仅含 USER（及可选 LOCAL）内容，不抛异常、不注入空的 PROJECT 段

#### Scenario: LOCAL scope 会话内有效

- **WHEN** 会话中写入一条 LOCAL 记忆
- **THEN** 该记忆在当前会话随后的 system prompt 中被注入；会话结束后不再存在（文件系统无残留）

#### Scenario: 记忆写入提示含实际 scope 路径

- **WHEN** system prompt 记忆段生成
- **THEN** 记忆段对每个已启用的 scope 都展示该 scope 的实际存放路径（非硬编码 `~/.agent-demo/memory/<name>.md`），且各路径可区分

### Requirement: Memory 作用域索引与召回

系统 SHALL 让每条记忆条目携带其所属 scope，并在解析索引、执行召回、序列化索引时按 scope 区分处理。

#### Scenario: 条目携带 scope

- **WHEN** 一条记忆被解析为 `MemoryEntry`
- **THEN** 该条目包含其来源 scope（USER / PROJECT / LOCAL），跨 scope 的条目不混淆

#### Scenario: 召回限定 scope

- **WHEN** 对某个 scope 执行召回查询
- **THEN** 仅在该 scope 的候选条目内评分，不跨 scope 混合；各 scope 独立返回命中的条目

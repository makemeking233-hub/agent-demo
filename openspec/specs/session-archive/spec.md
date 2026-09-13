# session-archive Specification

## Purpose
TBD - created by archiving change auto-archive-stale-sessions. Update Purpose after archive.
## Requirements
### Requirement: 超期会话自动归档

系统 SHALL 把最后活动时间早于保留期的会话自动移入归档，保留期默认 7 天且可配置。

#### Scenario: 超过保留期被归档

- **WHEN** 某会话最后活动时间早于「当前时间 − 保留期」
- **THEN** 该会话被移入归档
- **AND** 其侧车元数据一并移动

#### Scenario: 未超期保留

- **WHEN** 某会话最后活动时间在保留期之内
- **THEN** 该会话保持原位，不被归档

#### Scenario: 边界

- **WHEN** 某会话最后活动时间恰好等于「当前时间 − 保留期」
- **THEN** 该会话不被归档（仅严格早于才归档）

#### Scenario: 跳过进行中的会话

- **WHEN** 某会话存在活动流
- **THEN** 即使已超期也不归档

#### Scenario: 单个失败不影响其余

- **WHEN** 某个会话归档失败
- **THEN** 其余会话仍继续处理
- **AND** 失败被记录

### Requirement: 自动归档可配置与可关闭

自动归档 SHALL 支持开关、保留期与调度间隔三项配置，且 SHALL 在应用启动后执行一次并按间隔重复。

#### Scenario: 关闭后不归档

- **WHEN** 配置关闭自动归档
- **THEN** 不执行任何归档动作

#### Scenario: 启动后执行一次

- **WHEN** 应用启动完成
- **THEN** 立即执行一次整理，而不必等到第一个调度周期

#### Scenario: 覆盖所有工作区

- **WHEN** 存在多个工作区
- **THEN** 每个工作区的会话目录都被整理

### Requirement: 归档按时间分档

归档列表 SHALL 按最后活动时间距今的相对天数分档，档位边界 SHALL 与保留期阈值一致。

#### Scenario: 分档边界

- **WHEN** 归档会话的最后活动时间距今分别为 3 天、10 天、20 天、100 天
- **THEN** 依次归入「最近归档」「上周」「本月」「更早」

#### Scenario: 恰好落在档边界

- **WHEN** 距今恰好 7 天、14 天、30 天
- **THEN** 依次归入「上周」「本月」「更早」（区间为左闭右开）

#### Scenario: 手动归档的近期会话

- **WHEN** 用户手动归档一个刚结束的会话
- **THEN** 它归入「最近归档」而不是「上周」

### Requirement: 归档列表按分档分组展示

前端归档视图 SHALL 按时间分档分组展示归档会话，档内保持最近活动在前的顺序。

#### Scenario: 分组渲染

- **WHEN** 归档会话跨越多个档位
- **THEN** 按档位分组显示，每档带标题
- **AND** 档内按最后活动时间降序

#### Scenario: 空档不显示

- **WHEN** 某档没有任何归档会话
- **THEN** 不显示该档标题


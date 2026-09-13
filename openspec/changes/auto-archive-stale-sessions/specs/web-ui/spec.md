## ADDED Requirements

### Requirement: 归档列表项带时间分档

归档会话列表项 SHALL 携带时间分档字段，取值与 `session-archive` 定义的档位一致；非归档列表 SHALL NOT 依赖该字段。

#### Scenario: 归档列表带分档

- **WHEN** 请求归档会话列表
- **THEN** 每一项都带有时间分档字段

#### Scenario: 普通列表不要求分档

- **WHEN** 请求非归档会话列表
- **THEN** 前端仅按现有字段渲染，不因分档字段缺失而出错

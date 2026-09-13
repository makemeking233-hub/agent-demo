# data-isolation Specification

## Purpose
TBD - created by archiving change isolate-test-data-dir. Update Purpose after archive.
## Requirements
### Requirement: 测试可隔离数据目录

系统 SHALL 支持通过系统属性覆盖 agent 数据目录，且该覆盖 SHALL 优先于环境变量与默认位置。

#### Scenario: 系统属性生效

- **WHEN** 设置了数据目录覆盖的系统属性
- **THEN** 数据目录解析为该属性值下的 `.agent-demo`

#### Scenario: 属性优先于环境变量

- **WHEN** 系统属性与环境变量同时存在
- **THEN** 以系统属性为准

#### Scenario: 无覆盖时回退默认

- **WHEN** 系统属性为空或缺失且无环境变量
- **THEN** 数据目录为 `<user.home>/.agent-demo`

### Requirement: 数据目录与配置解析同源

配置文件 SHALL 从解析出的数据目录下读取，SHALL NOT 恒取默认位置。

#### Scenario: 覆盖数据目录时配置跟随

- **WHEN** 数据目录被覆盖
- **THEN** 配置也从该目录读取
- **AND** 不读取用户默认位置的配置

### Requirement: 集成测试不写用户真实数据

启动完整应用上下文的集成测试 SHALL 把数据目录指向临时位置，SHALL NOT 向用户真实数据目录写入会话存档等数据。

#### Scenario: 集成测试跑完真实目录不变

- **WHEN** 执行会产生会话的集成测试
- **THEN** 用户真实数据目录中的记录数不变
- **AND** 测试产生的数据落在临时目录

#### Scenario: 隔离早于上下文创建

- **WHEN** 集成测试设置数据目录覆盖
- **THEN** 该设置在任何数据写入之前生效


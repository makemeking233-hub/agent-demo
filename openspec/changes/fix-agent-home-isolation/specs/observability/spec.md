## MODIFIED Requirements

### Requirement: 日志根目录稳定且读写一致

运行时日志与 per-session 日志 SHALL 写入 agent 数据目录下的 `logs/`，SHALL NOT 依赖进程工作目录。日志查看接口 SHALL 读取同一目录。

agent 数据目录 SHALL 由**单一解析入口**决定，优先级为：系统属性 `agent.demo.home` → 环境变量 `AGENT_DEMO_HOME` → `user.home`（其下拼 `.agent-demo`）。全系统 SHALL NOT 存在第二个独立实现该解析的位置；`logback` 的 `app.log` 落盘路径 SHALL 遵循同一优先级。

#### Scenario: 不同工作目录写同一处

- WHEN 分别从仓库根目录与模块目录启动应用
- THEN 两次的日志都落在同一个 agent 数据目录下的 `logs/`

#### Scenario: 查看接口与写入一致

- WHEN 查询日志查看接口列出的会话
- THEN 列出的是实际被写入的会话日志

#### Scenario: 覆盖属性同时影响日志与查看接口

- WHEN 以系统属性 `agent.demo.home` 指向某个目录启动
- THEN 运行时日志与 per-session 日志都写在该目录下
- AND 日志查看接口从同一目录读取
- AND 未设置该属性时，全部路径与设置前逐字一致

### Requirement: 测试日志与运行时日志隔离

测试运行 SHALL NOT 向用户真实 agent 数据目录写入任何内容——包括运行时日志文件 `logs/app.log`、per-session 日志目录 `logs/sessions/`、会话存档 `sessions/`。

测试 JVM SHALL 默认携带 `agent.demo.home` 指向构建输出目录内的隔离位置，使「忘记设置」的默认后果是安全的，而不是写入真实数据。

#### Scenario: 跑测试不污染运行时日志

- WHEN 执行一次完整测试
- THEN 真实 `app.log` 不新增来自测试的行

#### Scenario: 跑测试不新增 per-session 日志目录

- WHEN 记录测试前真实 `logs/sessions/` 的目录数
- AND 执行一次完整测试
- THEN 测试后该目录数不变

#### Scenario: 新写的测试无需自觉隔离

- WHEN 新增一个不显式设置任何隔离属性的测试，且该测试触发完整回合
- THEN 其产生的日志与会话数据仍落在构建输出目录内
- AND 真实 agent 数据目录不被写入

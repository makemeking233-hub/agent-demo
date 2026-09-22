# testability Specification

## Purpose

可测性：日志驱动测试（golden 事件断言）、会话回放能力、日志模块可测试性。
## Requirements
### Requirement: 事件断言工具

系统 SHALL 提供测试可用的会话事件断言工具：读取 `session.jsonl` 后可按 `type` 过滤、按 `seq` 顺序断言、对指定字段做值断言；断言前 SHALL 对时间戳、`seq`、uuid 等易变字段做归一化（替换为占位符），保证 E2E 断言稳定。

#### Scenario: 断言事件类型序列

- **WHEN** 测试跑完一轮对话（含一次工具调用）后调用断言工具
- **THEN** 断言工具能按序匹配事件类型序列 `[turn/start, context/snapshot, user/message, assistant/message, tool/call, tool/result, turn/end]`，中间夹带的 `system/*` 事件可被忽略

#### Scenario: 断言事件字段

- **WHEN** 测试断言某条 `tool/call` 事件
- **THEN** 断言工具能验证 `name` 等于期望工具名、`arguments` 包含期望参数片段

#### Scenario: 归一化易变字段

- **WHEN** 两次独立运行的 golden 事件对比
- **THEN** 断言工具将 `timestamp`、`seq`、`callId` 归一化后，两次运行的事件类型序列与关键字段一致

### Requirement: 会话回放

系统 SHALL 提供只读回放工具：给定 `session.jsonl` 路径，按事件顺序重建 `MessageHistory`（`user/message` → `assistant/message`（含 toolCalls）→ `tool/result`），跳过 `context/snapshot` 与 `system/*` 事件；重建结果 SHALL 与原始对话的消息顺序一致。

#### Scenario: 单轮对话重建

- **WHEN** 回放一个含 `user` → `assistant` 的会话事件流
- **THEN** 重建的 `MessageHistory` 含 2 条消息，顺序为 user → assistant，内容与事件一致

#### Scenario: 工具调用轮重建

- **WHEN** 回放一个含 `user` → `assistant(toolCalls)` → `tool/result` → `assistant` 的会话事件流
- **THEN** 重建的 `MessageHistory` 消息顺序与真实对话一致，`assistant` 消息携带完整 `toolCalls`，`tool/result` 的 `toolCallId` 与对应 `toolCalls` 匹配

#### Scenario: 未知事件类型被跳过

- **WHEN** 事件流含未知/未来类型的事件行
- **THEN** 回放工具跳过该行不报错，其余事件正常重建

### Requirement: 日志模块可测试性

系统 SHALL 保证日志模块自身可测且故障隔离：日志写入失败（IO 异常、格式化异常）SHALL 只记录 WARN 并继续，绝不向调用方抛出；`logging.enabled=false` 时 SHALL 不产生任何会话日志文件且主流程零副作用；新增日志代码（Redactor、保留策略清理、事件写入扩展）SHALL 有单元测试覆盖并满足 jacoco 门禁。

#### Scenario: 日志写失败不打断对话

- **WHEN** `session.jsonl` 写入抛 IO 异常（如磁盘满）
- **THEN** 对话正常继续，仅输出 WARN 日志

#### Scenario: 关闭日志零副作用

- **WHEN** `logging.enabled=false`
- **THEN** 不创建 `logs/sessions/` 下任何目录或文件，CLI 行为与未接日志时完全一致

#### Scenario: 脱敏与清理有单测

- **WHEN** 运行 `mvn test`
- **THEN** `Redactor` 与保留策略清理器的单元测试全部通过，且覆盖分支满足 jacoco BRANCH≥70%

### Requirement: 悬挂 tool_calls 自愈

对每个含 `tool_calls` 的 assistant 消息，若其中某个 `tool_call_id` 在其后、下一条非 tool 消息之前没有对应的 `tool_result`，系统 SHALL 在该位置补一条合成错误 `tool_result`（`isError=true`），使消息序列满足 `tool_calls` 与 `tool` 消息的配对约束。

#### Scenario: 完全无结果

- **WHEN** 历史中某 assistant 带 `tool_calls=[c1]`，其后紧跟一条 user 消息
- **THEN** 在 assistant 与 user 之间插入一条 `tool_result(toolCallId=c1, isError=true)`
- **AND** 该结果的内容说明该工具调用未完成

#### Scenario: 部分结果

- **WHEN** 历史中某 assistant 带 `tool_calls=[c1,c2]`，其后只有 `tool_result(c1)`
- **THEN** 为 `c2` 补一条合成错误结果
- **AND** 补齐顺序与 `tool_calls` 顺序一致

#### Scenario: 已配对的历史零改动

- **WHEN** 每个 `tool_calls` 的 id 都有紧随其后的对应 `tool_result`
- **THEN** 消息列表不被修改

#### Scenario: 幂等

- **WHEN** 对同一消息列表连续执行两次修复
- **THEN** 第二次不产生任何新增消息

#### Scenario: 无 tool_calls 的 assistant 不受影响

- **WHEN** assistant 消息的 `tool_calls` 为空
- **THEN** 不对其做任何补齐

### Requirement: 恢复与请求两条路径都自愈

会话恢复（`SessionResumeLoader`）与每次构造请求（`AgentLoop`）SHALL 都应用**双向**配对修复：正向（assistant 的 `tool_calls` 缺结果时在其结果块末尾补合成错误结果）与反向（tool 结果缺前置 `assistant.tool_calls` 时在其前补合成 assistant 骨架）。由此，被污染的存档在恢复后即可继续对话，且同一进程内被打断的轮次、被裁剪过的历史都不会污染下一轮。

#### Scenario: 已污染存档恢复后可用

- **WHEN** 恢复一个含悬挂 `tool_calls` 的存档
- **THEN** 恢复出的消息列表满足配对约束
- **AND** 存档文件本身不被改写

#### Scenario: 进程内下一轮不被污染

- **WHEN** 内存历史中存在悬挂 `tool_calls`
- **THEN** 构造出的请求消息列表已补齐对应结果

#### Scenario: 请求路径同时修复正向与反向

- **WHEN** 内存历史中既有悬挂 `tool_calls`，又有无前置 `tool_calls` 的孤儿 `tool_result`
- **THEN** 构造出的请求消息列表对两者都已补齐
- **AND** 该列表满足双向配对约束

#### Scenario: 请求路径修复幂等

- **WHEN** 对同一内存历史连续构造两次请求
- **THEN** 两次得到的消息列表长度一致（第二次不新增任何合成消息）

#### Scenario: 内存历史不被修复改写

- **WHEN** 内存历史含悬挂 `tool_calls`
- **THEN** 修复只作用于将要发送的消息列表
- **AND** 内存历史本身的消息条数不变

### Requirement: 裁剪不破坏配对不变式

`SessionResumeLoader.snip` 按 token 上限从头部裁剪历史时 SHALL 以**配对组**为最小丢弃单位：SHALL NOT 丢弃某个 assistant 的 `tool_calls` 却保留其对应的 `tool_result`。裁剪结果的第一个非 system 消息 SHALL NOT 是 `ToolResult`。

#### Scenario: 裁剪点落在配对组中间

- **WHEN** 裁剪边界恰好落在 `assistant(tool_calls=[c1,c2])` 与其后续 `tool_result(c1)`、`tool_result(c2)` 之间
- **THEN** 该 assistant 与其全部结果一起被丢弃，或一起被保留
- **AND** 裁剪后的列表中没有无前置 `tool_calls` 的 `tool_result`

#### Scenario: 对齐后仍满足上限

- **WHEN** 裁剪点因对齐而后移
- **THEN** 保留的消息数不多于对齐前的保留数（对齐只减少保留量，不会重新超限）

#### Scenario: 未超限时不裁剪

- **WHEN** 消息总量不超过上限
- **THEN** 原样返回，不插入任何 summary 消息

#### Scenario: 裁剪后头部有压缩提示

- **WHEN** 发生了裁剪
- **THEN** 保留列表的首条为以 `[RESUMED]` 开头的 system 消息

### Requirement: 覆盖率门禁

每个模块的构建 SHALL 在 `mvn verify` 阶段用 jacoco 考核覆盖率：单元与集成测试覆盖的代码行 `LINE ≥ 0.80`、分支 `BRANCH ≥ 0.70`，考核方式按 `PACKAGE` 逐包独立考核（任一包不达标即构建失败，定位粒度到包）。`main` 入口类与无测试覆盖意图的类 SHALL 通过 `<excludes>` 排除，excludes 路径形式 SHALL 用斜杠（与 jacoco 内部类名形式一致）。

`PACKAGE` 元素的 `includes` **同时**列出「根包名」与「根包名 `.*`」两种模式：`X` 只匹配名为 `X` 的包、`X.*` 只匹配 `X` 的子包，只写其一会静默漏掉另一半。规则覆盖的包 SHALL 包含被测模块下所有需要考核的包及其子包。

`agent-core` 与 `agent-web` SHALL 在各自的 pom 中实现上述规则；任何对阈值、考核方式、排除清单的修改 SHALL 走 OpenSpec change 流程（门禁规则不再以「AGENTS.md 里一句口号」形式存在）。

`agent-core` 的排除清单 SHALL 至少包含以下条目（每条 SHALL 有可陈述的排除理由）：

| 排除项 | 理由 |
|--------|------|
| `com/example/agent/AgentCli.*` | JVM 入口，`main()` 不在单测中调用 |
| `com/example/agent/memory/embedding/OnnxEmbeddingProvider.*` | ONNX 推理适配层，核心路径依赖 95MB 外部模型文件（`model.onnx_data`）与 final 类 `ai.onnxruntime.OrtSession`（无法 mock）；其降级路径由 `EmbeddingProviderTest` 覆盖，真实推理路径由 `OnnxEmbeddingProviderE2ETest` 条件覆盖（本地有模型时 4/4 通过，无模型时 skip） |

#### Scenario: 阈值下调要走 change

- **WHEN** 项目希望把 BRANCH 阈值从 0.70 调到 0.60
- **THEN** 修改 pom 之前先开一个 OpenSpec change 描述动机与影响，且 delta spec 应包含被修改的 `覆盖率门禁` Requirement

#### Scenario: main 入口类排除

- **WHEN** 一个类的唯一用途是 JVM 入口（`public static void main`）
- **THEN** 通过 `<excludes>` 排除，路径用斜杠形式（如 `com/example/agent/AgentCli.*`）

#### Scenario: 依赖外部大模型的适配层排除

- **WHEN** 一个类的核心路径需要仓库外的大体积模型文件（如 95MB ONNX 权重）与无法 mock 的 final 类（如 `OrtSession`）
- **THEN** 通过 `<excludes>` 排除，且排除理由中 SHALL 说明该类的降级路径与真实路径分别由哪个测试覆盖

#### Scenario: 根包与子包都要写进 includes

- **WHEN** 一个模块的实际覆盖缺口集中在子包（如 `com.example.agent.tools.file`）
- **THEN** `includes` 必须同时包含 `com.example.agent.tools` 与 `com.example.agent.tools.*`
- **AND** 若只写前者，子包的缺口不会被考核（实测：改为只写根包名时 `tools.file` 的 BRANCH 0.50 仍报 `All coverage checks have been met`）

#### Scenario: jacoco check 真空通过要被抓出来

- **WHEN** `<element>BUNDLE</element>` 与类名通配 `<include>`（如 `com.example.agent.provider.*`）同时出现
- **THEN** 该规则的 `includes` 实际过滤的是 bundle 名而非类名，考核对象为 0，check 真空通过；本 spec 要求此种配置不允许出现，pom 中 SHALL 用 `<element>PACKAGE</element>` + 包名模式
- **AND** 判定某条规则是否真的生效 SHALL 用「阈值抬升实验」：把阈值临时改为不可能达到的值，构建应因此失败；若仍成功即为真空通过


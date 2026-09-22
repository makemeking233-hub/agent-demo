## ADDED Requirements

### Requirement: 覆盖率门禁

每个模块的构建 SHALL 在 `mvn verify` 阶段用 jacoco 考核覆盖率：单元与集成测试覆盖的代码行 `LINE ≥ 0.80`、分支 `BRANCH ≥ 0.70`，考核方式按 `PACKAGE` 逐包独立考核（任一包不达标即构建失败，定位粒度到包）。`main` 入口类与无测试覆盖意图的类 SHALL 通过 `<excludes>` 排除，excludes 路径形式 SHALL 用斜杠（与 jacoco 内部类名形式一致）。

`PACKAGE` 元素的 `includes` **同时**列出「根包名」与「根包名 `.*`」两种模式：`X` 只匹配名为 `X` 的包、`X.*` 只匹配 `X` 的子包，只写其一会静默漏掉另一半。规则覆盖的包 SHALL 包含被测模块下所有需要考核的包及其子包。

`agent-core` 与 `agent-web` SHALL 在各自的 pom 中实现上述规则；任何对阈值、考核方式、排除清单的修改 SHALL 走 OpenSpec change 流程（门禁规则不再以「AGENTS.md 里一句口号」形式存在）。

#### Scenario: 阈值下调要走 change

- **WHEN** 项目希望把 BRANCH 阈值从 0.70 调到 0.60
- **THEN** 修改 pom 之前先开一个 OpenSpec change 描述动机与影响，且 delta spec 应包含被修改的 `覆盖率门禁` Requirement

#### Scenario: main 入口类排除

- **WHEN** 一个类的唯一用途是 JVM 入口（`public static void main`）
- **THEN** 通过 `<excludes>` 排除，路径用斜杠形式（如 `com/example/agent/AgentCli.*`）

#### Scenario: 根包与子包都要写进 includes

- **WHEN** 一个模块的实际覆盖缺口集中在子包（如 `com.example.agent.tools.file`）
- **THEN** `includes` 必须同时包含 `com.example.agent.tools` 与 `com.example.agent.tools.*`
- **AND** 若只写前者，子包的缺口不会被考核（实测：改为只写根包名时 `tools.file` 的 BRANCH 0.50 仍报 `All coverage checks have been met`）

#### Scenario: jacoco check 真空通过要被抓出来

- **WHEN** `<element>BUNDLE</element>` 与类名通配 `<include>`（如 `com.example.agent.provider.*`）同时出现
- **THEN** 该规则的 `includes` 实际过滤的是 bundle 名而非类名，考核对象为 0，check 真空通过；本 spec 要求此种配置不允许出现，pom 中 SHALL 用 `<element>PACKAGE</element>` + 包名模式
- **AND** 判定某条规则是否真的生效 SHALL 用「阈值抬升实验」：把阈值临时改为不可能达到的值，构建应因此失败；若仍成功即为真空通过

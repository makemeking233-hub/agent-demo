# fix-jacoco-rule — 技术设计

## 1. 根因复盘

### 1.1 元素 vs 包含

jacoco 的 `<rule>` 里，`includes` / `excludes` 过滤的是「**被考核元素**」的名称，而不是「被考核类的名字」：

| `element` 值 | `includes` 过滤的是 | 与「类名通配」的关系 |
|---|---|---|
| `BUNDLE` | bundle 名（pom 里的 `artifactId`，本例 `agent-core`） | 包名模式 `com.example.agent.provider.*` 不匹配 `agent-core` → 0 个考核对象 → 真空通过 |
| `PACKAGE` | 包名 | 包名模式 `com.example.agent.web.*` 匹配 `com.example.agent.web` 等 → 真实考核 |

`agent-web` 用 `PACKAGE` + 包名通配 → 真实生效（snip-pairing-repair 调查时抬阈值实验验证）。`agent-core` 用 `BUNDLE` + 类名前缀 → 不匹配 bundle 名 → 空转。

佐证：两个模块 pom 的 `includes` 模式都是点号通配、字符串层面无差别，**差异完全在 element**。

### 1.2 为什么这条规则活了 50+ 天

| 原因 | 说明 |
|---|---|
| `BUILD SUCCESS` 让人安心 | 空转的 check 照样成功 |
| 覆盖率报告单独跑才看得见 | `jacoco:report` 不分 includes 直接报全量，CSV 仍能反映真实水平；check 与 report 共用同一份数据但口径不同 |
| 强制门禁仅写在 AGENTS.md | 没有规格层做锚点，更没有 CI 阻断 |
| 与 `agent-web` 的差异未被对照 | `agent-web` 用 PACKAGE 是 snip-pairing-repair 调查时才发现的；之前没人把两个 pom 摆在一起看 |

## 2. 方案

### D1 pom 规则改写

把 `agent-core/pom.xml` 的 jacoco 规则从：

```xml
<rule>
  <element>BUNDLE</element>
  <includes>
    <include>com.example.agent.provider.*</include>
    ... 6 个
  </includes>
  <limits>
    <limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.80</minimum></limit>
    <limit><counter>BRANCH</counter><value>COVEREDRATIO</value><minimum>0.70</minimum></limit>
  </limits>
</rule>
```

改为：

```xml
<rule>
  <element>PACKAGE</element>
  <includes>
    <include>com.example.agent.provider</include>
    <include>com.example.agent.provider.*</include>
    <include>com.example.agent.tools</include>
    <include>com.example.agent.tools.*</include>
    <include>com.example.agent.permission</include>
    <include>com.example.agent.permission.*</include>
    <include>com.example.agent.session</include>
    <include>com.example.agent.session.*</include>
    <include>com.example.agent.memory</include>
    <include>com.example.agent.memory.*</include>
  </includes>
  <limits>
    <limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.80</minimum></limit>
    <limit><counter>BRANCH</counter><value>COVEREDRATIO</value><minimum>0.70</minimum></limit>
  </limits>
</rule>
```

要点：

- 改 `element` 为 `PACKAGE`。
- **`X` 与 `X.*` 必须成对出现**（本次实测修正）：`PACKAGE` 元素下 `includes` 过滤的是**包名**而非类名。`X` 只匹配名为 `X` 的包，`X.*` 只匹配 `X` 的子包——只写前者会漏掉全部子包（实测：只写根包名时 `tools.file` BRANCH 0.50、`provider.minimax` LINE 0.53 都不被考核，构建照样打印 `All coverage checks have been met`）。这与开头要修的缺陷是**同一类错误**，只是从 BUNDLE 层搬到了 PACKAGE 层。
- 顺带说明 agent-web：它的 `com.example.agent.web.*` **漏了根包** `com.example.agent.web`，属同一问题的另一半（本次不修，已记入后续建议）。
- 删除 `com.example.agent.agent.*` 这条（对应不存在的包，2026-08-29 已记录）。
- `<excludes>` 放在 `check-coverage` execution 的 `<configuration>` 里，与 `rules` 同级，对齐 `agent-web/pom.xml` 的写法。
- 断言规则「真的生效」的方法固定为**阈值抬升实验**：临时把阈值改成不可能达到的值，构建应因此失败；仍成功即为真空通过。本次对 agent-core 与该实验对 agent-web 的结果互为对照。

### D2 补测策略

改 pom 后第一次 `mvn verify` 会暴露真实缺口。补测原则：

| 原则 | 说明 |
|---|---|
| 按缺口大小排优先级 | BRANCH 缺口最大的包先补（数据驱动，不是猜） |
| 一个包补到双双达标即停 | 不追求单类覆盖率 100%；阈值即门槛 |
| 避免为凑数加空测试 | 每个新测试断言至少一条「协议行为」或「不变式」，不是「调一下不崩」 |
| 复用既有测试组织 | 优先在对应类的既有 `*Test.java` 里加 `@Test`，必要时新建测试类 |

实测缺口（T1 拓宽 includes 后暴露，T2 逐包补齐）：

| 包 | 补前 LINE | 补前 BRANCH | 补后 LINE | 补后 BRANCH | 补测内容 |
|----|:--------:|:----------:|:--------:|:----------:|---------|
| `tools.file` | 0.62 | 0.50 | **0.82** | **0.95** | 新增 `FileToolsProtocolTest`(4)：四个文件工具的协议面（name/description/inputSchema/category/isReadOnly/isDestructive/checkPermissions/renderUse/renderResult） |
| `provider.minimax` | 0.53 | 1.00 | **1.00** | **1.00** | 新增 `MiniMaxProviderTest`(5)：三个构造器 + baseUrl/chatEndpoint/contextWindow/maxOutputTokens |
| `provider.anthropic` | 0.75 | 0.68 | **0.97** | **0.77** | 新增 `AnthropicProviderStreamChatTest`(9)：WireMock 跑通 `streamChat` 全链路 |
| `tools.shell` | 0.68 | 0.65 | **0.84** | **0.75** | `ShellToolTest` +5：协议面、`parseArguments`、输出截断、超时分支 |
| `tools.websearch` | 0.98 | 0.68 | **0.98** | **0.71** | `WebSearchProviderFactoryTest` +4：blank 回退、`pickFirstNonBlank`、null type/model 推断 |
| `session` | 0.77 | 0.64 | **0.80** | **0.71** | `SessionEntryTest`(8) + `SessionResumeLoaderParsingTest`(18) |
| `tools` | 0.78 | 0.50 | **0.89** | **0.88** | `ToolRegistryTest` +5 + `PathGuardTest`(4) + `WebSearchToolTest` +12 |

未在补测范围内的包（已达标，仅记录）：`provider` 1.00/1.00、`provider.deepseek` 0.94/1.00、`provider.openai` 0.91/0.74、`permission` 0.96/0.87、`memory` 0.91/0.73。

> 「补前」数值取自「includes 尚未拓宽」时的 `jacoco.csv`；「补后」取自最终门禁运行（`All coverage checks have been met` + BUILD SUCCESS）产生的 CSV。

补测原则（实际遵守）：

| 原则 | 说明 |
|---|---|
| 按缺口大小排优先级 | 先补 LINE/BRANCH 双缺口最大的包；`websearch` 只差 2 分支故最后做 |
| 达标即停 | 不追求单类 100%；越过阈值即收手 |
| 不为凑数加空测试 | 每条断言都对应一个协议行为或不变式；写 `FileToolsProtocolTest` 时删掉过一条无意义的 `Path` 断言 |
| 优先补「契约面」而非 getter | 文件工具的 `inputSchema`/`checkPermissions`/`renderUse` 被误改不会有人发现，比单纯的行覆盖更有价值 |

### D2.1 过程中发现的新缺陷（本 change 不修）

补 `provider.anthropic` 时需要覆盖 `streamChat`，于是写了 WireMock 端到端用例——**用例红了，而且不是测试写错**。

| 项 | 内容 |
|----|------|
| 现象 | `AnthropicProvider.streamChat` 在真实 `Content-Type: text/event-stream` 响应下**零产出**（收集到的 chunk 列表为空） |
| 实验 | 同一份 SSE 文本、同一份 stub，只把 `Content-Type` 从 `text/event-stream` 换成 `text/plain`，立刻产出 `[TextDelta[答案]]` |
| 根因 | Spring 的 `ServerSentEventHttpMessageReader` 对 `text/event-stream` 会**剥掉 `data: ` 前缀**（并把多行 data 用 `\n` 拼接）后交付给 `String` 解码器；而 `parseSseLine` 第一件事就是 `if (!line.startsWith("data: ")) return null;` —— 两者不匹配 |
| 影响 | Anthropic provider 走真实 API 时永远拿不到内容。既有单测之所以全绿，是因为它们直接调 `parseSseLine("data: {...}")`，绕过了 HTTP 层 |
| 本次处置 | **不修**（超出覆盖率门禁的 scope）。用 `AnthropicProviderStreamChatTest.knownDefectSseContentTypeYieldsNoChunks()` 作为 characterization 用例固化现状，用例 javadoc 写明修复后应删除它；正路径用例暂用 `text/plain` 以便 `streamChat` 的 HTTP 管线真的被执行到 |
| 建议 | 单开 change 修：`bodyToFlux(ServerSentEvent.class)` 后取 `data()`，或让 `parseSseLine` 兼容已剥前缀的载荷；修完把正路径用例改回 `text/event-stream` |

### D3 规格落地

`openspec/specs/testability/spec.md` 新增一条 Requirement《覆盖率门禁》：

```text
### Requirement: 覆盖率门禁

每个模块的构建 SHALL 在 `mvn verify` 阶段用 jacoco 考核覆盖率：
- 单元与集成测试覆盖的代码行 LINE ≥ 0.80；
- 分支 BRANCH ≥ 0.70；
- 考核方式按 PACKAGE 逐包独立考核（每包不达标即构建失败，定位粒度到包）；
- `main` 入口类与无测试覆盖意图的类 SHALL 通过 `<excludes>` 排除，excludes 路径形式用斜杠。

`agent-core` 与 `agent-web` SHALL 在各自的 pom 中实现上述规则；任何对阈值、考核方式、排除清单的修改 SHALL 走 OpenSpec change 流程。

#### Scenario: 修改阈值要走 change
- **WHEN** 项目希望把 BRANCH 阈值从 0.70 调到 0.60
- **THEN** 修改 pom 之前先开一个 OpenSpec change 描述动机与影响

#### Scenario: main 入口类排除
- **WHEN** 一个类的唯一用途是 JVM 入口
- **THEN** 通过 plugin-level `<excludes>` 排除；排除路径用斜杠形式（`com/example/Foo`）以与 jacoco 内部类名形式一致

#### Scenario: jacoco check 真空通过要被抓出来
- **WHEN** `<element>BUNDLE</element>` 与类名通配 `<include>` 同时出现
- **THEN** 文档/复盘记录应指出这是已知配置陷阱（见 2026-09-22 test-report §6）
```

## 3. 接口签名变化

| 类/方法 | 改前 | 改后 |
|---|---|---|
| `agent-core/pom.xml` jacoco 规则 | `<element>BUNDLE</element>` + 6 个类名通配 | `<element>PACKAGE</element>` + 5 个精确包名 + `agent.agent` 删除 + plugin-level `<excludes>` |
| `AGENTS.md §2.5.3` | 「jacoco 强制门禁」一句话 | 加一行指向 `testability/spec.md §覆盖率门禁` |
| `openspec/specs/testability/spec.md` | 6 条 Requirement | 7 条（新增《覆盖率门禁》） |

无公共 API 变更、无运行期行为变更。

## 4. 风险

| 风险 | 处置 |
|---|---|
| 改 pom 后构建立刻失败 | 本次就是这样设计的：暴露缺口后补齐；不下调阈值；记录在 test-report |
| 补测试需要回头读多个类的语义 | T2 子任务逐包推进，单包不超过半天 |
| excludes 排除过多绕过门禁 | 排除清单逐条说明动机，列在 design 与 commit message 中；任何新增 exclude 走 change |
| 阈值在 spec 层被错误地调低 | 写到 `testability/spec.md`，变更阈值要走 archive change 才能合并 |

## 5. 测试策略

| 层 | 用例 |
|---|---|
| T1（pom 改写） | 跑 `mvn -o -pl agent-core verify -DskipTests` 让 jacoco:check 用现有测试集评估；记录每个包的 LINE/BRANCH 与缺口，作为 T2 输入 |
| T2（逐包补测） | 按 T1 数据排序补；每个新测试类/方法跑单测、确认包覆盖率 ≥ 80/70；最终 `mvn verify` 全绿 |
| T3（spec） | `openspec validate --change fix-jacoco-rule` 通过；归档后主 spec 含新 Requirement |
| T4（合并） | 合并 main + 复验门禁 + push（§2.7.5 标准流程） |

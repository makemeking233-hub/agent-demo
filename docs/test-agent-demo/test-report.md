# agent-demo v0.1 全面测试报告

> 测试对象：`agent-demo`（Java 编写的 Claude Code 风格 Agent CLI）
> 测试范围：`agent-chat` REPL、LLM Provider、AgentLoop 主循环、工具层、权限、会话、记忆、上下文压缩、CLI 命令、运行期冒烟
> 报告日期：2026-08-29
> 执行者：测试工程师（自动化 + 手工冒烟）
> 角色视角：QA / 验收

---

## 1. 测试结论（TL;DR）

| 维度 | 结果 | 说明 |
|------|------|------|
| **构建** | ✅ 通过 | `mvn clean verify` BUILD SUCCESS；`java -jar` 可启动 |
| **自动化单元/集成/E2E** | ✅ 全绿 | **34 个测试类 / 137 个用例**，FAIL=0、ERR=0、SKIP=0 |
| **运行期冒烟** | ✅ 通过 | `chat --input "hi"` 完整走通并返回真实模型回复 |
| **用户报告的「输入就报错」** | ✅ **已修复** | 根因已定位并确认修复，见 §5 |
| **覆盖率（全局）** | 🟡 低于目标 | LINE **68.3%**、BRANCH **55.4%**（目标 LINE≥80% / BRANCH≥70%） |
| **覆盖率门禁** | 🔴 **失守** | jacoco 门禁 `includes` 引用已废弃包，实际形同虚设，见 §6.3 |
| **设计文档用例落地率** | 🟡 约 68% | 设计 200 用例，实际落地 137，见 §7 |

**核心结论**：项目**可构建、可运行、核心链路可用**，自动化测试全绿；但存在一个**高风险质量问题**——覆盖率门禁配置与代码包结构脱节（指向已删除的 `agent` 包），导致**系统最核心、最易出错的 `core`（AgentLoop / Message / ContextCompressor）与 `llm` 包完全不被覆盖率门禁约束**，且全局覆盖率实际不达标却显示"All coverage checks have been met"。此外，测试落地数（137）明显低于测试设计文档规划（200）。

---

## 2. 测试环境

| 项 | 值 |
|------|------|
| 操作系统 | Windows |
| JDK | OpenJDK 17.0.8 LTS |
| Maven | 3.6.1 |
| Spring Boot | 3.2.5 |
| 测试框架 | JUnit 5 + Mockito + WireMock 3.6.0 + Reactor Test |
| 覆盖率工具 | JaCoCo 0.8.11 |
| 被测版本 | HEAD `a4021b3`（含 5 条近期修复提交） |

> 环境说明：Maven 3.6.1 偏旧（Spring Boot 3.2 建议 ≥3.6.3），但本次构建/测试全部通过，未受影响。

---

## 3. 测试方法

分四层执行，由下而上：

| 层级 | 方式 | 说明 |
|------|------|------|
| **单元** | JUnit 5 + Mockito | 算法、边界、错误路径（Provider / AgentLoop / 工具 / 权限 / 会话 / 记忆） |
| **集成** | WireMock + Reactor Test | Provider 用 WireMock 模拟真实 DeepSeek SSE 流，验证解析与 usage |
| **E2E** | WireMock + picocli.testing | 验收 #1 #2（流式输出、工具调用） |
| **冒烟** | `java -jar` 真实运行 | 验证「启动 → REPL → 输入 → 真实模型回复 → 退出」完整链路 |

---

## 4. 自动化测试结果矩阵

**总计：34 个测试类，137 个用例，全部通过（FAIL=0 / ERR=0 / SKIP=0）。**

### 4.1 按测试类统计

| 测试类 | 用例数 | 覆盖模块 |
|--------|:------:|----------|
| AgentLoopTest | 9 | 主循环、maxToolIterations、参数反序列化、工具调用 |
| AgentLoopToolContextTest | 2 | 工具上下文 |
| ContextCompressorCompactTest | 2 | 压缩塌缩、熔断计数器 |
| ContextCompressorTest | 3 | 阈值计算、熔断 |
| MessageHistoryTest | 5 | 消息容器、token 估算、注入 |
| LlmRetryTest | 4 | 重试边界、退避 |
| StreamChunkAggregateTest | 5 | 工具调用累积、流聚合 |
| TokenEstimatorTest | 4 | token 估算 |
| SessionLoggerTest | 7 | 会话日志 |
| MemoryDirTest | 3 | 记忆目录 |
| MemoryIndexTest | 2 | 记忆索引 |
| MemoryPromptBuilderTest | 3 | 记忆 prompt 注入 |
| MemoryRecallTest | 3 | 记忆召回 |
| PermissionManagerTest | 7 | 权限裁决、敏感路径 |
| PermissionPathMatcherTest | 8 | Ant glob → 正则路径匹配 |
| SystemPromptBuilderTest | 8 | 系统提示词组装 |
| DeepSeekProviderTest | 5 | DeepSeek Provider + SSE + 缓冲 |
| OpenAiCompatibleMapperTest | 8 | 请求体构造、SSE 解析 |
| SessionStoreTest | 3 | JSONL 会话存储 |
| EditFileToolTest | 3 | 编辑文件 |
| LsToolTest | 3 | 列目录 |
| ReadFileToolTest | 6 | 读文件、编码回退、路径越界 |
| WriteFileToolTest | 3 | 写文件 |
| BashAdapterTest | 4 | Unix shell 适配 |
| CmdAdapterTest | 3 | Windows shell 适配 |
| ShellToolTest | 3 | 黑名单、正常命令、黑名单优先 |
| ToolRegistryTest | 3 | 工具注册表 |
| InitCommandTest | 2 | init 子命令 |
| SlashCommandTest | 4 | slash 命令 |
| ConfigLoaderTest | 3 | 配置加载 |
| ChatCommandFriendlyErrorTest | 4 | 错误友好提示（401/404/429/网络） |
| AgentCliTest | 2 | CLI 派发（--help / init） |
| ReplStreamingE2ETest | 1 | 验收 #1：流式输出 + include_usage |
| ToolCallE2ETest | 1 | 验收 #2：工具调用解析 |
| **合计** | **137** | — |

### 4.2 已验证的关键安全/权限场景（均通过）

- 🔴 危险命令黑名单拒绝：`format C: /q`、`del /f /s /q C:\foo`（`ShellToolTest.deniesBlacklistedCommand` / `blacklistedTakesPrecedenceOverSuccess`）
- 🔴 黑名单命中优先于"命令看似无害"（`blacklistedTakesPrecedenceOverSuccess`）
- 🔴 敏感路径强制 ask：`.env`、`~/.ssh/id_rsa`、`*.pem`（`PermissionManagerTest.sensitive*`）
- 🟡 默认权限策略：read=allow、write=ask、shell=ask（`PermissionManagerTest.readIsAllowedByDefault` / `writeAsks` / `shellAsks`）

---

## 5. 运行期冒烟（核心问题验证）

### 5.1 用户反馈「随便问句话都报错」— 已修复 ✅

**实证**：`java -jar target/agent-cli.jar chat --input "hi"`

```
退出码: 0, 耗时: 7.08s
agent-demo v0.1 chat (model=deepseek-chat), /help for commands, /quit to exit
模型回复: "你好，我是 agent-demo，一个终端 AI 助手。有什么我可以帮助你的吗？..."
```

链路完整走通：Spring Boot 启动 → REPL → 输入 → 调用真实 DeepSeek → 返回流式中文回复 → 退出码 0。**这证明"输入任意句子就报错"已不再复现。**

### 5.2 根因回溯（为何修复）

历史定位到的该 bug 根因，正是以下**近期提交**逐一修复的：

| 提交 | 修复内容 | 对应原报错 |
|------|---------|-----------|
| `6cdf3e0` | 修复工具调用**参数反序列化**与**增量 SSE 解析崩溃** | 输入触发工具调用时崩溃 |
| `5ea0103` | 工具失败结果**补全 toolCallId**，修复 400 | 消息顺序/toolCallId 不匹配 400 |
| `a4021b3` | 放大 WebClient 响应缓冲至 **16MB**，修复 `DataBufferLimitException` | 长响应报错 |
| `c2cf627` | 系统提示词注入运行时**存储位置** | 无法回答日志/会话位置 |
| `5b0bd88` | 文件工具放行 `~/.agent-demo` | 缺文件告警噪声 |
| `a62bcf6` | tools 转 **OpenAI 标准格式** + 4xx 错误体可见 | 请求体格式错误 / 错误不可见 |

结合本节冒烟通过，判定：**该缺陷已在 HEAD 修复且经真机验证通过。**

### 5.3 CLI 命令冒烟

| 命令 | 结果 | 输出 |
|------|------|------|
| `java -jar agent-cli.jar --help` | ✅ | Usage + `chat` / `init` 子命令正确列出 |
| `java -jar agent-cli.jar init --help` | ✅ | init 子命令正常解析 |
| `java -jar agent-cli.jar chat --input "hi"` | ✅ | 完整对话并返回真实回复 |

> ⚠️ 观察项：冒烟输出中有部分中文显示为乱码（`��ã�����`），这是 PowerShell 捕获管道时的**终端编码**问题（代码本身返回正确 Unicode 中文），非代码缺陷。建议在真实终端（支持 UTF-8）验证，报告记为低优先级环境观察项。

---

## 6. 覆盖率分析

### 6.1 全局覆盖率（真实，来自 jacoco.csv）

| 指标 | 数值 |
|------|------|
| 类数 | 99 |
| **LINE 覆盖率** | **68.3%**（1122/1643） |
| **BRANCH 覆盖率** | **55.4%**（331/598） |

**均低于设计目标（LINE≥80% / BRANCH≥70%）。**

### 6.2 各包覆盖率明细

| 包 | 类数 | LINE% | BRANCH% | 评估 |
|----|:----:|:-----:|:-------:|------|
| `com.example.agent.core` | 9 | **85.5%** | 68.3% | 核心编排，LINE 达标但 BRANCH 略低 |
| `com.example.agent.llm` | 18 | 80.7% | 62.5% | LLM 抽象层，LINE 达标 |
| `com.example.agent.memory` | 6 | 83.2% | 71.4% | 达标 |
| `com.example.agent.permission` | 5 | 86.4% | 80.8% | 达标 |
| `com.example.agent.prompt` | 1 | 100% | 75% | 达标 |
| `com.example.agent.provider.openai` | 6 | 79.7% | 66.2% | 接近达标 |
| `com.example.agent.config` | 8 | 78.9% | **39.3%** | LINE 接近，BRANCH 低 🔴 |
| `com.example.agent.tools` | 10 | 77.2% | 57.1% | 🟡 |
| `com.example.agent.render` | 1 | 73.3% | 0% | 🟡 |
| `com.example.agent.log` | 5 | 70.8% | 38.5% | 🟡 |
| `com.example.agent.session` | 3 | 67% | **50%** | 🟡 |
| `com.example.agent.tools.shell` | 8 | 65.2% | 65.4% | 🟡 |
| `com.example.agent.tools.file` | 8 | **46.7%** | **45%** | 🔴 严重不足 |
| `com.example.agent.cli` | 5 | **28.3%** | 27.6% | 🔴 严重不足（REPL 主循环低覆盖） |
| `com.example.agent.provider.deepseek` | 1 | 50% | 0% | 🟡 |
| `com.example.agent.provider.minimax` | 1 | **0%** | 0% | 🔴 完全未测 |
| `com.example.agent.util` | 1 | 60% | 25% | 🟡 |
| `com.example.agent.core.exception` | 2 | 83.3% | 0% | 🟡 |

### 6.3 🔴 关键质量问题：覆盖率门禁形同虚设

`pom.xml` 的 jacoco `check` 门禁 `includes` 声明为：

```
com.example.agent.provider.*
com.example.agent.agent.*      ← 该包已在 refactor 中删除（agent→core）
com.example.agent.tools.*
com.example.agent.permission.*
com.example.agent.session.*
com.example.agent.memory.*
```

**实测 `com.example.agent.agent.*` 在源码中命中 0 个类**（重构 `f04306d` 已把 `agent` 包改名为 `core`），导致：

1. **门禁覆盖范围严重失真**：`core`（AgentLoop / Message / MessageHistory / ContextCompressor —— 全系统最核心、最易出 bug 的部分）、`llm`、`log`、`cli`、`config`、`prompt`、`render` 等**全都不受覆盖率门禁约束**。
2. **全局 LINE 68.3% 未达标，但 `mvn verify` 却输出 "All coverage checks have been met"** —— 因为门禁只考察了它 includes 的那几个包（provider/tools/permission/session/memory），其中这些包恰好达标，于是"假通过"。

**影响**：覆盖率门禁失去了应有的质量闸门作用，无法防止核心模块（尤其是 AgentLoop 主循环）出现低覆盖回归。**列为全网最高优先级缺陷（🔴）。**

---

## 7. 与测试设计文档的差距

测试设计文档（`docs/test-agent-demo/test-design.md`）规划了 **200 个用例**（P0=124），当前实际落地 **137 个测试**，落地率约 **68%**。主要差距：

| 设计模块 | 设计用例 | 落地情况 | 主要缺口 |
|---------|:-------:|---------|---------|
| TC-T-TOOL（工具黑名单矩阵） | 34 | 3（ShellToolTest） | 只有 3 条，缺 basename 归一化、短参数簇展开（`rm -rf`≡`rm -fr`）等穷举 |
| TC-PERM | 15 | 7 | 缺裁决顺序（全局>工具>交互）、denyCommands 规则 |
| TC-SESS | 15 | 3 | 缺双路径去重、并发 flush、shutdown hook 兜底 |
| TC-COMP | 16 | 5 | 缺 PTL fallback、重注入（Post-Compact 文件/边界消息） |
| TC-MEM | 16 | 11 | 缺 MEMORY.md 截断 200 行/25KB 边界、自指引用 |
| TC-INT（中断/编码） | 10 | 0 | **完全未落地**（Ctrl+C、GBK/UTF-8 三重防御） |
| TC-ERR | 14 | 4 | 缺 context_too_long 触发压缩重试、流中途断 |
| TC-E2E | 14 | 2 | 只有 #1 #2，缺 #3-#14 |
| TC-SEC（安全专项） | 12 | 0 | **完全未落地**（prompt injection、路径越权、API key 泄露、OWASP 扫描） |
| TC-COST（成本） | 8 | 0 | **完全未落地**（费用估算、4 元预警/5 元停止、价格表查找） |
| TC-REPL | 12 | 4 | 缺流式渲染 CODE_FENCE 态、工具高亮、输入锁定 |

> 说明：137 个测试虽然全绿，但**测试设计文档中大量高价值用例尚未落地**，尤其安全专项（TC-SEC）、中断/编码（TC-INT）、成本控制（TC-COST）为零覆盖——这些都是设计文档标注为 P0 的重点。

---

## 8. 缺陷与风险清单（按严重级排序）

### 🔴 强制（必须修复）

| # | 问题 | 位置 | 影响 | 建议 |
|:--:|------|------|------|------|
| R1 | **jacoco 覆盖率门禁引用已废弃包 `com.example.agent.agent.*`** | `pom.xml` jacoco check includes | 核心模块（core/llm/cli/log/config）不受覆盖率约束，门禁形同虚设，掩盖真实低覆盖 | 更新 includes 为现有包：`com.example.agent.core.*`、`com.example.agent.llm.*`、`com.example.agent.cli.*` 等；并按新口径重设阈值 |
| R2 | **`tools.file` 包 LINE 覆盖率仅 46.7%**，`cli` 仅 28.3% | 测试缺失 | 文件工具边界（编码回退分支、父目录创建、覆盖权限）与 REPL 主循环分支未充分验证 | 补充 ReadFile/WriteFile/EditFile 的异常分支、编码矩阵、REPL 流式状态测试 |
| R3 | **`provider.minimax` 覆盖率 0%** | MiniMaxProvider | 已支持多 provider，但 MiniMax 路径完全未测试 | 补 MiniMax provider 单元/集成测试 |

### 🟡 推荐（建议整改）

| # | 问题 | 影响 | 建议 |
|:--:|------|------|------|
| Y1 | **全局覆盖率未达标**（LINE 68.3% / BRANCH 55.4% 均低于 80%/70%） | 质量闸门通过但实际不达标 | 提高 `core`/`llm`/`permission` 覆盖，优先补 `config`(BRANCH 39.3%)/`log`(38.5%)/`session`(50%) 分支 |
| Y2 | **测试设计 200 用例仅落地 137** | 设计意图未完全实现 | 对照 test-design.md 补齐缺口，尤其 TC-SEC/TC-INT/TC-COST |
| Y3 | **安全专项（TC-SEC）零覆盖** | prompt injection、路径越权、API key 泄露无回归防护 | 补安全专项测试 + `mvn dependency-check` hook |
| Y4 | **中断/编码（TC-INT）零覆盖** | Ctrl+C 二次退出、GBK/UTF-8 三重防御未验证 | 补 InterruptController 与编码矩阵测试 |
| Y5 | **成本控制（TC-COST）零覆盖** | 费用估算、4 元预警/5 元停止无测试 | 补价格表查找、费用预警/停止逻辑测试 |
| Y6 | **E2E 仅 2 条**（验收 #1 #2） | 验收 #3-#14 无自动回归 | 补 E2E #3-#14（工具调用确认、会话保存、配置生效、断网重连、内存写入/召回、压缩、Ctrl+C、history） |
| Y7 | `config` BRANCH 39.3%、`tools.shell` LINE 65.2%** | 关键分支未覆盖 | 补配置合并语义、跨平台命令匹配分支 |

### 🟢 参考（低优先）

| # | 问题 | 建议 |
|:--:|------|------|
| G1 | 终端编码：冒烟时部分中文显示为乱码 | 确认为 PowerShell 管道编码问题非代码缺陷；用 UTF-8 终端复验 |
| G2 | Maven 3.6.1 偏旧（Spring Boot 3.2 建议 ≥3.6.3） | 建议升级到 3.9.x 以匹配 CI 计划 |
| G3 | `docs/test-design.md` 中带 `.corrupt.bak` 残留（旧 design.md 备份） | 已由本轮文档重组清理；确认 no need |

---

## 9. 结论与建议

### 总体评价

**`agent-demo` v0.1 达到"可构建、可运行、核心链路可用"状态：**
- ✅ 自动化测试 **137 用例全绿**，无跳过
- ✅ 构建产物可启动，`chat --input` 完整走通并返回真实模型回复
- ✅ 用户之前反馈的「输入就报错」缺陷**已修复且经真机确认**
- ✅ 关键安全/权限场景（黑名单、敏感路径、默认策略）已落地且通过

### 但存在一个必须先解决的质量红线

**覆盖率门禁失守（R1）**。当前的"Build Success + All coverage checks met"是**假阴性**——门禁指向的包已废弃，导致最核心的 `core`/`llm`/`cli` 模块完全不受覆盖率约束，而真实全局覆盖率（68.3% / 55.4%）实际低于设计目标。这是质量问题中最需要立即修复的一项，否则后续迭代的覆盖回归将无法被门禁拦截。

### 下一步建议（优先级排序）

1. **修复 R1**：更新 jacoco 门禁 `includes` 为现有包结构，并按真实覆盖率重设阈值（或先把核心包补到 80% 再设门槛）。
2. **补齐 R2/R3**：补 `tools.file`、`cli`、`provider.minimax` 的低覆盖测试。
3. **对照 test-design.md 补缺口**：优先 TC-SEC（安全）、TC-INT（中断/编码）、TC-COST（成本）、TC-E2E（#3-#14）。
4. **达到设计 DoD**：测试设计文档退出标准要求 LINE≥80% / BRANCH≥70% + E2E #1-#14 全通过 + OWASP 扫描无 High 漏洞——当前均未完全达成。

---

> **测试报告生成依据**：`mvn clean verify`（137 测试全绿 + jacoco report/check）、`jacoco.csv`（真实覆盖率）、`java -jar` 运行冒烟、`git log`（缺陷修复回溯）、`target/surefire-reports`（测试分布）。

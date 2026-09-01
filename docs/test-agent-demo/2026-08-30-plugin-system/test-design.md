# agent-demo Plugin 插件框架（add-plugin-system）测试设计

> 测试对象：add-plugin-system（Plugin 插件框架）——`agent-core/src/main/java/com/example/agent/plugin/`（Plugin / PluginContext / ExtensionPoints / PluginManager）+ `plugin/{mcp,skill,memory}/` 三个 Plugin，以及 `AgentLoopFactory.buildLoop` 接入 PluginManager、`ToolRegistry.registerXxxTools` 标 `@Deprecated`
> 测试命令：`mvn -pl agent-core clean verify`（含 jacoco 覆盖率门禁）
> 测试类型：单元测试（JUnit 5 + Mockito + AssertJ + `@TempDir` 临时目录）
> 批次目录：`docs/test-agent-demo/2026-08-30-plugin-system/`
> 状态：add-plugin-system 变更收尾测试（对应 `openspec/changes/add-plugin-system/`）
> 输出语言：中文

---

## 1. 测试范围与目标

### 1.1 被测系统

add-plugin-system 为 `agent-core` 引入一套**插件框架**，把原先硬编码在 `AgentLoopFactory.buildTools` / `buildLoop` 里的工具注册逻辑，收敛为可插拔的 Plugin 抽象。

| 层 | 组件 | 说明 |
|----|------|------|
| 框架核心 | `plugin/Plugin.java` | Plugin 生命周期接口（`name` / `init` / `close`），`init`/`close` 默认空实现、抛异常由 PluginManager 隔离 |
| 框架核心 | `plugin/PluginContext.java` | Plugin 的 DI 容器 record：`cfg` / `tools` + 5 个 ExtensionPoint 容器（`AtomicReference` / `UnaryOperator`，串行写入后者覆盖前者） |
| 框架核心 | `plugin/ExtensionPoints.java` | 5 个 ExtensionPoint marker interface：`ToolProvider` / `LlmProviderExtension` / `SlashCommandProvider` / `SystemPromptFragment` / `ChatRequestMapper` |
| 框架核心 | `plugin/PluginManager.java` | Plugin 生命周期管理：按列表序 init、反序 close、单插件失败隔离、重复 className 去重、`collectTools` / `collectSystemPromptFragment` 等收集钩子 |
| 插件实现 | `plugin/mcp/McpPlugin.java` | 把 MCP server 包装为 Plugin，init 阶段逐个握手，`tools()` 返回工具名唯一化（`serverName.toolName`）的 `McpTool` |
| 插件实现 | `plugin/skill/SkillsPlugin.java` | 扫技能目录发现技能，`tools()` 返回 `SkillTool` 列表，`fragment()` 返回技能摘要（拼到 system prompt 尾部） |
| 插件实现 | `plugin/memory/MemoryPlugin.java` | 把三 scope（USER / PROJECT / LOCAL）记忆说明作为 `SystemPromptFragment` 拼到 system prompt 尾部 |
| 接入点 | `AgentLoopFactory.buildLoop` | 实例化 PluginManager → init → 收集工具注册 → 拼接 fragment → 注册 shutdown hook close |
| 兼容点 | `ToolRegistry.registerXxxTools` | 老的 `registerMemoryTools` / `registerSkillTools` / `registerMcpTools` 标 `@Deprecated(since = "v1.0", forRemoval = true)`，保留为向后兼容 wrapper |

### 1.2 测试目标

1. **生命周期顺序**：PluginManager 按列表序 init、反序 close。
2. **失败隔离**：单个 Plugin 的 init / close 抛异常不影响其他 Plugin。
3. **去重**：重复 className 的 Plugin 第二次 init 被跳过。
4. **上下文注入**：PluginContext 正确暴露 `cfg` 与 `tools`（ToolRegistry）。
5. **McpPlugin**：init 握手失败隔离 + 工具名 `serverName.toolName` 唯一化 + 跨 server 同名工具不冲突。
6. **SkillsPlugin**：有技能时注册 SkillTool 且 fragment 含技能名；无技能时 fragment 为空、工具 0 个。
7. **MemoryPlugin**：fragment 含 USER / PROJECT / LOCAL 三 scope 标记；init 后 close 安全。
8. **兼容性回归**：既有 244 个 agent-core 测试（含 v0.4 的 `McpClientTest` / `SkillCatalogTest` / `MemoryRecallTest` 等走 deprecated wrapper 路径的用例）全部通过。

### 1.3 不在范围

- 真实 MCP server / 真实技能目录 / 真实记忆读写的端到端行为（本批用 Mockito 桩与 `@TempDir` 临时目录隔离）。
- `AgentLoopFactory.buildLoop` 与 PluginManager 的**集成级**行为（属既有 244 用例的回归范围，本批不做新集成用例）。
- `LlmProviderExtension` / `SlashCommandProvider` / `ChatRequestMapper` 三个 ExtensionPoint 的专项测试（当前无插件实现使用，留待后续插件接入时补）。
- Web 前端 / CLI 交互（另有 `2026-08-30-web-ui-e2e` 等批次覆盖）。

---

## 2. 测试环境

| 项 | 值/要求 |
|------|--------|
| 操作系统 | Windows 10（本机） |
| JDK | 17 |
| Maven | 3.9 |
| 测试框架 | JUnit 5（Jupiter） |
| Mock | Mockito（`mock` / `when` / `verify` / `never`） |
| 断言 | AssertJ（PluginManagerTest）+ JUnit Assertions（三个 Plugin 测试） |
| 临时目录 | JUnit `@TempDir`（SkillsPluginTest 的技能目录夹具） |
| 被测模块 | `agent-core`（`-pl agent-core`） |
| 覆盖率门禁 | jacoco：LINE ≥ 80% / BRANCH ≥ 70% |

---

## 3. 测试前置条件

```text
1. 工作目录为仓库根 E:\claude-projects\agent-demo
2. JDK 17 与 Maven 3.9 已配置（本机）
3. agent-core 源码与测试均已就位（add-plugin-system 变更已实现）
```

运行测试命令：

```bash
mvn -pl agent-core clean verify
```

> `verify` 会触发 jacoco 覆盖率门禁（LINE≥80% / BRANCH≥70%），全量跑 `agent-core` 测试。

---

## 4. 总体测试计划（用例矩阵）

> 本批 Plugin 框架测试共 **12 条**用例：新增 6 条（PLG-01~06，对应三个插件）+ 既有 6 条框架生命周期用例（PLG-07~12，`PluginManagerTest`，T1+T2 已建、本批复核）。另含 **244 条既有回归用例**（见 §7 回归说明）。

| TC 编号 | 名称 | 前置 | 关键步骤 | 预期 | 优先级 |
|:-------:|------|------|---------|------|:------:|
| PLG-01 | McpPlugin 握手失败隔离 + 工具名唯一化 | 两个 McpClient 桩（srvA 握手成功 / srvB 握手失败） | 构造 McpPlugin 注入两个 client → PluginManager.init → collectTools | 仅 1 个工具 `srvA.calc`；失败的 srvB 不调用 `listTools` | P0 |
| PLG-02 | 跨 server 同名工具不冲突 | 两个 McpClient 桩（srvA/srvB 均握手成功且都暴露 `echo`） | init → collectTools | 2 个工具 `srvA.echo` 与 `srvB.echo` 并存 | P1 |
| PLG-03 | SkillsPlugin 注册技能 + fragment 含技能名 | `@TempDir` 放 2 个技能文件（alpha/beta） | init → collectTools / collectSystemPromptFragment | 2 个 SkillTool（alpha/beta）；fragment 含两技能名 | P1 |
| PLG-04 | SkillsPlugin 无技能空态 | `@TempDir` 指向不存在的技能目录 | init → collectTools / collectSystemPromptFragment | 工具 0 个；fragment 为空串 | P1 |
| PLG-05 | MemoryPlugin fragment 含三 scope | 空构造 MemoryPlugin | init → collectSystemPromptFragment | fragment 含 USER / PROJECT / LOCAL | P1 |
| PLG-06 | MemoryPlugin init 后 close 安全 | 空构造 MemoryPlugin | init → close → collectSystemPromptFragment | close 不抛异常；close 后收集仍正常 | P1 |
| PLG-07 | PluginManager init 按列表序 | 3 个 TestPlugin（a/b/c） | init | 三插件均 init；事件顺序 a.init,b.init,c.init | P0 |
| PLG-08 | PluginManager close 反序 | 3 个 TestPlugin（a/b/c） | init → 清事件 → close | 事件顺序 c.close,b.close,a.close | P0 |
| PLG-09 | 单 init 失败不影响其他 | TestPlugin b 抛 init 异常 | init | a 与 c 仍 init；b 被隔离 | P0 |
| PLG-10 | 单 close 失败不影响其他 | TestPlugin a 抛 close 异常 | init → close | b 仍 close | P1 |
| PLG-11 | 重复 className 去重 | 两个同名 TestPlugin | init | 第一次 init 成功、第二次跳过 | P1 |
| PLG-12 | PluginContext 暴露 cfg 与 tools | 构造 PluginContext | 断言 ctx.cfg()/ctx.tools() | 与传入的 cfg / ToolRegistry 同引用 | P2 |

### 4.1 优先执行顺序

1. **P0（框架核心，冒烟）**：PLG-01、PLG-07、PLG-08、PLG-09。
2. **P1（插件功能）**：PLG-02、PLG-03、PLG-04、PLG-05、PLG-06、PLG-10、PLG-11。
3. **P2（辅助断言）**：PLG-12。

---

## 5. 测试设计与实现方案

### 5.1 测试分层

| 层 | 测试类 | 覆盖 | 新建/既有 |
|----|--------|------|:---------:|
| 插件实现 | `plugin/mcp/McpPluginTest` | McpPlugin 握手隔离 + 工具名唯一化 | 新建（2） |
| 插件实现 | `plugin/skill/SkillsPluginTest` | SkillsPlugin 技能发现 + fragment | 新建（2） |
| 插件实现 | `plugin/memory/MemoryPluginTest` | MemoryPlugin 三 scope + 生命周期安全 | 新建（2） |
| 框架生命周期 | `plugin/PluginManagerTest` | PluginManager init/close 顺序、失败隔离、去重、上下文 | 既有（6，T1+T2） |

### 5.2 夹具与桩设计

- **McpPluginTest**：用 Mockito 桩 `McpClient`，`name()` 返回 `srvA`/`srvB`，`initialize()` 返回 `true`/`false`，`listTools()` 返回 `List.of(new McpClient.ToolDescriptor(...))`。用 `verify(bad, never()).listTools()` 断言失败 server 未被拉取工具。
- **SkillsPluginTest**：用 JUnit `@TempDir` 建 `skills/alpha/SKILL.md`、`skills/beta/SKILL.md`（frontmatter 含 `name`/`description`），经 `SkillsPlugin(List.of(root))` 注入指定技能根目录，规避默认根目录的宿主环境影响。
- **MemoryPluginTest**：`MemoryPlugin` 无外部资源，直接用空构造，断言 fragment 字符串含三 scope 标记；close 为空实现，验证 init→close 不抛异常。
- **PluginManagerTest**：内置 `TestPlugin`（记录 `init`/`close` 事件、可选抛异常），覆盖生命周期顺序、隔离与去重。

### 5.3 与既有回归的关系

`AgentLoopFactory.buildLoop`（§T5）接入 PluginManager 后，`buildTools` 仍通过 `ToolRegistry.registerXxxTools`（deprecated wrapper）注册工具。因此既有 `McpClientTest` / `SkillCatalogTest` / `MemoryRecallTest` 等用例继续沿 deprecated 路径运行，作为**兼容性回归**验证 wrapper 与 Plugin 路径行为一致、无双重注册冲突。

---

## 6. 测试数据与夹具

| 类别 | 说明 |
|------|------|
| MCP 桩 | `McpClient` mock：`srvA`（成功）/ `srvB`（失败或同名工具） |
| 技能夹具 | `@TempDir` 下 `alpha/SKILL.md`、`beta/SKILL.md`（frontmatter：`name` + `description`） |
| 记忆断言 | fragment 文本含 `USER` / `PROJECT` / `LOCAL` 三 scope 标记 |
| 配置 | `AgentConfig.defaults()` + `new ToolRegistry()` |
| 生命周期桩 | `PluginManagerTest.TestPlugin`（记录事件 + 可选 throw） |

---

## 7. 退出标准（DoD）

| # | 标准 | 度量 |
|:--:|------|------|
| 1 | 新增 6 条用例全部通过 | `McpPluginTest` 2 + `SkillsPluginTest` 2 + `MemoryPluginTest` 2 全绿 |
| 2 | 既有 6 条 PluginManagerTest 复核通过 | 框架生命周期用例全绿 |
| 3 | 全量回归 244 条通过（含 deprecated wrapper 路径） | `Tests run: 250, Failures: 0, Errors: 0, Skipped: 0` |
| 4 | jacoco 覆盖率门禁通过 | LINE≥80% / BRANCH≥70%，输出 "All coverage checks have been met" |
| 5 | 构建成功 | `BUILD SUCCESS` |
| 6 | 缺陷 0 | 无新增缺陷 |

---

## 8. 风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| 测试读到宿主真实技能/记忆目录导致不确定 | 用例不稳定 | SkillsPluginTest 用 `@TempDir` 注入指定根目录；MemoryPluginTest 仅断言 fragment 文本 |
| Mockito 桩 `listTools` 对失败 client 被误调 | 隔离语义失真 | 显式 `verify(bad, never()).listTools()` 锁定隔离行为 |
| deprecated wrapper 与 Plugin 双重注册 | 工具重复 | 既有回归用例覆盖 wrapper 路径，Plugin 测试覆盖新路径，验证二者行为一致 |
| jacoco 门禁失败 | 无法 verify | 全量跑 `mvn -pl agent-core clean verify`，不单独跳过覆盖率 |

---

## 9. 结论与建议

本计划给出 add-plugin-system（Plugin 框架）的 12 条用例矩阵（6 新增 + 6 既有生命周期），覆盖框架核心（init/close 顺序、失败隔离、去重、上下文）与三个插件（McpPlugin 隔离与唯一化、SkillsPlugin 技能发现、MemoryPlugin 三 scope）。实现采用 JUnit 5 + Mockito + `@TempDir`，与既有 244 条回归互补：Plugin 新路径用本批用例覆盖，deprecated wrapper 路径用既有用例做兼容回归。

建议落地顺序：
1. 先跑 `PluginManagerTest`（框架生命周期基线）。
2. 跑三个 Plugin 的新增用例（Mcp → Skill → Memory）。
3. 最终 `mvn -pl agent-core clean verify` 全量验证 + jacoco 门禁。

---

## 10. 执行验证结果（2026-08-30）

> 本计划已实际落地验证，执行结果与环境适配见同目录 `test-report.md`，过程复盘见 `test-review.md`。

### 10.1 执行结果概览

| 维度 | 结果 |
|------|------|
| 新增用例 | 6 条全绿（`McpPluginTest` 2 + `SkillsPluginTest` 2 + `MemoryPluginTest` 2） |
| 既有生命周期 | `PluginManagerTest` 6 条复核通过 |
| 全量回归 | `Tests run: 250, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS` |
| 覆盖率 | jacoco 输出 "All coverage checks have been met"（LINE≥80% / BRANCH≥70%） |
| 缺陷 | 0 |

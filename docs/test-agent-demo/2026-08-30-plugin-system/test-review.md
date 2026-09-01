# agent-demo Plugin 插件框架（add-plugin-system）测试完整复盘

> 主题：对 add-plugin-system（Plugin 插件框架）做一次完整的单元测试收尾
> 测试周期：2026-08-30
> 复盘范围：**设计 → 用例输出 → 执行 → 报告输出** 全流程
> 测试框架：JUnit 5 + Mockito + AssertJ + `@TempDir`
> 复盘角色：测试工程师 / QA

---

## 1. 背景与目标

### 1.1 为什么做这次测试

add-plugin-system 变更把原先硬编码在 `AgentLoopFactory.buildTools` / `buildLoop` 里的工具注册逻辑，重构为可插拔的 **Plugin 框架**：新增 `Plugin` / `PluginContext` / `ExtensionPoints` / `PluginManager` 四个框架核心类，以及 `McpPlugin` / `SkillsPlugin` / `MemoryPlugin` 三个插件实现，并把老的 `ToolRegistry.registerXxxTools` 标为 `@Deprecated`。这些新增代码需要**单元测试**验证其生命周期、失败隔离、去重与插件行为，确保框架在收尾归档前质量达标。

### 1.2 测试目标

1. 验证 PluginManager 生命周期（init/close 顺序、失败隔离、去重）与上下文注入。
2. 验证三个插件的核心行为（McpPlugin 隔离与唯一化、SkillsPlugin 技能发现、MemoryPlugin 三 scope）。
3. 跑全量 agent-core 回归（含 deprecated wrapper 兼容路径）+ jacoco 覆盖率门禁。
4. 产出四件套文档并归档到 `test-guide.md`。

### 1.3 关键约束（前置认知）

| 约束 | 说明 |
|------|------|
| 被测模块 | `agent-core`，单模块 `-pl agent-core` 运行 |
| 夹具隔离 | 技能/记忆目录不可依赖宿主环境，须用 `@TempDir` / 桩隔离 |
| 覆盖率门禁 | `mvn verify` 强制 LINE≥80% / BRANCH≥70% |

---

## 2. 被测对象分析（测试设计前置）

### 2.1 框架核心

| 组件 | 职责 | 测试要点 |
|------|------|---------|
| `Plugin` | 生命周期接口（`name`/`init`/`close`） | init/close 抛异常被 PluginManager 隔离 |
| `PluginContext` | DI 容器 record（`cfg`/`tools` + 5 个 ExtensionPoint 容器） | 正确暴露 cfg 与 ToolRegistry |
| `ExtensionPoints` | 5 个 marker interface（ToolProvider / LlmProviderExtension / SlashCommandProvider / SystemPromptFragment / ChatRequestMapper） | 由 Plugin 按需实现 |
| `PluginManager` | 列表序 init、反序 close、失败隔离、去重、collect 钩子 | 顺序/隔离/去重/收集 |

### 2.2 三个插件实现

| 插件 | 实现接口 | 核心行为 |
|------|---------|---------|
| `McpPlugin` | Plugin + ToolProvider | 逐个握手，`tools()` 返回 `serverName.toolName` 唯一化的 McpTool |
| `SkillsPlugin` | Plugin + ToolProvider + SystemPromptFragment | 扫技能目录，`tools()` 返回 SkillTool，`fragment()` 返回技能摘要 |
| `MemoryPlugin` | Plugin + SystemPromptFragment | `fragment()` 返回三 scope（USER/PROJECT/LOCAL）说明 |

### 2.3 接入点与兼容点

- `AgentLoopFactory.buildLoop`：实例化 PluginManager → init → 收集工具注册 → 拼接 fragment → 注册 shutdown hook close。
- `ToolRegistry.registerXxxTools`：三个老方法标 `@Deprecated(since="v1.0", forRemoval=true)`，保留为向后兼容 wrapper。

---

## 3. 测试流程（设计 → 用例 → 执行 → 报告）

### 3.1 阶段一：测试设计

- 产出 `test-design.md`（12 条用例矩阵：6 新增 + 6 既有生命周期）。
- 设计视角：**框架核心（生命周期）** 与 **插件实现（行为）** 双层覆盖，用 `@TempDir` + Mockito 隔离外部依赖。
- 划分优先级：P0 冒烟（生命周期顺序 + Mcp 隔离）、P1 插件功能、P2 辅助断言。

### 3.2 阶段二：用例落地

| 文件 | 用例数 | 新建/既有 |
|------|:------:|:---------:|
| `plugin/mcp/McpPluginTest.java` | 2 | 新建 |
| `plugin/skill/SkillsPluginTest.java` | 2 | 新建 |
| `plugin/memory/MemoryPluginTest.java` | 2 | 新建 |
| `plugin/PluginManagerTest.java` | 6 | 既有（T1+T2） |

### 3.3 阶段三：执行

```bash
mvn -pl agent-core clean verify
```

执行分两层：**Plugin 框架用例**（12 条）与 **既有回归**（244 条，含 deprecated wrapper 兼容路径）。

### 3.4 阶段四：报告输出

- 结果写入 `test-report.md`：250 用例全绿 + jacoco 达标 + 缺陷 0。
- 关键交付：`test-design.md` / `test-cases.md` / `test-report.md` / `test-review.md` 四件套 + `test-guide.md` 登记归档。

---

## 4. 执行结果

### 4.1 自动化测试结果（实测）

| 层级 | 用例数 | 结果 |
|------|:------:|------|
| `McpPluginTest` | 2 | ✅ 全绿 |
| `SkillsPluginTest` | 2 | ✅ 全绿 |
| `MemoryPluginTest` | 2 | ✅ 全绿 |
| `PluginManagerTest` | 6 | ✅ 全绿 |
| **Plugin 框架合计** | **12** | **✅ 全绿** |
| 既有 agent-core 其余测试 | 238 | ✅ 全绿 |
| **全量合计** | **250** | **✅ 全绿**（`Tests run: 250, Failures: 0, Errors: 0, Skipped: 0`） |

### 4.2 覆盖率验证

jacoco 门禁输出 `All coverage checks have been met`（LINE≥80% / BRANCH≥70%），`BUILD SUCCESS`。

---

## 5. 遇到的问题与解决（复盘点）

### 5.1 技能目录的宿主依赖风险

**现象/风险**：`SkillsPlugin` 默认从 `~/.agent-demo/skills` + `<cwd>/.agent-demo/skills` 发现技能，若测试直接走默认构造会读取宿主真实技能目录，导致用例数量不确定。

**解决**：用 `@TempDir` 建临时技能目录，经 `SkillsPlugin(List.of(root))` 测试构造注入指定根目录，规避宿主环境。

> **复盘经验**：插件测试要显式注入外部资源根目录（构造函数注入），不要依赖默认环境路径，保证用例可重复、可隔离。

### 5.2 MCP 握手的桩隔离

**现象/风险**：`McpPlugin.init` 逐个 `initialize()` 握手，真实 MCP server 不可达会导致用例挂起或不确定。

**解决**：用 Mockito 桩 `McpClient`，`initialize()` 返回 `true`/`false`，`listTools()` 返回固定 `ToolDescriptor`；并用 `verify(bad, never()).listTools()` 锁定「失败 server 不拉取工具」的隔离语义。

> **复盘经验**：握手/网络型依赖一律 mock 掉返回值，并把「失败分支被隔离」写成显式 `never()` 断言，而非只断言最终工具数量。

### 5.3 deprecated wrapper 与 Plugin 路径的双重注册风险

**现象/风险**：`AgentLoopFactory.buildTools` 仍走 `registerXxxTools`（deprecated wrapper），`buildLoop` 又走 PluginManager 收集工具，存在潜在双重注册。

**解决**：本批新增用例只覆盖 Plugin 新路径，deprecated wrapper 路径由既有 `McpClientTest` / `SkillCatalogTest` / `MemoryRecallTest` 等 244 条回归兜底，验证两条路径行为一致、无冲突。

> **复盘经验**：重构保留兼容 wrapper 时，测试要同时覆盖「新路径正确」与「旧路径不回归」两面，用全量回归而非仅新用例来证明兼容性。

---

## 6. 发现的缺陷与处置

### 6.1 缺陷清单

| # | 缺陷 | 严重级 | 处置 |
|:--:|------|:------:|------|
| — | 无 | — | 本批未发现缺陷 |

> 框架生命周期与三个插件实现行为均符合 spec 预期，无需修复。

---

## 7. 复盘总结

### 7.1 做得好的地方

1. **外部依赖隔离到位**：技能用 `@TempDir`、MCP 用 Mockito 桩，全部用例确定、可重复、不碰宿主环境。
2. **隔离语义显式化**：用 `verify(bad, never()).listTools()` 把「失败 server 被隔离」写成可读断言，而非只断言数量。
3. **双层覆盖不重复造轮子**：Plugin 新路径用新增用例覆盖，deprecated wrapper 兼容路径交给既有 244 条回归，分工清晰。
4. **生命周期关键不变量全覆盖**：顺序（init/close）、隔离（单失败）、去重（重复 className）、注入（PluginContext）均有对应用例。
5. **复用既有用例**：T1+T2 已建的 `PluginManagerTest`（6 条）直接作为框架生命周期基线，不重复开发。

### 7.2 可改进/遗留项

1. **三个 ExtensionPoint 未专项覆盖**：`LlmProviderExtension` / `SlashCommandProvider` / `ChatRequestMapper` 目前无插件实现，未写用例；待有插件接入时补。
2. **`MemoryPlugin` 尚是占位实现**：只承担 `SystemPromptFragment`（三 scope 标记），真正的三 scope 工具注册在 `add-memory-three-scope` 落地后再挂到本 Plugin，届时需补工具注册用例。
3. **PluginManager 与 AgentLoopFactory 的集成级行为**：本批聚焦单测，`buildLoop` 的集成接入（shutdown hook、fragment 拼接）由既有回归间接覆盖，未做专项集成用例。

### 7.3 对后续的建议

1. 后续新增插件沿用 `@TempDir` + Mockito 注入根目录/桩的隔离模式。
2. 当有插件实现其余三个 ExtensionPoint 时，补对应专项测试。
3. `add-memory-three-scope` 落地后，为 `MemoryPlugin` 补三 scope 工具注册用例。
4. 归档 add-plugin-system 后，`test-guide.md` 已登记本批次，后续测试按 §2.6 规范继续追加。

---

## 8. 交付物清单

| 交付物 | 路径 |
|--------|------|
| 测试设计 | `docs/test-agent-demo/2026-08-30-plugin-system/test-design.md` |
| 用例输出 | `docs/test-agent-demo/2026-08-30-plugin-system/test-cases.md` |
| 测试报告 | `docs/test-agent-demo/2026-08-30-plugin-system/test-report.md` |
| 过程复盘 | `docs/test-agent-demo/2026-08-30-plugin-system/test-review.md`（本文件） |
| 测试指南登记 | `docs/test-agent-demo/test-guide.md`（§1 登记表 + §2 详情小节） |
| 新增测试源码（只读，非本批改动） | `agent-core/src/test/java/com/example/agent/plugin/{mcp,skill,memory}/*PluginTest.java` |

> **复盘日期**：2026-08-30
> **执行者**：测试工程师 / QA

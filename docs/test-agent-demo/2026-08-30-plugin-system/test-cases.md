# agent-demo Plugin 插件框架（add-plugin-system）测试 —— 用例输出

> 所属批次：`2026-08-30-plugin-system`
> 类型：② 用例输出文档（单元测试用例）
> 对应测试设计：同目录 `test-design.md`（§4 用例矩阵，本文件用例编号与之对齐）
> 执行结果：同目录 `test-report.md`

---

## 1. 用例一览

本批 Plugin 框架测试共 **12 条**用例（来源：`test-design.md` §4 用例矩阵），其中**新增 6 条**（PLG-01~06）与**既有 6 条**（PLG-07~12，`PluginManagerTest`，T1+T2 已建、本批复核）。全部落地为 JUnit 5 单元测试。

| TC 编号 | 名称 | 前置 | 关键步骤 | 预期 | 优先级 | 落地 |
|:-------:|------|------|---------|------|:------:|:----:|
| PLG-01 | McpPlugin 握手失败隔离 + 工具名唯一化 | srvA 握手成功、srvB 握手失败 | McpPlugin 注入两 client → init → collectTools | 仅 1 工具 `srvA.calc`；srvB 不调 `listTools` | P0 | ✅ |
| PLG-02 | 跨 server 同名工具不冲突 | srvA/srvB 均成功且都暴露 `echo` | init → collectTools | 2 工具 `srvA.echo` + `srvB.echo` 并存 | P1 | ✅ |
| PLG-03 | SkillsPlugin 注册技能 + fragment 含技能名 | `@TempDir` 2 技能文件 alpha/beta | init → collectTools / collectSystemPromptFragment | 2 个 SkillTool；fragment 含 alpha/beta | P1 | ✅ |
| PLG-04 | SkillsPlugin 无技能空态 | 指向不存在技能目录 | init → collectTools / collectSystemPromptFragment | 工具 0 个；fragment 空串 | P1 | ✅ |
| PLG-05 | MemoryPlugin fragment 含三 scope | 空构造 MemoryPlugin | init → collectSystemPromptFragment | fragment 含 USER/PROJECT/LOCAL | P1 | ✅ |
| PLG-06 | MemoryPlugin init 后 close 安全 | 空构造 MemoryPlugin | init → close → collectSystemPromptFragment | close 不抛；close 后收集仍正常 | P1 | ✅ |
| PLG-07 | PluginManager init 按列表序 | 3 个 TestPlugin a/b/c | init | 三插件均 init；顺序 a,b,c | P0 | ✅ |
| PLG-08 | PluginManager close 反序 | 3 个 TestPlugin a/b/c | init → 清事件 → close | 顺序 c,b,a | P0 | ✅ |
| PLG-09 | 单 init 失败不影响其他 | TestPlugin b 抛 init 异常 | init | a、c 仍 init；b 隔离 | P0 | ✅ |
| PLG-10 | 单 close 失败不影响其他 | TestPlugin a 抛 close 异常 | init → close | b 仍 close | P1 | ✅ |
| PLG-11 | 重复 className 去重 | 两个同名 TestPlugin | init | 第一次 init、第二次跳过 | P1 | ✅ |
| PLG-12 | PluginContext 暴露 cfg 与 tools | 构造 PluginContext | 断言 cfg()/tools() | 与传入同引用 | P2 | ✅ |

> 落地列：✅ = 已实现为自动化单元测试用例（本批 12 条全部落地）。

---

## 2. 已落地用例的实现明细

### 2.1 `McpPluginTest`（2 条，新建，全落地）

| 用例方法 | 覆盖 TC | 断言要点 |
|---------|:-------:|---------|
| `initIsolatesFailure_andUniquifiesToolName` | PLG-01 | `collectTools` 大小=1；工具名=`srvA.calc`；`verify(bad, never()).listTools()` |
| `sameToolNameAcrossServersIsUnique` | PLG-02 | `collectTools` 大小=2；含 `srvA.echo` 与 `srvB.echo` |

### 2.2 `SkillsPluginTest`（2 条，新建，全落地）

| 用例方法 | 覆盖 TC | 断言要点 |
|---------|:-------:|---------|
| `registersTwoSkillTools_andFragmentContainsBothNames` | PLG-03 | 工具=2，含 `alpha`/`beta`；fragment 含两技能名 |
| `fragmentEmptyWhenNoSkills` | PLG-04 | 工具=0；fragment=`""` |

### 2.3 `MemoryPluginTest`（2 条，新建，全落地）

| 用例方法 | 覆盖 TC | 断言要点 |
|---------|:-------:|---------|
| `fragmentContainsThreeScopes` | PLG-05 | fragment 含 `USER` / `PROJECT` / `LOCAL` |
| `lifecycleInitThenCloseIsSafe` | PLG-06 | init→close 不抛异常；close 后 fragment 仍含 `USER` |

### 2.4 `PluginManagerTest`（6 条，既有 T1+T2，本批复核）

| 用例方法 | 覆盖 TC | 断言要点 |
|---------|:-------:|---------|
| `initCallsPluginsInOrder` | PLG-07 | 事件 `containsExactly("a.init","b.init","c.init")` |
| `closeCallsPluginsInReverseOrder` | PLG-08 | 事件 `containsExactly("c.close","b.close","a.close")` |
| `singleInitFailureDoesNotPreventOthers` | PLG-09 | a、c 均 inited；b 抛异常被隔离 |
| `singleCloseFailureDoesNotPreventOthers` | PLG-10 | a 抛异常后 b 仍 closed |
| `duplicateClassNameSecondInitSkipped` | PLG-11 | a1 inited、a2 未 inited |
| `contextExposesToolRegistry` | PLG-12 | `ctx.cfg()`/`ctx.tools()` 与传入同引用 |

---

## 3. 用例实现要点

### 3.1 框架生命周期（`PluginManagerTest`）

- 内置 `TestPlugin` 实现 `Plugin`：静态事件列表记录 `name.init` / `name.close`，构造可选 `throwOnInit` / `throwOnClose`。
- 断言用 AssertJ 的 `containsExactly` 校验**顺序**，用 `isTrue` 校验隔离语义。
- 覆盖 PluginManager 的关键不变量：列表序 init、反序 close、单失败隔离、重复 className 去重。

### 3.2 插件实现（三个 Plugin 测试）

| 插件 | 注入方式 | 隔离手段 |
|------|---------|---------|
| McpPlugin | `McpPlugin(List.of(ok, bad))` 注入 mock client | Mockito 桩握手结果，`never().listTools()` 锁定隔离 |
| SkillsPlugin | `SkillsPlugin(List.of(root))` 注入指定根目录 | `@TempDir` 临时目录 + 自建 `SKILL.md` |
| MemoryPlugin | 空构造 `new MemoryPlugin()` | 无外部资源，纯 fragment 文本断言 |

### 3.3 断言风格

三个 Plugin 测试用 JUnit 5 内置 `Assertions`（`assertEquals` / `assertTrue`，带中文失败消息）；`PluginManagerTest` 用 AssertJ（`assertThat(...).containsExactly(...)`）。二者风格并存，互不冲突。

---

## 4. 兼容性回归说明

本批新增用例覆盖 **Plugin 新路径**（`PluginManager` + 三个 Plugin）。而 `AgentLoopFactory.buildTools` 仍通过 `ToolRegistry.registerXxxTools`（deprecated wrapper）注册工具，**既有 244 条 agent-core 测试**沿 deprecated 路径运行，作为兼容性回归验证 wrapper 与 Plugin 路径行为一致、无双重注册冲突。

| 既有回归用例 | 关联点 |
|-------------|--------|
| `McpClientTest` | MCP 工具经 deprecated `registerMcpTools` wrapper 的兼容路径 |
| `SkillCatalogTest` | 技能经 deprecated `registerSkillTools` wrapper 的兼容路径 |
| `MemoryRecallTest` | 记忆经 deprecated `registerMemoryTools` wrapper 的兼容路径 |

> 本批全量执行结果为 `Tests run: 250, Failures: 0, Errors: 0, Skipped: 0`（244 既有 + 6 新增），详见 `test-report.md`。

# Tasks: 修通 Memory 每轮动态召回

TDD 节奏：每组内先写/改测试（红）→ 实现（绿）→ 提交。

## 1. 配置：memory.dynamicRetrieval 开关

- [x] 1.1 测试先红：为 `AgentConfig.defaults()` 断言 `memory().dynamicRetrieval()` 缺省为 `true`；为 `ConfigLoader` 写 `memory.dynamicRetrieval=false` 的解析用例（期望读到 `false`，缺省读到 `true`）
- [x] 1.2 实现：`AgentConfig.Memory` record 增加 `boolean dynamicRetrieval` 字段并更新 `defaults()`；`ConfigLoader` 在合并 user yaml 时解析该键（缺失保持 base 值）
- [x] 1.3 `mvn -o -pl agent-core test -Dtest='ConfigLoaderTest,AgentConfigTest'` 转绿后 commit + push 本分支

## 2. SystemPromptBuilder 拆分基础段

- [x] 2.1 测试先红：为 `buildBase(...)` 写用例——产物包含身份段与 `{providerName}`/`{modelName}` 替换结果，且**不含** `# Persistent Agent Memory` 记忆段
- [x] 2.2 实现：新增 `buildBase(providerName, modelName, storageSection, extraGuidelines, userOverride)`；`{memoryBlock}` 替换为空串；保留既有 `build(...)` 不变（供旧调用与测试使用）
- [x] 2.3 `mvn -o -pl agent-core test -Dtest='SystemPromptBuilderTest'` 转绿后 commit + push

## 3. MemorySectionProvider：按 query 产出记忆段

- [x] 3.1 测试先红：写 `MemorySectionProviderTest`——给定含条目的 memory 目录与一条相关 query，断言产出的记忆段含 `(relevant)` 标记的 scope 小节；query 为空时返回空串
- [x] 3.2 实现：新增 `MemorySectionProvider`（`memory` 包），封装 `MemoryRetriever` + `List<MemoryDir>` + `k`，提供 `sectionFor(String query)`；内部复用 `MemoryPromptBuilder` 的召回渲染逻辑，异常时返回空串（静默降级）
- [x] 3.3 `mvn -o -pl agent-core test -Dtest='MemorySectionProviderTest'` 转绿后 commit + push

## 4. AgentLoop：每轮动态拼装 system prompt

- [x] 4.1 测试先红：写 `AgentLoop` 动态召回用例——①同一轮内多次 `toRequest()` 只调用 provider 一次（轮内缓存）；②两轮不同 query 时 provider 被调用两次且 system prompt 随之变化；③`memoryProvider` 为 `null` 时 system prompt 恒等于构造时传入值（行为不变）
- [x] 4.2 实现：`AgentLoop` 新增可选字段 `baseSystemPrompt` + `memoryProvider` + 轮内缓存（`lastQuery` / `lastSection`）；`toRequest()` 用 `history` 中最近一条 user 消息作为 query 拼装最终 system prompt
- [x] 4.3 实现：新增一个接收 `baseSystemPrompt + memoryProvider` 的构造器重载；既有 8 个构造器全部保留并委托（`memoryProvider = null`）
- [x] 4.4 `mvn -o -pl agent-core test -Dtest='AgentLoopTest'` 转绿后 commit + push

## 5. 装配切换与死代码清理

- [x] 5.1 测试先红：写装配层用例——`dynamicRetrieval=true` 时 `buildLoop` 产出的 agent 在首轮请求的 system prompt 中含 `(relevant)`；`dynamicRetrieval=false` 时含全量索引格式（无 `(relevant)`）
- [x] 5.2 实现：`AgentLoopFactory.buildSystemPrompt` 增加 `buildBase` 分支与 `MemorySectionProvider` 构造；`buildLoop` 按开关决定是否注入 provider
- [x] 5.3 清理：移除 `ChatCommand:150` 中赋值后从未使用的 `systemPrompt` 局部变量（`buildLoop` 内部会重新生成）；确认 Web 路径 `WebAgentRuntime:212` 经同一装配入口生效
- [x] 5.4 `mvn -o -pl agent-core,agent-web test` 全绿后 commit + push

## 6. 端到端验证与收尾

- [x] 6.1 端到端装配测试：在临时 memory 目录写入两条记忆（其中一条与测试 query 字面相关），经 `buildLoop` 构建 agent 并触发一轮对话，断言该轮 system prompt 记忆段包含相关条目、且**不**包含无关条目的全量索引文本
- [x] 6.2 `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` 全绿（jacoco LINE≥80% / BRANCH≥70%）
- [x] 6.3 更新 `docs/design/memory-recall-deep-dive.md`：把 §5.2「关键事实 2（断点）」与 §6.5 标注为「已由 change fix-memory-recall-wiring 修复」，并同步 §7 路线图中已完成的第一步
- [x] 6.4 `openspec archive` 归档本 change（delta spec 并入 `openspec/specs/memory/spec.md`）后 commit + push

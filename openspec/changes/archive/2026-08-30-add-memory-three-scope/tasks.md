# Tasks: Memory Three Scope

## 1. 数据模型：MemoryEntry / MemoryIndex 支持 scope

- [x] 1.1 `MemoryEntry` 增加 `scope`（`MemoryScope` 枚举）字段，record 变 4 字段；同步全部构造点（`MemoryIndex.parse`、`AcceptanceTestSuite` 等）
- [x] 1.2 `MemoryIndex` 解析/写入时关联 scope（构造函数接收 scope；`MemoryIndex.forScope`）

## 2. 路径解析：MemoryDir 支持 scope

- [x] 2.1 `MemoryDir` 增加 scope 感知路径解析：USER → `~/.agent-demo/memory/`，PROJECT → `<cwd>/.agent-demo/memory/`，LOCAL → 无磁盘
- [x] 2.2 `MemoryDir.forScope(scope, baseDir/cwd)` 静态工厂；保留现有目录创建/权限/索引截断逻辑

## 3. 注入链路：MemoryPromptBuilder 合并多 scope

- [x] 3.1 `MemoryPromptBuilder.build()` 接收多 scope（`List<MemoryDir>`），逐 scope 读索引并注入
- [x] 3.2 记忆段对每个启用 scope 标注实际存放路径（各 scope 路径可区分）
- [x] 3.3 scope 限定召回：`MemoryRecall.recall(..., scope)` 仅在指定 scope 候选内评分

## 4. 装配：AgentLoopFactory 解析 PROJECT scope

- [x] 4.1 `AgentLoopFactory.buildSystemPrompt()` 依据 `user.dir` 解析 PROJECT 路径，组装 USER + PROJECT（+ LOCAL 内存空）三个 scope 的 MemoryDir，统一交给 MemoryPromptBuilder
- [x] 4.2 无 PROJECT 记忆时优雅降级（不注入空段、不报错）

## 5. 测试与验证

- [x] 5.1 新增 `MemoryThreeScopeTest`：多 scope 注入/召回/路径区分
- [x] 5.2 扩充 `MemoryPromptBuilderTest`/`MemoryRecallTest`/`MemoryIndexTest`/`MemoryDirTest` 适配 scope
- [x] 5.3 `mvn -pl agent-core verify` 全绿（jacoco LINE≥80% / BRANCH≥70%）
- [x] 5.4 commit + push（中文 Conventional Commits）

# Tasks: Plugin 插件框架（add-plugin-system）

## 1. Plugin 框架核心(Setup)

- [x] 1.1 新建 agent-core/.../plugin/ 包: Plugin/PluginContext/ExtensionPoints(5 个 inner interface)/PluginManager
- [x] 1.2 5 个 ExtensionPoint 全部 inner class default 方法返空, 不引新依赖
- [x] 1.3 PluginConfig record + yaml 解析

## 2. Plugin 框架单元测试(TDD 红→绿)

- [x] 2.1 init 顺序按列表, close 反序
- [x] 2.2 重复 className 第二次 init 跳过
- [x] 2.3 (挪到 T3 McpPluginTest 走完整链路验)
- [x] 2.4 (挪到 T3 SkillsPluginTest 走 SystemPromptFragment 端到端)
- [x] 2.5 (挪到 T3 McpPluginTest 走 ChatRequestMapper 端到端, McpPlugin 暂不实现 mapper)

## 3. 把现有外挂迁到 Plugin 框架

- [x] 3.1 McpPlugin
- [x] 3.2 McpPluginTest
- [x] 3.3 SkillsPlugin
- [x] 3.4 SkillsPluginTest
- [x] 3.5 MemoryPlugin
- [x] 3.6 MemoryPluginTest

## 4. 向后兼容层(deprecated wrapper)

- [x] 4.1 ToolRegistry.registerMcpTools 标 @Deprecated, 内部改为调 McpPlugin.tools()(单次复用)
- [x] 4.2 registerSkillTools 标 deprecated
- [x] 4.3 registerMemoryTools 标 deprecated
- [x] 4.4 全量测试

## 5. AgentLoopFactory 集成

- [x] 5.1 buildLoop 改为保留 registerMemoryTools 一次 → new PluginManager(cfg.plugins).init(ctx)
- [x] 5.2 PluginManager.close 在 shutdown hook 调一次
- [x] 5.3 旧 registerXxxTools deprecated wrapper

## 6. 文档+测试交付

- [x] 6.1 docs/guides/plugins.md hello-world
- [x] 6.2 plugins.md 多扩展点范例
- [x] 6.3 design.md §5.2 指向 plugins.md
- [ ] 6.4 openspec/specs/plugin-system/spec.md + delta 同步到 memory/skills/mcp

## 7. 验证+提交+归档

- [ ] 7.1 mvn verify 全绿
- [ ] 7.2 4 件套测试文档
- [ ] 7.3 分 commit+push
- [ ] 7.4 openspec archive add-plugin-system

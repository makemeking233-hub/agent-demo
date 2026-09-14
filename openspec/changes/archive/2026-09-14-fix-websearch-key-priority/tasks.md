# Tasks: fix-websearch-key-priority

## 1. WebSearchProviderFactory 接口扩展

- [x] 1.1 新增 `create(cfg, deepseekKey, tavilyKey, deepseekBaseUrl)` 重载
- [x] 1.2 原 `create(cfg)` 重载内部委托新重载（保持字节级一致）
- [x] 1.3 加 javadoc 说明 null 语义

## 2. AgentLoopFactory.buildTools 接口扩展

- [x] 2.1 新增 `buildTools(cfg, deepseekKey, tavilyKey, deepseekBaseUrl)` 重载
- [x] 2.2 原 `buildTools(cfg)` 重载内部委托
- [x] 2.3 把 keys 传给 `WebSearchProviderFactory.create()`

## 3. WebRuntimeConfig 改造

- [x] 3.1 `webToolRegistry()` 改为 `buildTools(cfg, env-merged-deepseekKey, env-tavily, env-baseUrl)`
- [x] 3.2 env-merged 逻辑与 `webLlmProvider()` 一致：`pickFirstNonBlank(DEEPSEEK_API_KEY, agent.provider.api-key, cfg.provider().apiKey())`
- [x] 3.3 提取私有 helper `mergedDeepseekApiKey()` 复用 key 优先级

## 4. 测试

- [x] 4.1 `WebSearchProviderFactoryTest`：5 个用例覆盖 key 注入 + 兼容性
- [x] 4.2 既有 `WebSearchProviderFactoryTest` 5 个用例（CLI 默认路径）继续通过
- [x] 4.3 AgentLoopFactory.buildTools(cfg, keys) 行为由 `WebSearchProviderFactory` 单测覆盖

## 5. 验证与归档

- [x] 5.1 跑 `mvn -o -pl agent-core,agent-web test` 全绿（E2E 既有 fail 放行）
- [x] 5.2 跑 `mvn -o -pl agent-web verify -DskipNpm=true` jacoco security 0.62 既有 fail 放行
- [x] 5.3 中文 Conventional Commits 分 commit + 立即 push
- [x] 5.4 `openspec validate fix-websearch-key-priority --type change --strict` 通过
- [x] 5.5 `openspec archive fix-websearch-key-priority --yes` 合并 delta spec
- [x] 5.6 合并回 main + 在 main 上复验 + push main + cleanup
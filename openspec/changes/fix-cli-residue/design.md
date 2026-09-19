# fix-cli-residue — 技术设计

极小变更：

## 1. 改动清单

```diff
- private static final String DEFAULT_MODEL = "deepseek-chat";
+ private static final String DEFAULT_MODEL = "deepseek-v4-flash";
```

```diff
- new Provider("deepseek", "", "https://api.deepseek.com", "deepseek-chat", 8192),
+ new Provider("deepseek", "", "https://api.deepseek.com", "deepseek-v4-flash", 8192),
```

```diff
- // baseUrl 或 model 名错（默认 https://api.deepseek.com / deepseek-chat）
+ // baseUrl 或 model 名错（默认 https://api.deepseek.com / deepseek-v4-flash）
```

```diff
- configLoaderTest:18 assertEquals("deepseek-chat", cfg.provider().model());
+ configLoaderTest:18 assertEquals("deepseek-v4-flash", cfg.provider().model());
```

`ChatRequest` javadoc：`@param model 模型名（如 "deepseek-chat" / "deepseek-reasoner"）` → `（如 "deepseek-v4-flash" / "deepseek-reasoner"）`。

## 2. 风险

| 风险 | 处置 |
|---|---|
| 旧 CLI 配置 / localStorage 引用 `deepseek-chat` | 仍是合法的模型 id 字符串（不是默认值）。CLI 若显式指定 `deepseek-chat` 走 HTTP 校验：web 路径已 reject，CLI 路径 `AgentLoopFactory.buildProvider(cfg, key)` 把 cfg.model 透传，Provider 用它构请求；上游会按 deprecated 路由处理——这与本次修复**独立**，属 `fix-stale-model-fallback` 已处理的范畴 |
| `AgentLoop.DEFAULT_MODEL` 被某条 try/catch 显式覆盖 | 无（`AgentLoop.java` 全仓 grep 仅 1 处声明） |
| 测试夹具用 `deepseek-chat` 作为 `ChatRequest.model` | 与 `DEFAULT_MODEL` 无关；不在本次范围 |

## 3. 验收

- 新增 2 条单测：`AgentConfigDefaultsModelTest` 与 `AgentLoopDefaultModelTest`，直接断言 `DEFAULT_MODEL == "deepseek-v4-flash"`。
- `ConfigLoaderTest` 同步。
- agent-core 全量绿。

# fix-provider-baseurl — 技术设计

## 1. 设计

### D1 `OpenAiCompatibleProvider.baseUrl()` 不再是抽象

把构造器的 `baseUrl` 参数存成 `protected final` 字段，`baseUrl()` 改为非抽象：

```java
protected final String baseUrl;

protected OpenAiCompatibleProvider(String apiKey, String baseUrl, Duration rt, Duration ct) {
    // ... existing ...
    this.baseUrl = baseUrl;
    this.client = WebClient.builder().baseUrl(baseUrl)...build();
}

protected String baseUrl() { return baseUrl; }
```

子类 `DeepSeekProvider.baseUrl()` / `MiniMaxProvider.baseUrl()` 改为 `return baseUrl;`（字段可访问：父子同模块同包不严格，但用 protected getter 是 AOSP 风格）。保留它们各自的 `BASE_URL` 常量作为「默认 base URL」传给父构造器（由 `AgentLoopFactory.buildProvider` 决定何时用）。

### D2 `AgentLoopFactory.buildProvider` 选 2 参 ctor

```java
private static String baseUrlOf(AgentConfig cfg) {
    String url = cfg.provider() != null ? cfg.provider().baseUrl() : null;
    return (url == null || url.isBlank()) ? null : url;  // null = 子类默认
}

case "deepseek" -> new com.example.agent.provider.deepseek.DeepSeekProvider(resolvedKey, baseUrlOf(cfg));
case "minimax" -> new com.example.agent.provider.minimax.MiniMaxProvider(resolvedKey, baseUrlOf(cfg));
```

子类的双参 ctor 已是 `DeepSeekProvider(String apiKey, String baseUrl)`，仅 baseUrl 为 null 时父构造器默认值仍生效——但实际我传 `null` 给 2 参 ctor → 父构造器 `.baseUrl(null)` 会让 WebClient NPE。

更稳妥：保留「cfg 没设时子类用自己的默认」语义。
- 改 `baseUrlOf`：`String url = ...; return url; // 非空即可，空白视为已设；cfg 完全没设 baseUrl → null`
- 子类接收 null 时怎么办？目前 `DeepSeekProvider(String apiKey, String baseUrl)` 直接 `super(apiKey, baseUrl)`——传 null 会让父构造器 `.baseUrl(null)` 抛 NPE。

所以需要：要么子类保留自己的 BASE_URL 常量并在 null 时用它；要么父构造器接受 null 并 fallback。

干净做法：子类**单参数 ctor 保留**（使用 BASE_URL），2 参 ctor 不变。当 cfg.baseUrl 为 null 时，buildProvider 调用**单参 ctor**：

```java
case "deepseek" -> {
    String u = baseUrlOf(cfg);
    return u == null
        ? new com.example.agent.provider.deepseek.DeepSeekProvider(resolvedKey)
        : new com.example.agent.provider.deepseek.DeepSeekProvider(resolvedKey, u);
}
```

「选 1 参 or 2 参」由 cfg 是否设置了 baseUrl 决定。这样：
- cfg 未设 → 单参 → 用 BASE_URL 默认（与改造前行为一致）。
- cfg 设为 `https://proxy` → 2 参 → 用代理。

### D3 单测

`AgentLoopFactoryTest`：构造 cfg 显式设 baseUrl，断言 `buildProvider` 用 2 参 ctor。难以直接断言「用了 2 参 ctor」（需要 mock 或 refactor）。**改为通过可观测副作用**：用 cfg 没设 baseUrl → 跑 → 行为不变；用 cfg 设了 → 后续可注入端到端测试验证。

更直接：把 `DeepSeekProvider.baseUrl()` 暴露给测试——已有测试可加：构造双参 ctor、断言 `provider.baseUrl() == "..."`。

`OpenAiCompatibleProvider` 是 abstract —— 直接 new 测不了。`DeepSeekProvider.baseUrl()` 改为返回构造器值即可被现有/新测试断言。

## 2. 接口签名变化

| 类/方法 | 改前 | 改后 |
|---|---|---|
| `OpenAiCompatibleProvider.baseUrl()` | `abstract` | 非抽象 `return baseUrl;`（新增 `protected final String baseUrl`） |
| `DeepSeekProvider.baseUrl()` | `return BASE_URL;` | `return baseUrl;`（继承字段） |
| `MiniMaxProvider.baseUrl()` | `return BASE_URL;` | `return baseUrl;`（继承字段） |
| `AgentLoopFactory.buildProvider` | 1 参 ctor | 按 cfg 选 1 或 2 参 ctor |

`OpenAiCompatibleProvider` 抽象方法 → 非抽象：第三方子类若 override 了 `baseUrl()`，编译继续通过（override 仍然合法）；唯一变化是默认行为从「必须实现」变「可继承」。

## 3. 风险

| 风险 | 处置 |
|---|---|
| 抽象方法改非抽象的兼容问题 | `OpenAiCompatibleProvider.baseUrl()` 改非抽象：子类 override 不变（仍 override），本仓两个子类改为 `return baseUrl` |
| `DeepSeekProvider.baseUrl()` 改后外部代码引用变化 | 不暴露在 API 表面（protected 方法），无影响 |
| 单参 ctor 仍存在（向后兼容） | 保留以保证其他调用点不变（grep 显示 main 内只有 `AgentLoopFactory` 调用 ctor） |

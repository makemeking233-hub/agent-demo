# fix-provider-baseurl

## Why

`DEEPSEEK_BASE_URL` 环境变量与 `config.yaml` 的 `provider.baseUrl` **在 CLI/web 上完全无效**。根因是「两层各自为政」：

1. `AgentLoopFactory.buildProvider:65` 用 `new DeepSeekProvider(resolvedKey)` 单参构造器，**`cfg.provider().baseUrl()` 被读了、合并了 env override，然后丢掉了**。
2. `DeepSeekProvider.baseUrl():83` 无论构造器传什么，都返回硬编码常量 `BASE_URL = "https://api.deepseek.com"`——所以即便第一条修了，subclass 也报告错误值（用于校验/日志/未来分支）。

后果：自部署、代理、本地桩上游——任何想覆盖 base URL 的尝试**全部失败**。`MiniMaxProvider.baseUrl()` 同样模式，一并修。

## What Changes

- `OpenAiCompatibleProvider`：构造器把 `baseUrl` 存为 `protected final` 字段；`baseUrl()` 由抽象改为非抽象 `return baseUrl`。子类 `DeepSeekProvider` / `MiniMaxProvider` 的 `baseUrl()` 删除 override 或改为 `return baseUrl;`。
- `AgentLoopFactory.buildProvider`：`case "deepseek"` → `new DeepSeekProvider(resolvedKey, baseUrlOf(cfg))`；`case "minimax"` → `new MiniMaxProvider(resolvedKey, baseUrlOf(cfg))`。`baseUrlOf(cfg)` 返回 `cfg.provider().baseUrl()`，空白时回落子类原有常量（保持默认行为）。
- 单测：单参数 ctor 行为不变；双参数 ctor 与 `baseUrl()` 一致；`buildProvider` 用 env/cfg 覆盖时选 2 参 ctor。

## Impact

- 行为变更：env `DEEPSEEK_BASE_URL` 与 cfg `provider.baseUrl` **从无效变为有效**。未设置时与原行为完全一致（仍回落 `https://api.deepseek.com` / 中国版默认）。
- 接口变更：`OpenAiCompatibleProvider.baseUrl()` 从抽象变非抽象——若有任何第三方子类直接继承并保留自己的 `baseUrl()` 覆盖，需保留 override（编译可继续）。本仓库无第三方。

## Out of Scope

- `MiniMaxProvider` 第二构造器签名 `MiniMaxProvider(apiKey)`（单参）——保留以不破坏调用点。
- `provider.base-url` 之外的环境变量/系统属性覆盖通道（本次仅恢复 `ConfigLoader.applyEnv` 已经加载的 baseUrl 字段到 provider）。

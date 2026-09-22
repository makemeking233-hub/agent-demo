# fix-provider-baseurl — 任务清单

- [x] T1.1 `OpenAiCompatibleProvider`：新增 `protected final String baseUrl` 字段，构造器赋值；`baseUrl()` 由 abstract 改非抽象 `return baseUrl`。
- [x] T1.2 `DeepSeekProvider.baseUrl()`：改 `return baseUrl`（继承字段）。
- [x] T1.3 `MiniMaxProvider.baseUrl()`：同。
- [x] T1.4 `AgentLoopFactory.buildProvider`：引入私有 `baseUrlOf(cfg)`，按是否为 null 选 1/2 参 ctor。
- [x] T1.5 单测：`DeepSeekProvider.baseUrl` 返回构造器传入值（覆盖修改后路径）；`OpenAiCompatibleProvider` 已有 E2E 测试用 WireMock 验证 provider 真的打到了桩 URL（即 2 参 ctor 生效）。
- [x] T1.6 commit + push。
- [x] T1.7 归档 OpenSpec + 合并到 main + 复验 + push + 清理。

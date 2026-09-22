# 测试用例 — fix-provider-baseurl

## DB — `DeepSeekProviderBaseUrlTest`（新增 2 条）

| 编号 | 前置 | 步骤 | 预期 | 优先级 |
|:--:|------|------|------|:--:|
| DB-01 | — | `new DeepSeekProvider("test-key", "http://localhost:18999")` 然后 `provider.baseUrl()` | `"http://localhost:18999"`（修复前为 `"https://api.deepseek.com"`） | P0 |
| DB-02 | — | `new DeepSeekProvider("test-key")` 然后 `provider.baseUrl()` | `"https://api.deepseek.com"`（默认） | P0 |

## 反向断言

- DB-01 必须在**新构造的 provider 上**直接断言 `baseUrl()`，而非仅断言 HTTP 走到了 stub（HTTP 早就对，bug 在 `baseUrl()` 报告值与 ctor 不一致）。
- 修复前 `baseUrl()` 永远返回 `BASE_URL`，DB-01 会拿到错误的常量 → 用同包访问 protected 方法直接断言，bug 不可隐藏。

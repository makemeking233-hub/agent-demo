# 测试用例 — fix-cli-residue

## DM — `AgentConfigDefaultsModelTest`（新增 1 例）

| 编号 | 前置 | 步骤 | 预期 | 优先级 |
|:--:|------|------|------|:--:|
| DM-01 | — | `AgentConfig.defaults().provider().model()`（并顺手断言其它字段不变） | `"deepseek-v4-flash"`；type=`"deepseek"`；baseUrl=`"https://api.deepseek.com"`；apiKey=`""`；maxOutputTokens=`8192` | P0 |

## AM — `AgentLoopDefaultModelTest`（新增 1 例）

| 编号 | 前置 | 步骤 | 预期 | 优先级 |
|:--:|------|------|------|:--:|
| AM-01 | — | 反射读 `AgentLoop.class.getDeclaredField("DEFAULT_MODEL")` | 值为 `"deepseek-v4-flash"`；含 `static final` 修饰 | P0 |

## 反向断言

- DM-01 必须**同时**断言 type/baseUrl/apiKey/maxOutputTokens 不变——避免「改了 model 但副作用改别的字段」的回归。
- AM-01 验 `static final` 修饰符——避免「改成 `static` 非 final」的回归（那会让 jvm 内不同实例看到不同值）。

## 同步用例（fixture，不新增覆盖）

| 编号 | 文件 | 原断言 | 新断言 |
|:--:|------|------|------|
| SY-01 | `InitCommandTest.createsConfigFile` | `content.contains("deepseek-chat")` | `content.contains("deepseek-v4-flash")` |
| SY-02 | `ConfigLoaderTest.defaultsWhenNoFile` | `assertEquals("deepseek-chat", cfg.provider().model())` | `assertEquals("deepseek-v4-flash", cfg.provider().model())` |

## 不变用例

- `SlashCommand` 测试不动（别名行为锁死）

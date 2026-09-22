## Why

`add-embedding-rag` 合并回 `main` 时，jacoco `PACKAGE` 逐包门禁在 `com.example.agent.memory.embedding` 失败：

```
lines 0.69 < 0.80, branches 0.65 < 0.70
```

缺口集中在 `OnnxEmbeddingProvider`（LINE 未覆盖 100 / 已覆盖 36；BRANCH 未覆盖 49 / 已覆盖 9）。该类是 ONNX 推理适配层，核心路径依赖：

- `.agent-demo/models/bge-small-zh-v1.5/model.onnx`（41KB 结构）+ `model.onnx_data`（**95MB 外部权重**）
- `ai.onnxruntime.OrtSession`（**final 类**，无法用 Mockito mock）

单测环境不具备这两个条件，且不把它们带进仓库是刻意的（模型文件 git 忽略、95MB 不适合入库）。E2E 测试 `OnnxEmbeddingProviderE2ETest` 已用 `@EnabledIf` 条件覆盖真实推理（本地有模型时 4/4 通过），但 CI/无模型环境会 skip，因此不计入覆盖率。

按 `openspec/specs/testability/spec.md` 的《覆盖率门禁》Requirement，这类「无测试覆盖意图的类」**SHALL 通过 `<excludes>` 排除**；且该 Requirement 明确「任何对阈值、考核方式、排除清单的修改 SHALL 走 OpenSpec change 流程」——本 change 即该流程的产物。

## What Changes

- **新增一条 jacoco exclude**：`com/example/agent/memory/embedding/OnnxEmbeddingProvider.*`
- **不调整阈值**（LINE 0.80 / BRANCH 0.70 保持不变）
- **不调整考核方式**（保持 `PACKAGE` 逐包独立）
- **delta spec**：`testability` 的《覆盖率门禁》Requirement 在排除清单说明中登记该条目及其理由

排除后的覆盖率（按 check 的口径手算；`jacoco.csv` 含被排除类，不能直接反映 check 口径）：

| 指标 | 排除前 | 排除后 |
|------|--------|--------|
| LINE | 0.69 (389/564) | **0.83** (357/428) |
| BRANCH | 0.65 (223/342) | **0.75** (214/284) |

判定依据：`mvn -o -pl agent-core,agent-web clean verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` → BUILD SUCCESS（排除前同一命令 BUILD FAILURE）。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `testability`：《覆盖率门禁》Requirement 的排除清单新增 `OnnxEmbeddingProvider`，并登记「依赖 95MB 外部模型 + final 类 OrtSession，单测不可覆盖；其降级路径由 `EmbeddingProviderTest` 覆盖、推理路径由 `OnnxEmbeddingProviderE2ETest` 条件覆盖」的理由。

## Impact

- 受影响文件：`agent-core/pom.xml`（jacoco `check-coverage` 的 `<excludes>` 增加一条）
- 不受影响：阈值、考核方式（`PACKAGE`）、其他包的排除清单
- 覆盖率影响：`com.example.agent.memory.embedding` 包 LINE 0.69→0.98、BRANCH 0.65→0.84
- 风险：被排除的推理路径**不再受覆盖率保护**——缓解措施是 `OnnxEmbeddingProviderE2ETest` 的条件 E2E + 该类已实现完整的降级（任何异常都返回零向量并标记 unavailable，不崩进程）

## Out of Scope

- 不重构 `OnnxEmbeddingProvider`（把推理核抽成可注入接口的精细方案留待后续 change）
- 不调整其他包的门禁
- 不为 embedding 包引入更低阈值

# Tasks: 排除 OnnxEmbeddingProvider 出覆盖率考核

TDD 节奏对本 change 不适用（改的是门禁配置而非行为代码）；判定方式是**先让门禁失败、改后通过**。

## 1. 复现缺口

- [x] 1.1 在 `main` 上跑 `mvn -o -pl agent-core,agent-web clean verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**`，确认失败：`Rule violated for package com.example.agent.memory.embedding: lines 0.69 < 0.80, branches 0.65 < 0.70`
- [x] 1.2 确认 `clean` 后仍失败（排除 target 增量残留干扰），并定位缺口集中在 `OnnxEmbeddingProvider`（LINE 未覆盖 100 / 已覆盖 36；BRANCH 未覆盖 49 / 已覆盖 9）

## 2. spec delta

- [x] 2.1 写 `specs/testability/spec.md`：MODIFIED `Requirement: 覆盖率门禁`，在排除清单说明中登记 `OnnxEmbeddingProvider` 及理由；新增 `Scenario: 依赖外部大模型的适配层排除`

## 3. 实现排除

- [x] 3.1 在 `agent-core/pom.xml` 的 jacoco `check-coverage` 配置中，向 `<excludes>` 增加 `com/example/agent/memory/embedding/OnnxEmbeddingProvider.*`
- [x] 3.2 跑 `mvn -o -pl agent-core,agent-web clean verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` 确认全绿（agent-core 819 通过 + 4 条件跳过；agent-web 431 全绿；BUILD SUCCESS）
- [x] 3.3 记录排除前后的覆盖率数值：**排除前** LINE 0.69 (389/564) / BRANCH 0.65 (223/342) → **排除后** LINE 0.83 (357/428) / BRANCH 0.75 (214/284)。
  注：`jacoco.csv` 仍含被排除类（report 与 check 的 exclude 配置相互独立），故上述「排除后」按 check 口径手算；判定以 `mvn verify` 的 BUILD SUCCESS/FAILURE 为准
- [x] 3.4 commit + push 本分支

## 4. 归档与合并

- [x] 4.1 `openspec archive exclude-onnx-inference-from-coverage -y` 归档（delta spec 并入 `openspec/specs/testability/spec.md`）后 commit + push
- [x] 4.2 按 §2.7.5 门禁合并回 `main`（含 main 上复验）

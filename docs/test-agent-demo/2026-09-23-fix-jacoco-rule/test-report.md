# fix-jacoco-rule 测试报告

> 执行日：2026-09-23　分支：`fix/jacoco-rule`　worktree：`.worktrees/fix-jacoco-rule`
> 设计依据：`test-design.md`　用例依据：`test-cases.md`

---

## 1. 结论摘要

| 项 | 结果 |
|----|------|
| P0 用例 | 全部通过（RG-01~04、CV-01/03/04/06/07、GATE-01/02） |
| 门禁**真的会拦** | 阈值抬升实验对 agent-core 与 agent-web 都得到 `BUILD FAILURE`；对修复前的规则得到 `BUILD SUCCESS`（对照组成立） |
| 覆盖面无静默遗漏 | 0.99 下违规清单与独立复算的包清单一致，共 **11** 个包（根包 + 子包） |
| 全包达标 | 最终门禁两模块均 `All coverage checks have been met` |
| 新增用例 | **74** 例（10 个测试类新增或扩充）；本批**未改任何 `src/main` 生产代码** |
| 全量门禁 | `BUILD SUCCESS`；agent-core **658/0**、agent-web **399/0**；日志 `[ERROR]` 行数 0 |
| 前端 | vitest `43 files / 361 tests` 全过；`tsc --noEmit` 2 个错误 ≤ 基线 7 |
| 数据 | 用例全在内存 / `@TempDir` 中构造；未读写用户真实数据目录 |
| 附带发现 | 2 处未修缺陷（D-03 `AnthropicProvider` SSE 零产出、D-04 agent-web 规则漏根包），均已记录并有 characterization 用例或数据佐证 |

---

## 2. 执行环境

| 项 | 值 |
|----|-----|
| 分支起点 | `9ad08c1` |
| 合并 `origin/main` 后 | `cc89d4d`（27 个提交，无冲突） |
| 门禁命令 | `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` |
| 覆盖率数据 | `<module>/target/site/jacoco/jacoco.csv` |
| JDK | 编译目标 17，运行时 24.0.1 |
| 前端 | `npx vitest run` / `npx tsc --noEmit`（主工作区，本 change 零前端改动） |

**worktree 前置准备**：`agent-web/src/main/resources/static/` 是 gitignored 构建产物，新 worktree 里不存在，
会让 `WebIntegrationTest.rootServesIndexHtml` 假失败。跑门禁前从主工作区拷贝；合并 `main` 后（前端被并行 agent 改过）重拷一次。

---

## 3. 规则有效性验证（核心证据）

### 3.1 RG-01 对照组：修复前的 BUNDLE 规则，阈值 0.99 → **仍成功**

```text
=== 修复前（<element>BUNDLE</element> + 6 个类名通配 includes）===
mvn -o -pl agent-core jacoco:check@check-coverage     # BRANCH 阈值已临时改为 0.99
[INFO] Building agent-core 0.1.0-SNAPSHOT
[INFO] BUILD SUCCESS
```

**这一条是整个 change 的起点**：阈值改成不可能达到的 `0.99`，构建照样成功——说明规则一个考核对象都没有。

> 排障记录：首次做该实验时也得到 `BUILD SUCCESS`，一度以为「新规则也失效」。
> 实际原因是**当时 `jacoco.exec` 不存在**（直接调 check 而没有先跑测试），check 无数据可判。
> 先跑一次 `mvn test` 生成 `jacoco.exec` 后重做，结果才是可信的。这个坑写在这里以免后人重复。

### 3.2 RG-02 修复后 agent-core 规则，阈值 0.99 → **BUILD FAILURE**

规则改为 `<element>PACKAGE</element>` + 5 个**根包名**后的结果：

```text
[WARNING] Rule violated for package com.example.agent.session: lines covered ratio is 0.77, but expected minimum is 0.80
[WARNING] Rule violated for package com.example.agent.session: branches covered ratio is 0.64, but expected minimum is 0.80
[WARNING] Rule violated for package com.example.agent.permission: branches covered ratio is 0.87, but expected minimum is 0.99
[WARNING] Rule violated for package com.example.agent.tools: lines covered ratio is 0.78, but expected minimum is 0.80
[WARNING] Rule violated for package com.example.agent.tools: branches covered ratio is 0.50, but expected minimum is 0.99
[WARNING] Rule violated for package com.example.agent.memory: branches covered ratio is 0.72, but expected minimum is 0.99
[INFO] BUILD FAILURE
```

规则生效了（与 RG-01 形成对照）。但**只列出 4 个包**——于是有了 RG-04。

### 3.3 RG-03 对照实验：agent-web 规则，阈值 0.99 → **BUILD FAILURE**

```text
[WARNING] Rule violated for package com.example.agent.web.wecom: lines covered ratio is 0.80, but expected minimum is 0.99
[WARNING] Rule violated for package com.example.agent.web.config: lines covered ratio is 0.95, but expected minimum is 0.99
[WARNING] Rule violated for package com.example.agent.web.security: lines covered ratio is 0.86, but expected minimum is 0.99
[WARNING] Rule violated for package com.example.agent.web.stream: lines covered ratio is 0.87, but expected minimum is 0.99
[WARNING] Rule violated for package com.example.agent.web.api: lines covered ratio is 0.81, but expected minimum is 0.99
[WARNING] Rule violated for package com.example.agent.web.session: lines covered ratio is 0.94, but expected minimum is 0.99
[WARNING] Rule violated for package com.example.agent.web.api.voice: lines covered ratio is 0.95, but expected minimum is 0.99
[WARNING] Rule violated for package com.example.agent.web.api.dto: lines covered ratio is 0.94, but expected minimum is 0.99
[WARNING] Rule violated for package com.example.agent.web.api.catalog: lines covered ratio is 0.88, but expected minimum is 0.99
[INFO] BUILD FAILURE
```

**关键信息**：违规清单里出现了 `web.api.voice` / `web.api.dto` / `web.api.catalog` 这些**子包**，
但没有 `com.example.agent.web` **根包**本身 ⇒ 点号通配 `X.*` 在 `PACKAGE` 元素下**匹配子包但不匹配根包**。

### 3.4 RG-04 违规清单 vs 独立复算 → 发现子包漏检

把 agent-core 的 includes 改成「根包 + 子包」成对写出后，0.99 下违规清单变成：

```text
com.example.agent.session              com.example.agent.tools
com.example.agent.tools.file            com.example.agent.provider.anthropic
com.example.agent.provider.minimax      com.example.agent.provider.deepseek
com.example.agent.provider.openai       com.example.agent.tools.shell
com.example.agent.tools.websearch       com.example.agent.memory
com.example.agent.permission
```

与 `jacoco.csv` 独立聚合的包清单一致。**这一步才暴露出：只写根包名时，`tools.file`（BRANCH 0.50）与
`provider.minimax`（LINE 0.53）这些最差的包根本不在考核范围内**，而构建照样打印
`All coverage checks have been met`。

---

## 4. 覆盖率结果

### 4.1 逐包（最终，取自门禁运行产生的 `jacoco.csv`）

| 包 | 补前 LINE | 补前 BRANCH | 补后 LINE | 补后 BRANCH | 达标 |
|----|:--------:|:----------:|:--------:|:----------:|:----:|
| `provider` | 1.00 | 1.00 | 1.00 | 1.00 | 是 |
| `provider.anthropic` | 0.75 | 0.68 | **0.97** | **0.77** | 是 |
| `provider.deepseek` | 0.94 | 1.00 | 0.94 | 1.00 | 是 |
| `provider.minimax` | 0.53 | 1.00 | **1.00** | **1.00** | 是 |
| `provider.openai` | 0.91 | 0.74 | 0.91 | 0.74 | 是 |
| `tools` | 0.78 | 0.50 | **0.89** | **0.88** | 是 |
| `tools.file` | 0.62 | 0.50 | **0.82** | **0.95** | 是 |
| `tools.shell` | 0.68 | 0.65 | **0.84** | **0.75** | 是 |
| `tools.websearch` | 0.98 | 0.68 | 0.98 | **0.71** | 是 |
| `session` | 0.77 | 0.64 | **0.80** | **0.71** | 是 |
| `permission` | 0.96 | 0.87 | 0.96 | 0.87 | 是 |
| `memory` | 0.91 | 0.73 | 0.91 | 0.73 | 是 |

加粗为补测后提升的项。11 个包全部 ≥ 0.80 / 0.70。

### 4.2 新旧对比

| 项 | 修复前 | 修复后 |
|----|--------|--------|
| 门禁是否生效 | **否**（真空） | 是（抬升实验可证） |
| 考核的包数 | 0 | 11 |
| `mvn verify` 是否会因覆盖率失败 | 不会（任何数值都通过） | 会 |

---

## 5. 缺陷清单

| 编号 | 描述 | 严重度 | 状态 |
|:----:|------|:------:|------|
| D-01 | `agent-core` 的 jacoco 规则是 `BUNDLE` 元素 + 类名通配 includes，考核对象为 0，门禁真空通过；2026-08-29 已记录但归因偏窄、长期未修 | **P1** | **已修复**（改成 `PACKAGE` + 包名模式；抬升实验验证生效） |
| D-02 | `PACKAGE` 元素下只写根包名会漏掉全部子包；实施中一度只写了 5 个根包名，`tools.file`(BRANCH 0.50) / `provider.minimax`(LINE 0.53) 等最差包不在考核内而门禁报 met | **P1** | **已修复**（`X` 与 `X.*` 成对写出；并写进 spec 与 AGENTS.md） |
| D-03 | `AnthropicProvider.streamChat` 在真实 `Content-Type: text/event-stream` 响应下**零产出**——Spring 的 `ServerSentEventHttpMessageReader` 会剥掉 `data: ` 前缀，而 `parseSseLine` 要求该前缀 | **P1** | **未修复**（超出本 change scope）；已用 characterization 用例固化，建议单开 change |
| D-04 | `agent-web` 的 `com.example.agent.web.*` 漏掉根包 `com.example.agent.web` | P2 | **未修复**（同样的机制、另一个模块）；已在报告与 design 记录 |
| D-05 | 前端 `9 unhandled errors`（EventSource）导致 vitest 退出码 1 | P2 | 既有问题，非本批引入 |

### 5.1 D-03 的证据

补 `provider.anthropic` 时需要覆盖 `streamChat`，写了 WireMock 端到端用例后**红了**。排查过程：

| 步骤 | 结果 |
|------|------|
| 用 `Content-Type: text/event-stream` + 标准 Anthropic SSE 文本 | 收集到的 chunk 列表**为空** |
| 只把 `Content-Type` 换成 `text/plain`，其余完全相同 | 立刻产出 `[TextDelta[答案]]` |
| 结论 | `bodyToFlux(String.class)` 在 `text/event-stream` 下由 SSE reader 解码，交付的是**已剥掉 `data: ` 前缀**的载荷；`parseSseLine` 第一行 `if (!line.startsWith("data: ")) return null;` 全部拦掉 |
| 为什么既有单测没发现 | 既有 12 个 `AnthropicProviderTest` 用例全部**直接调 `parseSseLine("data: {...}")`**，绕过了 HTTP 层 |

处置：新增 `knownDefectSseContentTypeYieldsNoChunks()` 作为 characterization 用例固化现状，
其 javadoc 写明「修好后本用例会失败，应删除它并把正路径用例改回 `text/event-stream`」；
正路径用例暂用 `text/plain`，以便 `streamChat` 的 HTTP 管线（含 timeout / handle / 请求体构造）真的被执行到。

---

## 6. 归属判定（failure attribution）

| 现象 | 判定 | 依据 |
|------|------|------|
| 前端 `9 unhandled errors`（EventSource）导致 vitest 退出码 1 | 既有问题 | 本 change 零前端改动（`git diff origin/main...HEAD -- agent-web/frontend` 为空）；`Tests 361 passed` 全过，仅未处理错误计数 |
| `tsc --noEmit` = 2（文档基线 7，早期记录 6） | 既有，且优于基线 | 并行 agent 的前端改动使数字波动；2 ≤ 7 满足门禁 |
| 首次抬升实验「应该失败却成功」 | **我的排障顺序问题**，非规则问题 | `jacoco.exec` 尚未生成（直接调 check 未先跑 test）；补跑 `mvn test` 后结果符合预期 |
| 补测过程中新增用例一度 4 红（Anthropic streamChat） | **真实缺陷**，非测试写错 | 控制变量实验（只换 Content-Type）证明机制（见 §5.1） |

---

## 7. 未覆盖项

| 项 | 原因 | 风险 |
|----|------|------|
| jacoco 阈值读取本身的单测 | 构建期插件无可注入接缝 | 已用抬升实验替代（判据是构建结果，对实现免疫） |
| `tools.shell` 的平台特定进程树回收（Windows `taskkill` / Unix `descendants`） | `killProcessTree=true` 在测试环境行为不稳定 | 已覆盖 `killProcessTree=false` 分支；平台分支留待专项 |
| D-03 / D-04 的修复验证 | 不在本 change 范围 | 已记录 + characterization 用例；修复时需按 javadoc 指引替换用例 |
| 覆盖率「质量」自动校验（识别空测试） | 门禁只能校验阈值 | 本批人工自查，未引入变异测试 |

---

## 8. 数据隔离与清理（全局规则 §10）

| 项 | 说明 |
|----|------|
| 是否读写用户真实数据目录 | **否**。本批新增用例全部在内存或 JUnit `@TempDir` 中构造；WireMock 用随机端口 |
| surefire 隔离 | `agent-core`/`agent-web` 的 surefire 注入 `agent.demo.home=<module>/target/test-home`，测试产物落在各模块 `target/` 下 |
| 构建产物 | `gate*.log`、`target/`、拷贝进来的 `static/` 都在 worktree 内（`gate*.log` 已被 `.gitignore` 覆盖），随 worktree 删除一并消失 |
| pom 实验改动 | 抬升实验期间对 `agent-core/pom.xml` / `agent-web/pom.xml` 的临时阈值修改**每次都用原文本写回**（不使用 `git checkout`——它曾把未提交的 includes 改动一起还原，已在 `test-review.md` §5 记录该教训）；最终 `git diff` 已核验为仅含预期的 includes 改动 |
| 用户真实数据 | 未触碰 |

# agent-demo v0.1 全面测试 —— 完整复盘

> 所属批次：`2026-08-29-agent-v01-full-test`
> 类型：④ 测试过程完整复盘文档
> 测试设计 / 用例 / 报告：同目录 `test-design.md` / `test-cases.md` / `test-report.md`
> 测试日期：2026-08-29（执行）/ 2026-08-30（归档本批次时补写复盘）

---

## 1. 本次测试概览

对 `agent-demo` v0.1（Java 编写的 Claude Code 风格 Agent CLI）做了一次**全面测试**，覆盖 REPL、LLM Provider、AgentLoop、工具层、权限、会话、记忆、上下文压缩、CLI 命令与运行期冒烟。

**核心结论**：项目可构建、可运行、核心链路可用，自动化测试全绿；但存在**覆盖率门禁失守**的质量问题（见 §4）。

---

## 2. 测试流程回顾

1. **设计**：`test-design.md` —— 规划 **196+ 条用例**（14 模块），含评审总览、风险识别、可测性建议、退出标准、待澄清问题与设计答复落地。
2. **用例输出**：`test-cases.md` —— 承载全量详细用例表（TC-PROV / TC-LOOP / TC-COMP / TC-TOOL / TC-PERM / TC-SESS / TC-MEM / TC-REPL / TC-CFG / TC-ERR / TC-INT / TC-COST / TC-E2E / TC-SEC）。
3. **执行**：`mvn clean verify` + 运行期冒烟。
4. **报告**：`test-report.md` —— 137 用例全绿、覆盖率明细、缺陷/风险清单。

---

## 3. 执行结果

| 维度 | 结果 |
|------|------|
| 构建 | ✅ `mvn clean verify` BUILD SUCCESS |
| 自动化测试 | ✅ **34 测试类 / 137 用例**全绿（FAIL=0/ERR=0/SKIP=0） |
| 运行冒烟 | ✅ `chat --input "hi"` 完整走通并返回真实模型回复 |
| 用户反馈「输入就报错」 | ✅ 已确认修复（根因由近期提交 `6cdf3e0`/`a4021b3`/`5ea0103`/`a62bcf6` 修复） |
| 全局覆盖率 | 🟡 LINE **68.3%** / BRANCH **55.4%**（低于目标 80%/70%） |

---

## 4. 核心问题复盘（最重要的发现）

### 🔴 覆盖率门禁失守（最高优先级缺陷）

`pom.xml` 的 jacoco `check` 门禁 `includes` 包含 `com.example.agent.agent.*`，但该包在重构 `f04306d` 中已被改名 `core`，**实测命中 0 个类**。导致：

1. 门禁只统计 `provider/tools/permission/session/memory` 这几个包，**`core`（AgentLoop/Message/ContextCompressor 等最核心、最易出 bug 的部分）与 `llm`/`cli`/`log` 完全不受覆盖率约束**。
2. 全局 LINE 68.3% 未达标，但 `mvn verify` 却输出 "All coverage checks have been met" —— **假阴性**。

**复盘经验**：覆盖率门禁的 `includes` 必须随代码包结构调整而更新，否则门禁失去质量闸门作用，掩盖真实低覆盖。

### 🟡 测试落地率与设计差距

设计 196+ 用例，实际落地 137（约 68%）。安全专项（TC-SEC）、中断/编码（TC-INT）、成本控制（TC-COST）**零覆盖**，E2E 只有 #1 #2。

**复盘经验**：设计用例与落地存在差距，应排优先级逐步补齐，尤其安全/中断/成本这类设计标注为 P0 的高价值场景。

---

## 5. 做得好的地方

1. **测试驱动发现缺陷**：E2E 与冒烟定位了「输入就报错」的真实缺陷，并追溯到根因提交。
2. **覆盖率量化透明**：通过 jacoco.csv 给出各包真实覆盖率，而非只看门禁结果，从而发现门禁失守。
3. **问题定位到根因**：对覆盖率门禁失守、编译问题等都追到具体原因，而非症状绕过。

---

## 6. 可改进/后续建议

1. **修复 jacoco 门禁**：更新 `includes` 为现有包（`core`/`llm`/`cli`/`log`/`config`/`prompt`/`render` 等），并按真实覆盖率重设阈值。
2. **补低覆盖模块**：`tools.file`（LINE 46.7%）、`cli`（28.3%）、`provider.minimax`（0%）。
3. **补齐设计用例缺口**：优先 TC-SEC（安全）、TC-INT（中断/编码）、TC-COST（成本）、TC-E2E（#3-#14）。
4. **统一测试数口径**：architecture.md 写 94、spec 写 92，与实际 137/91 不一致，需统一。

---

## 7. 交付物清单

| 交付物 | 路径 |
|--------|------|
| 测试设计 | `test-design.md`（本批次） |
| 用例输出 | `test-cases.md`（本批次） |
| 测试报告 | `test-report.md`（本批次） |
| 本复盘 | `test-review.md`（本批次） |

---

> 复盘日期：2026-08-30
> 说明：本批次最初仅产出 test-design + test-report，`test-cases.md` / `test-review.md` 在本批次归档时按「测试文档四件套」规范补齐。

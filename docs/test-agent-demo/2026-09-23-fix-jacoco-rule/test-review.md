# fix-jacoco-rule 测试复盘

> 批次：`2026-09-23-fix-jacoco-rule`　执行日：2026-09-23
> 关联交付：`test-design.md` / `test-cases.md` / `test-report.md`

---

## 1. 目标与结果对照

| 目标 | 计划判定 | 实际结果 | 达成 |
|------|---------|---------|:----:|
| G1 证明抬升实验有区分力 | 对修复前的规则做实验，应仍成功 | BUILD SUCCESS（对照组成立） | 是 |
| G2 证明规则会拦 | agent-core / agent-web 抬升后应失败 | 两者都 BUILD FAILURE | 是 |
| G3 规则覆盖面符合预期 | 0.99 违规清单 = 独立复算清单 | 一致，11 个包 | 是 |
| G4 被漏掉的包补到达标 | 逐包 ≥ 0.80 / 0.70 | 11 个包全达标；两模块 met | 是 |
| G5 无回退 | 全量门禁全绿 | agent-core 658/0 + agent-web 399/0 + 前端 361 全过 + tsc 2 | 是 |
| — | 附带 | 发现 2 处未修缺陷（D-03/D-04） | 已记录 |

---

## 2. 流程回顾

```mermaid
flowchart LR
    A["explore<br/>用户三选：PACKAGE / 逐包补测 / 进 spec"] --> B["propose<br/>proposal + design<br/>+ delta spec + tasks"]
    B --> C["apply T1<br/>改 pom + 抬升实验"]
    C --> D["发现子包漏检<br/>回头拓宽 includes"]
    D --> E["apply T2<br/>逐包补测 7 个包"]
    E --> F["T3 spec + AGENTS.md"]
    F --> G["archive + 合并 + 复验"]
```

### 2.1 关键转折：实施中途发现「修了一半」

设计阶段（design.md D1）写的是「`<element>PACKAGE</element>` + 5 个精确包名」。
T1 照做后抬升实验**确实失败了**——按当时的判据，规则已经修好。若就此收工，会交付一条**仍然会漏检**的门禁。

转折点是做了 RG-04（违规清单 vs 独立复算 CSV）：发现 `All coverage checks have been met` 与
「CSV 里 `tools.file` BRANCH 只有 0.50」这两件事同时成立 —— 矛盾，才定位到 `X` 与 `X.*` 的匹配差异。

> **教训**：验「规则会不会拦」和验「规则覆盖了哪些对象」是两件事，前者通过不代表后者正确。
> 只做抬升实验会把 D-02 漏掉。

### 2.2 数据驱动补测

T2 完全按 CSV 数据排序推进，每补一个包就跑门禁复算：

| 顺序 | 包 | 补前 | 补后 | 新增用例 |
|:----:|----|------|------|:--------:|
| 1 | `session` | 0.77/0.64 | 0.80/0.71 | 26 |
| 2 | `tools` | 0.78/0.50 | 0.89/0.88 | 21 |
| 3 | `tools.websearch` | 0.98/0.68 | 0.98/0.71 | 4 |
| 4 | `provider.minimax` | 0.53/1.00 | 1.00/1.00 | 5 |
| 5 | `provider.anthropic` | 0.75/0.68 | 0.97/0.77 | 9 |
| 6 | `tools.file` | 0.62/0.50 | 0.82/0.95 | 4 |
| 7 | `tools.shell` | 0.68/0.65 | 0.84/0.75 | 5 |

合计 74 例。没有预先承诺「补多少个」，而是补到越过阈值即停。

---

## 3. 做得好的

| 项 | 说明 |
|----|------|
| 判据选得对 | 用「阈值抬升后构建是否失败」验规则生效，而不是去读 jacoco 内部语义——判据对实现细节免疫 |
| 设了对照组 | 只对修好的规则做实验，排除不了「实验恒真」；对修复前的规则也做一次，两次结果不同才有说服力 |
| 不只看「会不会拦」 | 额外比对「考核了哪些包」，这一步抓出了 D-02（差点交付一条半好门禁） |
| 补测优先补契约面 | 文件工具补的是 `inputSchema`/`checkPermissions`/`renderUse`，而不是无脑补 getter——这些被误改才是真风险 |
| 拒绝凑数测试 | 写 `FileToolsProtocolTest` 时写下过一条无意义的 `Path` 断言，发现后删掉了；`blankTavilyKeyFallsBackToEnv` 只断言 provider 类型而不断言 key 值（避免环境相关的脆弱测试） |
| Scope 自律 | 发现 D-03（`AnthropicProvider` 真缺陷）后**没有顺手修**——修它属于 provider 行为变更；改用 characterization 用例固化并在 javadoc 写明未来如何替换 |
| 零生产代码改动 | 本批只加 pom 与测试，`src/main` 一行未改，diff 可验证；这让评审只需看「测试是否有效」而不必担心行为回归 |
| 踩坑写进文档 | 首次抬升实验的假结果（`jacoco.exec` 未生成）、`git checkout` 误还原未提交改动，都写进了报告与复盘 |

---

## 4. 可改进的

| 项 | 问题 | 下次怎么做 |
|----|------|-----------|
| 设计阶段结论未验证就写进 design | D1 写的「5 个精确包名」在实施中被推翻，design 需要回改 | design 里涉及「配置语义」的结论应标注为「待实测确认」，并在 apply 阶段回填 |
| T1 的判据不完整 | 只验「会拦」，没验「拦了哪些」 | 把「违规清单 vs 独立复算」写进 specs 的验收场景（本次已写进 `覆盖率门禁` 的 Scenario） |
| 补测顺序有个反复 | 先补 `session`/`tools`（缺口大但补起来费事），而 `websearch` 只差 2 分支——若先做它能更快看到「接近全绿」的正反馈 | 按「缺口/工作量」比排序而不是只按缺口绝对值 |
| 平台相关分支回避 | `tools.shell` 的 `taskkill` / `descendants` 路径没覆盖 | 若要覆盖需引入可注入的 `os.name` 判定，属工具本身的可测性改造，可另开小 change |
| 补测与门禁跑动次数偏多 | 每个包补完都跑一次完整门禁（约 1-2 分钟），累计多次 | 可先只跑 `jacoco:report` 看数值，达标后再跑完整 verify |

---

## 5. 踩坑记录

| 坑 | 现象 | 原因 | 处置 |
|----|------|------|------|
| 抬升实验假阴性 | 明明已把阈值改成 0.99，构建仍 SUCCESS | `jacoco.exec` 不存在时 check 无数据可判，直接跳过 | 先跑 `mvn test` 生成 exec 再做实验；已写进报告 §3.1 |
| `git checkout` 吃掉未提交改动 | 做完实验想「还原 pom」，一执行 `git checkout -- agent-core/pom.xml`，**连先前未提交的 includes 改写一起被还原**了，下一轮实测才发现规则「没生效」 | `git checkout` 恢复的是 index 版本，而改动尚未 `git add` | 改为「实验前 `Get-Content -Raw` 存原文，实验后用原文写回」，不再用 `git checkout` 还原 |
| 测试红得莫名其妙 | 新增 4 个 Anthropic `streamChat` 用例全红，chunk 列表为空 | 不是测试写错，是 D-03 真缺陷（SSE reader 剥前缀） | 控制变量实验确认机制 → 改为 characterization 用例 + 正路径用 `text/plain` |
| `PowerShell` 解析错误 | `"exec 存在？" Test-Path ...` 报 `Unexpected token` | 中文问号后直接跟 cmdlet，被当成表达式 | 拆成独立语句 |

---

## 6. 交付物清单

| 类型 | 路径 |
|------|------|
| 构建配置 | `agent-core/pom.xml`（jacoco 规则：BUNDLE → PACKAGE；includes 根包 + 子包成对；`<excludes>` 排除 `AgentCli`） |
| 测试（新增） | `agent-core/src/test/java/com/example/agent/provider/minimax/MiniMaxProviderTest.java`（5） |
| 测试（新增） | `agent-core/src/test/java/com/example/agent/provider/anthropic/AnthropicProviderStreamChatTest.java`（9） |
| 测试（新增） | `agent-core/src/test/java/com/example/agent/session/SessionEntryTest.java`（8） |
| 测试（新增） | `agent-core/src/test/java/com/example/agent/session/SessionResumeLoaderParsingTest.java`（18） |
| 测试（新增） | `agent-core/src/test/java/com/example/agent/tools/PathGuardTest.java`（4） |
| 测试（新增） | `agent-core/src/test/java/com/example/agent/tools/file/FileToolsProtocolTest.java`（4） |
| 测试（扩充） | `ToolRegistryTest`（+5）、`WebSearchToolTest`（+12）、`ShellToolTest`（+5）、`WebSearchProviderFactoryTest`（+4） |
| Spec | `openspec/specs/testability/spec.md`（+1 新需求 `覆盖率门禁`） |
| 归档 | `openspec/changes/archive/2026-09-23-fix-jacoco-rule/` |
| 项目规则 | `AGENTS.md` v0.1.8（§2.5.3 的 jacoco 一行改为指向 spec，并写明 includes 成对规则） |
| 文档 | `docs/test-agent-demo/2026-09-23-fix-jacoco-rule/`（本四件套） |

---

## 7. 后续建议

| 优先级 | 建议 | 理由 |
|:------:|------|------|
| P0 | 单开 change 修 D-03：`AnthropicProvider.streamChat` 的 SSE 解析（`bodyToFlux(ServerSentEvent.class)` 取 `data()`，或让 `parseSseLine` 兼容已剥前缀的载荷） | 该 provider 走真实 API 时**完全不可用**；修完需按 `AnthropicProviderStreamChatTest` 的 javadoc 替换 characterization 用例 |
| P1 | 修 D-04：`agent-web` 的 includes 补上根包 `com.example.agent.web` | 同类机制的第二个实例；改动一行，但会暴露 `web` 根包的真实覆盖缺口，需一并补测 |
| P1 | 统一两个模块的 jacoco 配置形态 | 目前一个用「根包 + 子包成对」、一个只有 `.*`，容易再次漂移；建议抽到父 pom 的 pluginManagement 统一管理 |
| P2 | 给「覆盖率门禁的有效性」加自动化看护 | 目前靠人工做抬升实验；可在 CI 里加一条「故意把阈值改为 1.0 并断言构建失败」的守护作业（成本高，列为可选） |
| P2 | 评估 `tools.shell` 平台分支的可测性改造 | `os.name` 判定目前写死在 `killTree` 里，无法注入 |

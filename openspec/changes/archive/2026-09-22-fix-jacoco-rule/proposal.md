## Why

`mvn verify` 的 jacoco 门禁在 `agent-core` 上是**真空通过的**——snip-pairing-repair 修复的真实会话 400 调查（`docs/test-agent-demo/2026-09-22-snip-pairing-repair/test-report.md §6`）过程中发现的遗留风险。

根因：

```text
agent-core/pom.xml 的 jacoco 规则：
  <rule>
    <element>BUNDLE</element>
    <includes>
      <include>com.example.agent.provider.*</include>
      ... (6 个 com.example.agent.*.* 包名模式)
    </includes>
    <limits>LINE ≥ 0.80 / BRANCH ≥ 0.70</limits>
  </rule>
```

`BUNDLE` 元素下 `includes` 过滤的是 **bundle 名**（`agent-core`）而非类名——6 个包名模式全部匹配不到，0 个考核对象，"All coverage checks have been met" 是空话。

决定性证据（`test-report.md §6.2`）：把 BRANCH 阈值从 `0.70` 临时改成 `0.99` 后 `jacoco:check` 仍报 met + BUILD SUCCESS；同手法在 agent-web（用 `<element>PACKAGE</element>`）得到 9 条 `Rule violated` + BUILD FAILURE。

这条规则在 2026-08-29 首次测试时就已记录（`docs/test-agent-demo/test-guide.md §2.1`：当时归因为「引用了已废弃包 `com.example.agent.agent.*` 导致门禁失守」），但归因偏窄、长期未修。

`agent-core` 当前实测水平（按 6 包聚合）：LINE 0.8023 ≥ 0.80 但 BRANCH 0.6601 < 0.70——一旦规则真的生效，当前构建就会失败。

## What Changes

- **改 pom**：把 `agent-core/pom.xml` 的 jacoco 规则改为 `<element>PACKAGE</element>`；为 `main` 入口类（`AgentCli`）加 `<excludes>`，对齐 `agent-web` 的做法。
- **includes 覆盖根包与子包**：`PACKAGE` 元素下 `includes` 过滤的是**包名**，`X` 只中根包、`X.*` 只中子包，故两者成对写出，覆盖 `provider` / `tools` / `permission` / `session` / `memory` 及其全部子包。（这一条是实施中发现的第二个同类陷阱：只写根包名时子包的缺口同样静默漏检。）
- **逐包补测**：includes 拓宽后暴露 7 个包不达标（`tools.file`、`provider.minimax`、`provider.anthropic`、`tools.shell`、`tools.websearch`、`session`、`tools`），逐包补齐到 LINE ≥ 0.80 / BRANCH ≥ 0.70。
- **写进 spec**：在 `openspec/specs/testability/spec.md` 新增《覆盖率门禁》Requirement，把阈值、考核方式（含「根包 + 子包」「阈值抬升实验验证生效」）与 `excludes` 约定正式落到规格层；`AGENTS.md §2.5.3` 的「jacoco 强制门禁」指向该 spec。
- **附带发现（不修）**：实施中发现 `AnthropicProvider.streamChat` 在真实 `text/event-stream` 下零产出（Spring SSE reader 剥 `data:` 前缀 ↔ `parseSseLine` 要求前缀），已在 change 内记录并用 characterization 用例固化，建议单开 change 修。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `testability`: 新增 Requirement《覆盖率门禁》——将 `LINE ≥ 0.80 / BRANCH ≥ 0.70` 与按 `PACKAGE` 逐包考核、`main` 入口类的 exclude 约定落到规格。

## Impact

| 项 | 内容 |
|----|------|
| 代码 | `agent-core/pom.xml`（规则改写）、`agent-core/src/test/java/...`（补 ~10-30 个测试用例，具体数量看数据） |
| 文档 | `AGENTS.md §2.5.3` 加一行指向新 spec（避免一处改两处漂移）；`test-guide.md` 加一条记录 |
| API / 行为 | 无 |
| 风险 | 改 pom 后 `mvn verify` 会暴露真实缺口，本次把缺口一次性补齐；不下调阈值 |
| 不做 | 不下调阈值（项目规格层维持 80/70）；不改动 `agent-web` 的规则；不补 `agent-web` 的覆盖率缺口（如果有） |

# fix-jacoco-rule 测试用例

> 来源：`test-design.md` §7 用例矩阵的展开。本文件是可独立追溯的全量用例表。
> 执行日：2026-09-23　分支：`fix/jacoco-rule`

本批新增 **74** 个测试用例（10 个测试类新增或扩充），分两类：
**规则有效性用例**（RG-*，手工执行的实验，非自动化）与**覆盖率补齐用例**（CV-*，JUnit 自动化）。

---

## 1. L1/L2：规则有效性与覆盖面（RG，手工实验）

这四条不是 JUnit 用例，而是**用手工实验验证 pom 配置**——因为要验的是「门禁会不会拦」，而这只能在构建层面观测。

### RG-01 修复前 BUNDLE 规则的抬升实验（对照组，P0）

| 项 | 内容 |
|----|------|
| 前置 | `agent-core/pom.xml` 为**修复前**状态（`<element>BUNDLE</element>` + 6 个类名通配 includes） |
| 步骤 | 把 BRANCH 阈值由 `0.70` 临时改为 `0.99`，跑 `mvn -o -pl agent-core jacoco:check@check-coverage` |
| 预期 | **BUILD SUCCESS**（真空规则对阈值无感）；同时 `Analyzed bundle 'agent-core' with N classes` 仍会打印 |
| 为什么值得测 | 这是**对照组**：若没有它，「修好后的规则会失败」就无法排除「实验恒真」这一解释 |
| 实测 | BUILD SUCCESS（见 `test-report.md` §3.1） |

### RG-02 修复后 agent-core 规则的抬升实验（P0）

| 项 | 内容 |
|----|------|
| 前置 | 规则已改为 `<element>PACKAGE</element>` + 根包/子包成对的包名 includes |
| 步骤 | 同上（阈值 → `0.99`） |
| 预期 | **BUILD FAILURE**，且输出 `Rule violated for package ...` 多条 |
| 为什么值得测 | 唯一能证明「这条规则真的在考核」的判据；与 RG-01 形成对照 |
| 实测 | BUILD FAILURE，初始 4 个包违规；拓宽 includes 后 11 个包中若干违规（见 §3.2） |

### RG-03 agent-web 规则的抬升实验（对照，P0）

| 项 | 内容 |
|----|------|
| 前置 | `agent-web/pom.xml` 规则（`<element>PACKAGE</element>` + `com.example.agent.web.*`） |
| 步骤 | 把 LINE 阈值由 `0.80` 临时改为 `0.99`，跑 `mvn -o -pl agent-web jacoco:check@check-coverage` |
| 预期 | **BUILD FAILURE** + 多条 `Rule violated for package` |
| 为什么值得测 | 证明「点号通配在 PACKAGE 元素下确实能匹配包名」——排除「RG-02 的失败其实源于别的机制」 |
| 实测 | BUILD FAILURE，9 条违规（`web.wecom` / `web.config` / `web.security` / `web.stream` / `web.api` / `web.session` / `web.api.voice` / `web.api.dto` / `web.api.catalog`） |

### RG-04 违规清单覆盖全部预期包（L2，P0）

| 项 | 内容 |
|----|------|
| 前置 | agent-core 规则已拓宽到「根包 + 子包」 |
| 步骤 | 阈值全设 `0.99` 跑 check；把违规清单里的包名去重；与 `jacoco.csv` 里独立聚合出的包清单比对 |
| 预期 | 两边一致，且包含 `provider` / `tools` / `permission` / `session` / `memory` 5 个根包 与 `provider.anthropic` / `provider.deepseek` / `provider.minimax` / `provider.openai` / `tools.file` / `tools.shell` / `tools.websearch` 7 个子包 |
| 为什么值得测 | **这一步是发现「子包漏检」的关键**：只看「是否失败」会以为门禁已经好了 |
| 实测 | 一致，共 11 个包（`provider` 根包 1.00/1.00 连 0.99 都过，故未出现在违规清单里，但已在覆盖集合中） |

---

## 2. L4：覆盖率补齐用例（CV，JUnit）

每条用例的「为什么值得测」写的是**它能发现什么回归**，而不是「它覆盖了哪几行」。

### CV-01 `tools.file`（补前 LINE 0.62 / BRANCH 0.50 → 补后 0.82 / 0.95）

新增 `FileToolsProtocolTest`（4 例）：

| 用例 | 断言 | 为什么值得测 |
|------|------|-------------|
| `readFileProtocolSurface` | name/description/category/isReadOnly、schema 的 `required=[path]`、安全路径→`allow()`、含 `..`→`deny()`、`renderResult` 在 >100 字符时截断成 103 字符（含 `...`）、恰好 100 字符不截断 | 覆盖**权限默认值**与**输出截断边界**：把 `allow()` 误改成 `ask()`、或把截断阈值改错，都是静默的行为变更 |
| `lsProtocolSurface` | 同上，含 `deny()` 分支与 `renderResult` 原样返回 | 同上 |
| `writeFileProtocolSurface` | category=WRITE、`isDestructive=true`、`required=[path,content]`、安全路径→`ask()`、含 `..`→**`deny()`**（守卫优先于 ask） | 锁定「路径守卫优先于 ask」这一安全语义：若把 guard 判断挪到 ask 之后，写工具就能被诱导写到任意路径 |
| `editFileProtocolSurface` | category=WRITE、`required=[path,oldText,newText]`、**一律 `ask()`**（不走 PathGuard）、`renderUse` 的 `oldLen` 在 oldText 为 null 时按 0 计 | 记录 EditFile 与其它三个工具**刻意不同**的权限策略，避免后人「对齐」时改坏 |

### CV-02 `provider.minimax`（补前 LINE 0.53 → 补后 1.00）

新增 `MiniMaxProviderTest`（5 例）：

| 用例 | 断言 | 为什么值得测 |
|------|------|-------------|
| `singleArgConstructorUsesChinaEndpointBaseUrl` | `baseUrl()=="https://api.minimaxi.com"`、`name()=="minimax"` | MiniMax 中国版与海外版域名不同；改错会导致全部请求 404 |
| `twoArgConstructorHonoursCustomBaseUrl` | 自定义 URL 原样返回 | 与 `fix-provider-baseurl` 同源的回归防线：防止再回到「忽略构造器参数、返回硬编码常量」 |
| `fourArgConstructorAcceptsExplicitTimeouts` | 4 参构造可正常构造 | 超时构造器此前完全没被执行过 |
| `chatEndpointDiffersFromStandardOpenAiPath` | `chatEndpoint()=="/v1/text/chatcompletion_v2"` | MiniMax 的路径与 OpenAI 标准 `/v1/chat/completions` **不同**；这条是它最容易踩的坑 |
| `contextWindowAndMaxOutputFollowOfficialDocs` | 128000 / 8192 | 上下文窗口报错会导致压缩阈值算错 |

### CV-03 `provider.anthropic`（补前 LINE 0.75 / BRANCH 0.68 → 补后 0.97 / 0.77）

新增 `AnthropicProviderStreamChatTest`（9 例）：

| 用例 | 断言 | 为什么值得测 |
|------|------|-------------|
| `streamChatParsesThinkingTextAndMessageStop` | 一次流里同时产出 `ThinkingDelta`、`TextDelta`、`Finished` | 覆盖 `streamChat` 全链路（此前完全未执行） |
| `streamChatSkipsBlankEventAndCommentLines` | 空行/`event:` 行/注释行不产出 chunk，只有 `data:` 行产出 1 个 | 防止「每行都 emit」导致的重复渲染 |
| `streamChatIgnoresMalformedJsonPayload` | 坏 JSON 行被吞掉（WARN），后续好行仍产出 | 覆盖 `parseSseLine` 的 catch 分支 |
| `streamChatSendsApiKeyAndAnthropicVersionHeaders` | `x-api-key` 与 `anthropic-version: 2023-06-01` 都发出 | 缺任一 header 上游都拒绝 |
| `streamChatRejectsMismatchedProviderInExtra` | `extra.provider="deepseek"` → `IllegalArgumentException` | fail-closed：绝不能把 Anthropic 请求发给别的 provider |
| `knownDefectSseContentTypeYieldsNoChunks` | `text/event-stream` 下 chunk 列表**为空** | **characterization 用例**，固化已发现的缺陷（见 `test-report.md` §5）；修复后应删除本用例 |
| `twoArgConstructorUsesProvidedBaseUrl` | 自定义 baseUrl 可正常跑通 | 同 CV-02 |
| `fourArgConstructorAcceptsExplicitTimeouts` | 4 参构造可正常跑通 | 超时构造器此前未被执行 |
| `nameContextWindowAndMaxOutputAreStable` | anthropic / 200000 / 8192 | 防误改 |

### CV-04 `tools.shell`（补前 LINE 0.68 / BRANCH 0.65 → 补后 0.84 / 0.75）

`ShellToolTest` 扩充（+5 例，总 8）：

| 用例 | 断言 | 为什么值得测 |
|------|------|-------------|
| `protocolSurface` | name/description/category=SHELL/`isDestructive=true`/`required=[command]`/`checkPermissions()==ask()`/renderUse 含命令原文 | Shell 是最危险的工具，`isDestructive` 或 category 被改错会绕过权限确认 |
| `parseArgumentsReadsCommand` | JSON → `Input(command)` | 基本契约 |
| `parseArgumentsRejectsMalformedJson` | 坏 JSON → `IllegalArgumentException` | 覆盖 catch 分支；防止坏参数被当成空命令执行 |
| `truncatesOutputBeyondMaxOutputBytes` | `maxOutputBytes=20` 时输出含 `[truncated: output exceeded 20 bytes]` | 覆盖 `appendBounded` 的截断分支；输出上限失效会导致内存被一条 `yes` 打爆 |
| `timeoutReportsTimeoutAndKillsProcess` | `timeoutSec=0` → 错误结果含 `[TIMEOUT after 0s]` | 覆盖超时分支与 `killTree`；超时失效会挂死整个 Agent 回合 |

### CV-05 `tools.websearch`（补前 LINE 0.98 / BRANCH 0.68 → 补后 0.98 / 0.71）

`WebSearchProviderFactoryTest` 扩充（+4 例，总 14）：

| 用例 | 断言 | 为什么值得测 |
|------|------|-------------|
| `blankExplicitProviderFallsBackToInference` | `search.provider="   "` → 视同未配置，回落模型推断 | 覆盖 `pickFirstNonBlank` 与显式/推断分支；空白被当成有效配置会让用户「填了但没生效」 |
| `blankDeepseekKeyFallsBackToCfgKey` | 显式 key 为空白 → 回落 `cfg.provider().apiKey()` | 同上（key 优先级链） |
| `blankTavilyKeyFallsBackToEnv` | 显式 tavily key 为空白 → 回落到 env | 同上 |
| `nullTypeAndModelInferTavily` | `provider.type` 与 `model` 均 null → 推断为 tavily | 覆盖 `infer` 的两个 null 分支；此处 NPE 会让工具注册整体失败 |

> 注：`blankTavilyKeyFallsBackToEnv` 只断言「仍构造出 Tavily provider」而不断言具体 key 值——
> env 里是否有 `TAVILY_API_KEY` 取决于运行机器，断言具体值会变成环境相关的脆弱测试。

### CV-06 `session`（补前 LINE 0.77 / BRANCH 0.64 → 补后 0.80 / 0.71）

新增 `SessionEntryTest`（8 例）与 `SessionResumeLoaderParsingTest`（18 例）：

| 用例组 | 断言 | 为什么值得测 |
|--------|------|-------------|
| `SessionEntryTest` 工厂方法（6 例） | 4 个工厂在 **parent 非空**时正确序列化 `parentUuid`；parent 为 null 时为 null；meta 无 parent 且携带 key/value | 既有测试一律传 `null` parent，于是「有父」这条路径从未执行——而对话树正是靠它构建 |
| `SessionEntryTest` record 语义（2 例） | 值相等的两条 entry `equals` 且 `hashCode` 相同；与非同类对象不等；accessor 暴露全部组件 | record 的隐式 `equals` 参与会话去重/比对 |
| `SessionResumeLoaderParsingTest` 类型分发（4 例） | 空列表 / `system` / 未知类型跳过 / extras 为 null | 存档里出现未知 type 时必须跳过而不是崩 |
| 私有辅助容错（7 例） | `intOf` 吃 Number / 数字字符串 / 非数字字符串（catch）/ null；`boolVal` 吃 Boolean / 字符串 / null；`str` 吃 null | 这些分支只在**存档字段类型不符预期**时才走到——即「旧版本写出的存档」或「手工改过的存档」，正是恢复功能存在的理由 |
| `parseToolCalls`（3 例） | toolCalls 不是 List → 空列表；数组里有非 Map 元素 → 跳过；字段为 null → 空串 | 同上；解析失败会让整段历史丢失 tool_calls |
| `loadArchivedById`（1 例） | 归档目录缺失 → 空结果；有归档 → 正确读出 | 归档视图的入口 |
| `snip` 边界（2 例） | `maxTokens <= 0` 原样返回；空列表原样返回 | 与 `snip-pairing-repair` 的裁剪逻辑相邻，锁定「不裁剪」的边界 |

### CV-07 `tools`（补前 LINE 0.78 / BRANCH 0.50 → 补后 0.89 / 0.88）

| 测试类 | 用例 | 断言 | 为什么值得测 |
|--------|------|------|-------------|
| `PathGuardTest`（新建 4 例） | 安全路径→null、`../x`→deny、`a/../b`→deny、null→deny | 这是各文件工具共用的**唯一越界防线**；`a/../b` 这种中间态最容易被漏（只查前缀 `../` 的实现会放过它） |
| `ToolRegistryTest`（+5 例） | `get`/`getRaw` 查不到返回 null；`registerSkillTools(null)` 不炸；注册 Skill 后工具名=skill 名；`registerMcpTools(null)` 不炸；MCP 握手失败时不注册 | 三个 deprecated 静态方法的 null/失败分支；它们仍是 CLI 向后兼容入口 |
| `WebSearchToolTest`（+12 例） | `renderUse` 的 null 输入/null query；`renderResult` 原样返回；坏 JSON 抛指定消息；`maxResults` 非数字视为未提供；`execute(null)` 报错；显式 maxResults>0 覆盖默认值；maxResults<=0 回退默认值；空/ null sources → 占位文案；标题空白 → 用 URL 当标题；snippet/date 空白 → 不输出该行；truncated → 追加标记；输出无尾随空白 | `renderText` 是**给模型看的最终文本**：标题/摘要/日期的空值处理逻辑错一处，模型就会看到 `null` 或空行；`maxResults` 回退错会让用户配置失效 |

---

## 3. L5：门禁用例

| 编号 | 命令 | 通过判据 | 实测 |
|:----:|------|---------|------|
| GATE-01 | `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` | BUILD SUCCESS；两模块 `Failures: 0, Errors: 0`；两模块都出现 `All coverage checks have been met` | agent-core 658/0 + agent-web 399/0，两处 met，SUCCESS |
| GATE-02 | `npx vitest run`（`agent-web/frontend`） | `Test Files` 与 `Tests` 全过 | 43 files / 361 tests 全过 |
| GATE-03 | `npx tsc --noEmit` | `error TS` 行数 ≤ 基线 7 | 2 |

> GATE-02/03 在主工作区执行：本 change **零前端改动**（`git diff origin/main...HEAD -- agent-web/frontend` 为空），
> 因此分支上的前端源码与主工作区逐字节相同，跑在主工作区等价。

---

## 4. 未落地 / 未覆盖项

| 项 | 原因 | 影响 |
|----|------|------|
| jacoco 规则「阈值读取」本身的单测 | jacoco 是构建期插件，没有可注入的测试接缝 | 用抬升实验代替（RG-01~03），判据是构建结果 |
| `agent-web` `includes` 漏根包的修复 | 同类问题但属另一个模块，避免 scope 蔓延 | 已记入缺陷清单 D-04 与后续建议 |
| `AnthropicProvider` SSE 缺陷的修复验证 | 修复不在本 change 范围 | 已用 characterization 用例固化现状；修复后需替换该用例并把正路径改回 `text/event-stream` |
| `tools.shell` 的平台特定分支（`taskkill` 路径 / Unix `descendants`） | `killProcessTree=true` 的进程树回收在 CI 上行为不稳定 | 已覆盖 `killProcessTree=false` 分支；平台分支留待专项 |
| 覆盖率「质量」的自动校验 | 门禁只校验阈值，无法识别空测试 | 本批靠人工自查（DoD §5），未引入变异测试等重型手段 |

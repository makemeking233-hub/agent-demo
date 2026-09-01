# agent-demo

> Java 编写的 Claude Code 风格 Agent CLI + Web UI，第一阶段 v0.1（CLI REPL），已扩展到 v0.3+（CLI + Web + OpenSpec 迭代 + 可观测性）。

- 设计：`docs/design/design.md`（技术设计 1288 行）
- 测试设计：`docs/test-agent-demo/test-design.md`
- 测试报告：`docs/test-agent-demo/test-report.md`
- 架构详解：`docs/guides/architecture.md`（11 张 Mermaid 图）
- 实施计划：`docs/superpowers/plans/2026-08-26-agent-cli-v0.1.md`
- 迭代流程：**OpenSpec**（`openspec/`，见 AGENTS.md §2.5）—— 默认四阶段（explore → propose → apply → archive）

---

## 1. 项目定位

在终端里与 LLM 协作：流式对话、调用本地工具（读文件、执行命令）、多步自主完成任务、会话持久化到本地。也可通过 Web UI（agent-web 模块）获得 DeepSeek Harness 风格的三栏交互界面。

| 维度 | 设计取向 |
|------|----------|
| 集成方式 | 独立调 LLM API（不依赖 dsh / Claude Code 进程） |
| 目标用户 | 习惯终端 + Web 的开发者；要求可控、可观测、可测试 |
| 模型支持 | 多 Provider：DeepSeek（默认）/ OpenAI 兼容 / MiniMax（中国版 OpenAI 兼容） |
| 能力范围 | 流式对话 / 工具调用 / 权限确认 / 会话持久化 / Memory / Web UI / 可观测性 / 日志脱敏 |
| 迭代 | OpenSpec 四阶段，默认所有功能改动走 explore → propose → apply → archive |

> 与 Claude Code 的关系：本项目独立实现，借鉴其成熟的工程模式（Tool 协议对象、append-only JSONL 会话、compact 熔断、`MEMORY.md` 索引）。**不依赖 Claude Code 运行时**，不调用其 API。

---

## 2. 核心特性

### 2.1 CLI REPL

- ✅ REPL 交互：连续多轮对话、流式输出（边生成边打印）
- ✅ 工具调用：`ReadFile` / `WriteFile` / `EditFile` / `Ls` / `Shell`，自动执行并回流结果
- ✅ 权限确认：写文件与命令执行需要用户交互确认（默认 allow-read, ask-write）
- ✅ 会话持久化：JSONL append-only 格式保存到 `~/.agent-demo/sessions/`（`/resume` 可加载最近会话）
- ✅ Slash 命令：`/help` `/clear` `/quit` `/history` `/resume` `/model`
- ✅ Memory 记忆：长期记忆写入 `~/.agent-demo/memory/`，下次会话按相关度自动召回
- ✅ 上下文压缩：128K 上限前自动触发 compact，失败熔断防止死循环
- ✅ 错误重试：网络 / 5xx / 429 自动重试；401 / 404 / 限流 / 网络错显示友好提示并继续 REPL（不退出进程）
- ✅ Ctrl+C 中断：第一次优雅取消当前生成、第二次（500ms 内）强制退出
- ✅ 跨平台：Windows / Linux / macOS；中文编码三重防御（GBK↔UTF-8 回退）
- ✅ 成本可见：每轮 token 累计，`/history` 显示估算费用，达到阈值告警 / 停止

### 2.2 Web UI（v0.1 增量，agent-web 模块）

- ✅ React 18 + Vite 6 前端（DeepSeek Harness 风格三栏布局）
- ✅ Server-Sent Events 流式输出（7 种事件：`message_start` / `message_delta` / `tool_call_start` / `tool_call_end` / `permission_request` / `message_stop` / `error`）
- ✅ 后端 `agent-web` 独立 Spring Boot 应用（端口 18080）

---

## 3. 技术栈

| 类别 | 选型 | 版本 | 理由 |
|------|------|------|------|
| JDK | OpenJDK | 17 | |
| 框架 | Spring Boot | 3.2.5 | |
| HTTP | Spring WebFlux `WebClient` | 6.1.x | 原生支持 SSE |
| CLI | picocli | 4.7.6 | |
| 终端 | JLine3 | 3.25.1 | raw mode + 历史 |
| Token | JTokkit CL100K_BASE | 0.6.1 | |
| 构建 | Maven | 3.9 | |
| 测试 | JUnit 5 + Mockito + WireMock + Reactor Test | — | |
| 日志 | SLF4J + Logback + 自研 Redactor | — | 敏感字段脱敏（T3） |
| JSON | Jackson + jackson-dataformat-yaml | — | |
| 前端 | React 18 + Vite 6 | — | agent-web/frontend |
| 状态（已实现） | ✅ v0.1 CLI 全交付；v0.2+ /model /resume；v0.3 web + observability + testability；v1.0 Plugin 框架 + MCP/Skills/Memory + Worktree | — | 190+ commits |

> **不引入 Lombok、spring-boot-starter-web（agent-web 用 webflux）、数据库**——CLI 端用 JSON 文件存会话足够，agent-web 端用文件 + 内存。

---

## 4. 项目结构（多 module）

```text
agent-demo/
├── pom.xml                     # 多 module 聚合（agent-core + agent-web）
├── agent-core/                 # 核心域 + CLI 入口
│   ├── pom.xml                 # finalName=agent-cli；exec classifier 打可执行 fat jar
│   ├── src/main/java/com/example/agent/
│   │   ├── AgentCli.java       # picocli 路由 + Spring Boot 启动
│   │   ├── AgentLoop.java      # 主循环（maxToolIterations / setModel / setHistory / abort）
│   │   ├── MessageHistory.java # 消息列表 + token 估算 + 压缩熔断 + Post-Compact
│   │   ├── ContextCompressor.java # summary + 坍缩 + PTL fallback
│   │   ├── core/exception/     # MaxIterationsExceeded / CompactCircuitBroken
│   │   ├── cli/                # ChatCommand + SlashCommand + InitCommand + Completion
│   │   ├── llm/                # LlmProvider / StreamChunk / ChatRequest / LlmRetry / TokenEstimator
│   │   ├── provider/           # deepseek / openai / minimax
│   │   ├── tools/              # Tool + ToolRegistry + AbstractFileTool
│   │   │   ├── file/           # ReadFile / WriteFile / EditFile / Ls / ToolInput
│   │   │   └── shell/          # ShellTool + Adapters + DenylistMatcher
│   │   ├── permission/         # PermissionManager + PermissionPathMatcher + PermissionDecision
│   │   ├── memory/             # MemoryDir + MemoryIndex + MemoryRecall + MemoryPromptBuilder
│   │   ├── session/            # SessionStore + Session + SessionEntry
│   │   ├── config/             # ConfigLoader + AgentConfig + EnvKeys
│   │   ├── render/             # StreamingPrinter
│   │   ├── prompt/             # SystemPromptBuilder
│   │   ├── signal/             # AbortSignal
│   │   ├── util/               # PromptLoader
│   │   ├── log/                # 可观测性：Redactor / SessionRetentionCleaner / SessionLogger / SessionReplay ...
│   │   └── plugin/             # Plugin 框架：Plugin / PluginContext / PluginManager / ExtensionPoints + {mcp,skill,memory}
├── agent-web/                  # Web UI（独立 Spring Boot 应用，端口 18080）
│   ├── pom.xml                 # finalName=agent-web；frontend-maven-plugin 打包
│   ├── src/main/java/          # WebApplication + WebController + SSE + LogController
│   ├── src/main/resources/     # application-web.yml（web profile）
│   └── frontend/               # React 18 + Vite 6（三栏 UI + SSE）
├── docs/                       # 设计 / 测试 / 架构文档
├── openspec/                   # 迭代流程（change / specs / config.yaml）
├── bin/                        # launcher 脚本（agent.sh / agent.bat）
└── AGENTS.md                   # 项目级规则（含 OpenSpec 流程 §2.5）
```

> 多 module 拆分：`agent-core`（核心域 + CLI）/ `agent-web`（Web 入口）。`agent-core` 内部分为 `core / cli / llm / provider / tools / permission / memory / session / config / render / prompt / signal / util / log / plugin` 等包。

---

## 5. 快速开始

### 5.1 构建

```bash
mvn clean install
# 产物：
#   agent-core/target/agent-cli-exec.jar  （CLI 可执行 fat jar，~15 MB）
#   agent-web/target/agent-web.jar        （Web fat jar，含前端 dist）
```

### 5.2 配置 API key

三层优先级（详见 `docs/design/design.md` §9）：

1. CLI flag：`--api-key sk-...`
2. 环境变量：`DEEPSEEK_API_KEY` / `OPENAI_API_KEY` / `MINIMAX_API_KEY`
3. `~/.agent-demo/config.yaml`（`agent-demo init` 生成）
4. `application-local.yml`（gitignored，本地密钥）

LLM Provider 通过 `--provider deepseek|openai|minimax` 选择，默认 `deepseek`。

#### 5.2.1 快速配置（PowerShell）

```powershell
$env:DEEPSEEK_API_KEY = "sk-your-key-here"
java -jar agent-cli/target/agent-cli.jar chat
```

#### 5.2.2 错误处理

401 / 429 / 网络错误 → 打印友好提示 + 继续 REPL 等待输入（不退出进程）。`/clear` 清空历史后可重试。

### 5.3 启动 CLI REPL

```bash
# 类 Unix
java -jar agent-core/target/agent-cli-exec.jar chat
# 或 launcher（自动设置 UTF-8）
./bin/agent chat
# Windows CMD
bin\agent.bat chat
```

### 5.4 启动 Web UI

```bash
# 终端 A：后端（默认绑 127.0.0.1:18080）
mvn -pl agent-web spring-boot:run -Dspring-boot.run.profiles=web
# 或独立 jar
mvn -pl agent-web clean package
java -jar agent-web/target/agent-web.jar

# 终端 B：前端（开发模式）
cd agent-web/frontend
npm run dev    # http://localhost:5173
```

生产一体化构建（前端 dist 嵌入 jar）：`mvn -pl agent-web clean package` 后直接 `java -jar agent-web/target/agent-web.jar`。

---

## 6. REPL 命令

| 命令 | 行为 | 输出示例 |
|------|------|----------|
| `/help` | 列出可用命令 | `可用命令: /help /clear /quit /history /resume /model` |
| `/clear` | 清空当前会话历史（保留 session 文件） | `[已清空会话历史]` |
| `/quit` | 退出 REPL（exit code 0） | （无输出） |
| `/history` | 显示累计 token + 估算费用 | `消息数: 12 \| 累计 token: 345 in / 678 out \| 估算费用: ¥0.0061` |
| `/resume` | 从 `~/.agent-demo/sessions/` 加载最近 session（按 mtime），整体替换当前 history | `[/resume] 已恢复 N 条消息` / `[/resume] 当前无可恢复会话` |
| `/model` | 列出当前 model + 支持的 model 列表（无参数） | `当前 model: deepseek-chat`<br/>`支持: deepseek-chat, deepseek-reasoner` |
| `/model <name>` | 运行时切换 model（下一轮 LLM 调用生效） | `[/model] 切换到 deepseek-reasoner` |
| 其他 `/xxx` | 未知命令 | `[未知命令] 输入 /help 查看可用命令` |

> **/resume 注意**：按文件 mtime 排序选最新（不是文件名），更鲁棒。找不到任何 session 文件 → 静默提示"无历史会话"，不报错。替换（不是合并）history，避免双 session 数据混淆。
>
> **/model 注意**：运行时切换（`AgentLoop.setModel()` 改 volatile 字段），下一轮 LLM 调用生效。

---

## 7. 权限与危险操作

默认策略：

| 操作 | 默认决策 | 提示样式 |
|------|---------|----------|
| 读文件 / 列目录 | allow | 不提示 |
| 写文件 / 编辑文件 | ask | 显示路径与变更预览，`y/n/a`（a = 始终允许本次会话） |
| 执行命令 | ask | 显示完整命令 + 危险等级评估 |

跨平台危险命令黑名单（无论权限策略都会二次确认）：

- 类 Unix：`rm -rf /`、`mkfs`、`dd if=...of=/dev/...`、`chmod -R 777 /`、`shutdown`、`reboot`
- Windows：`format`、`rd /s /q C:\`、`del /f /s /q C:\*`、`diskpart`、`bcdedit`、`reg delete HKLM`

实际匹配语义与完整黑名单见 `docs/design/design.md` §6.6（含归一化 basename + 短参数簇展开 + `PermissionPolicy` 默认）。

---

## 8. Web UI（v0.1+ 增量）

agent-demo 含 React 18 + Vite 6 Web UI，与 CLI 并存。详见 `docs/design/web-ui-design.md`。

启动：

```bash
# 终端 A：后端（web profile）
mvn -pl agent-web spring-boot:run -Dspring-boot.run.profiles=web

# 终端 B：前端
cd agent-web/frontend
npm run dev   # http://localhost:5173
```

打开 http://localhost:5173/。`vite.config.ts` 把 `/api/*` proxy 到 `:18080`。

生产一体化构建（前端 dist 嵌入 jar）：

```bash
mvn -pl agent-web clean package
java -jar agent-web/target/agent-web.jar
```

默认绑 `127.0.0.1:18080`（loopback）。改 `application-web.yml` 暴露到 LAN，或加 `--agent.web.trusted-hosts=192.168.1.0/24`。

SSE 事件流：后端用 7 种事件（`message_start` / `message_delta` / `tool_call_start` / `tool_call_end` / `permission_request` / `message_stop` / `error`）推模型输出 / 工具调用 / 权限请求。完整 schema 见 `docs/design/web-ui-design.md`。

---

## 9. 验证

```bash
mvn test                # 单元/集成测试（107 个）
mvn verify              # 同上 + jacoco 覆盖率门禁（LINE ≥ 80%，BRANCH ≥ 70%）
mvn -pl agent-web verify # agent-web 模块独立验证
```

测试报告：`docs/test-agent-demo/test-report.md`。

---

## 10. 后续阶段（已实现 / 进行中）

| 阶段 | 状态 | 关键交付 |
|------|------|----------|
| **v0.1** | ✅ 已完成 | CLI REPL + 5 工具 + Memory + JSONL + Slash 命令 + 50 个 Task（M0-M10） |
| **v0.2** | ✅ 已完成 | `/resume` 加载最近 session / `/model` 运行时切换 / Session Memory Compaction |
| **v0.3** | ✅ 已完成 | agent-web Web UI + 可观测性（T1-T8 组：日志事件链路 / 脱敏 / 日志保留 / LogController）+ 可测试性（session 回放）+ MiniMax provider |
| **v0.4** | ✅ 已完成 | OpenSpec 迭代流程 + MCP 客户端（add-mcp-client）+ Skills 系统（add-skills-system）+ Worktree 模式（add-worktree-mode）+ Memory 三 scope + 语义召回（sideQuery，add-memory-sidequery） |
| **v1.0** | ✅ 部分 | Plugin 插件框架（add-plugin-system：可插拔 MCP / Skills / Memory）；剩余 Team Memory / 远程同步 / Prompt Cache 复用 计划中 |

详见 `openspec/` 目录的 active changes。

---

## 11. 参与开发

```bash
# 克隆
git clone <repo> agent-demo
cd agent-demo

# IDE 导入（IntelliJ IDEA / VS Code + Extension Pack for Java）

# 本地测试（不需要真实 API key）
mvn test -Dtest=DeepSeekProviderTest   # 用 WebTestClient 模拟 SSE

# 调试 CLI
mvn -pl agent-core spring-boot:run -Dspring-boot.run.arguments="chat --model deepseek-chat"

# 调试 Web
mvn -pl agent-web spring-boot:run -Dspring-boot.run.profiles=web

# 查看完整日志
tail -f ~/.agent-demo/logs/agent.log
```

### 11.1 提交规范

按全局规则 + AGENTS.md §2.2：
- 中文 Conventional Commits 格式（`feat` / `fix` / `docs` / `refactor` / `chore` / `test`）
- commit 即 push（本地 commit 后立即 push 到 origin/main）

### 11.2 OpenSpec 流程

AGENTS.md §2.5 默认所有功能改动走 OpenSpec 四阶段：

| 阶段 | Skill | 产出 |
|------|-------|------|
| 1. 探索 | `openspec-explore` | 设计方向（不进 git） |
| 2. 提案 | `openspec-propose` | `openspec/changes/<id>/{proposal.md, tasks.md, design.md, specs/<cap>/spec.md}` |
| 3. 实施 | `openspec-apply-change` | 按 tasks.md 逐项实现（TDD + commit 即 push） |
| 4. 归档 | `openspec-archive-change` | delta spec 合并到 `openspec/specs/`，change 标记 completed |

> 文档补充 / typo / CI 调整 / 测试用例补全等小改动可直接 commit（§2.5.5 豁免清单）。

---

## 12. 文档索引

| 路径 | 用途 |
|------|------|
| `docs/design/design.md` | 技术设计（1288 行，含 18 章：背景/架构/技术栈/模块结构/数据契约/Agent 主循环/压缩/配置/会话/错误/测试/打包/验收/版本/风险/中断/可观测性） |
| `docs/design/memory-design.md` | Memory 系统设计（写入/索引/召回/注入 4 条链路） |
| `docs/design/logging-design.md` | 可观测性 / 日志脱敏 / 保留策略 |
| `docs/design/web-ui-design.md` | agent-web 三栏布局 + SSE 协议 |
| `docs/test-agent-demo/test-design.md` | 537 行测试设计 |
| `docs/test-agent-demo/test-report.md` | 测试报告（验证清单 + 覆盖率） |
| `docs/guides/architecture.md` | 11 张 Mermaid 图架构详解 |
| `docs/guides/plugins.md` | Plugin 插件系统指南（hello-world + 多扩展点范例 + ChatRequestMapper） |
| `docs/superpowers/plans/2026-08-26-agent-cli-v0.1.md` | v0.1 实施计划（M0-M10） |
| `openspec/` | 当前进行中的 OpenSpec changes |
| `AGENTS.md` | 项目级规则（本文件 §2.5 含 OpenSpec 流程） |

---

> **License**：MIT
> **状态**：v0.1→v0.4 已完成（CLI + Web + OpenSpec + 可观测性 + MCP/Skills/Worktree + Memory 三 scope），v1.0 Plugin 插件框架已落地，Team Memory / 远程同步 / Prompt Cache 复用计划中

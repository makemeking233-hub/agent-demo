# agent-demo 项目级规则

> 本文件为本项目（agent-demo）的 Agent 工作规则，仅在本项目工作目录内生效。
>
> 与全局规则（`~/.dsh/AGENTS.md`）冲突时，本文件优先。

---

## 1. 项目定位

Java 编写的 Claude Code 风格 Agent CLI，第一阶段独立调 DeepSeek API，支持流式对话、工具调用、权限确认、会话持久化、长期记忆。

- 详细设计：`docs/design/design.md`
- 测试设计：`docs/test-agent-demo/test-design.md`
- 日志设计：`docs/design/logging-design.md`
- 实施计划：`docs/superpowers/plans/2026-08-26-agent-cli-v0.1.md`
- 使用说明：`README.md`

---

## 2. 项目级规则

### 2.1 成本红线豁免（覆盖全局规则 §8）

> **使用 MiniMax 模型进行本项目开发时，不设任务成本上限。**

理由：本项目实施计划包含约 50 个 Task、~3500-4500 行代码、预计 12 天工作量，按全局规则 §8 的 5 元红线会中途强制停止，导致项目无法交付。

**本项目豁免规则**：

| 项 | 全局规则 | 本项目规则 |
|----|---------|-----------|
| 单任务成本上限 | 5 元 | **不设上限** |
| 4 元告警阈值 | 停止重型动作 | **不生效** |
| 5 元停止阈值 | 停止一切 | **不生效** |
| 子代理派遣上限 | 1-2 个 | 不限 |
| 重型动作（深读、批量） | 4 元时停止 | 不限制 |

**豁免适用范围**：
- 仅本项目（`E:\claude-projects\agent-demo`）工作目录内
- 仅使用 MiniMax 模型时
- 仅开发任务（不含设计评审、文档评审等纯沟通任务——后者仍按全局规则执行成本汇报）

**仍保留的成本管理实践**：
- 每完成一个里程碑（M0-M10）仍汇报累计成本
- 仍优先读官方文档 + 少量关键源码，避免无意义重复读取
- 仍按里程碑分阶段交付，每完成一个里程碑停下来让用户确认是否继续
- 仍避免无意义的大改重写

---

### 2.2 实施方法学

- **TDD 优先**：每个 Task 严格按 plan 中"测试先红 → 实现 → 测试转绿"的顺序执行
- **commit 即里程碑**：每个 Task 完成后立即 commit，commit 信息遵循全局规则 §13 的中文 Conventional Commits 风格
- **commit 即 push**：本地 commit 完成后**立即** push 到 `origin/main`，不积压等待；默认分支是 `main`
- **里程碑 review 点**：每个里程碑（M0-M10）完成后停下，向用户汇报：
  - 已完成内容
  - 测试结果（`mvn test` 全绿）
  - 累计 token / 成本
  - 下一步建议

---

### 2.3 与全局规则的关系

| 全局规则章节 | 在本项目的适用性 |
|------------|----------------|
| §1 项目克隆默认目录 | 适用（项目已在本地） |
| §2 语言与沟通（中文）| **优先** |
| §3 Markdown 写作规范 | 仅适用于 `docs/` 下的 md 文档，不约束本 AGENTS.md |
| §4 本机环境 | 适用 |
| §5 服务器清单 | 不适用（本项目纯本地） |
| §6 Java 代码审查 | **优先**：每次写完/修改 Java 后执行 code-review-refactor |
| §7 图片识别 | 不适用（CLI 项目） |
| §8 任务成本红线 | **被本文件 §2.1 覆盖** |
| §9 任务成本汇报 | 降级为"每里程碑汇报"而非"每任务汇报" |

### 2.4 Mermaid 8.8.3 兼容性规则（docs/ 下 md 文档专属）

> 本项目 `docs/` 下的 Markdown 文档使用 mermaid 8.8.3 渲染（GitHub / VS Code 通用版本）。  
> 下方规则是从 `~/.dsh/AGENTS.md §3` 抽取的本项目精简版，写入以防后续 Agent 重新踩坑。

#### 🔴 必修（违反会导致渲染失败）

| 规则 | ❌ 反例 | ✅ 正确 |
|------|--------|--------|
| **不用 `actor`** | `actor App as main()` | `participant App as "main()"` |
| **classDiagram 不用泛型** | `class Tool~I,O~` | `class Tool`（用注释指向源码） |
| **classDiagram 不用 `List~T~`** | `+toolCalls List~ToolCall~` | `+toolCalls List` |
| **flowchart 不写 `&` 链式语法** | `ToolReg --> ReadFile & WriteFile` | 多行 `ToolReg --> ReadFile\nToolReg --> WriteFile` |
| **节点标签内 ASCII `"`** | `node["文本"关键词"更多"]` | 改用 `「」` 或去掉引号 |
| **节点标签内 `→`** | `node["a → b"]` | 改 `,` 或 `->` |
| **节点标签内 `\|\|`** | `node["W' = ... / \|\|W₀\|\|"]` | 改 `norm(W)` 或 `||x||` 文字 |

#### 🟡 推荐

| 规则 | ❌ 反例 | ✅ 正确 |
|------|--------|--------|
| **participant 名含空格/括号/点** | `participant File as .jsonl 文件` | `participant File as "JSONL 文件"` |
| **subgraph 内 direction** | `subgraph X\n  direction LR` | 8.x 不支持，省略或用注释 |
| **subgraph 之间互连** | `subgraph A --> subgraph B` | 从子图**内部节点**出发：`A_node --> B_node` |

#### ✅ 允许

| 项 | 用途 |
|----|------|
| `<br/>` 在 flowchart 节点 label 内 | 换行（OK） |
| `<br/>` 在 sequenceDiagram Note / 消息文本内 | 换行（OK） |
| `participant X as "Foo Bar"` | 引号包名字含空格 |
| `node["任意不含特殊字符的标签"]` | 标准标签 |

**自检清单**：写完 mermaid 图后扫一遍
1. `grep -n '^\s*actor '` → 必须为 0 匹配
2. `grep -n '~[A-Z][a-z]*~'` → classDiagram 必须 0 匹配（其他图可有）
3. `grep -n '^\s\+[A-Z][a-zA-Z]* -->'` 看 `&` 在末尾的→改为多行
4. `grep -n '||'` 在节点 label 内 → 改为文字

## 3. 关键决策摘要（供后续 Agent 快速对齐）

- **JDK 17 + Spring Boot 3.2 + Maven 3.9**（plan §3）
- **Provider 默认 DeepSeek**（OpenAI 兼容协议，v0.1 单 Provider）
- **Fail-Closed 默认**：所有新工具 `isConcurrencySafe=false` / `isReadOnly=false`
- **stream_options.include_usage=true 强制**：DeepSeek 必须带，否则 token 计数为 0
- **JSONL append-only 会话存储**：文件 0600、目录 0700
- **shell 黑名单匹配**：归一化（basename）+ 短参数簇展开（`-rf` ≡ `-fr` ≡ `-r -f`）
- **不引入 Lombok、spring-boot-starter-web、数据库**
- **stdout 留给模型输出，日志主写文件，WARN+ 镜像 stderr**

---

> 修订记录：
> - v0.1.1（2026-08-26）：新增 §2.4 Mermaid 8.8.3 兼容性规则（docs/ 文档专属）
> - v0.1.0（2026-08-26）：初版；定义成本红线豁免与实施方法学
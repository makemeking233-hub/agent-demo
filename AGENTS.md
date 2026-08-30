# agent-demo 项目级规则

> 本文件为本项目（agent-demo）的 Agent 工作规则，仅在本项目工作目录内生效。
>
> 与全局规则（`~/.dsh/AGENTS.md`）冲突时，本文件优先。

---

## 1. 项目定位

Java 编写的 Claude Code 风格 Agent CLI，第一阶段独立调 DeepSeek API，支持流式对话、工具调用、权限确认、会话持久化、长期记忆。

- 详细设计：`docs/design/design.md`
- 测试文档：`docs/test-agent-demo/`（每批一个时间戳子目录，含四件套 test-design/test-cases/test-report/test-review，见 §2.6）
- 日志设计：`docs/design/logging-design.md`
- 实施计划：`docs/superpowers/plans/2026-08-26-agent-cli-v0.1.md`
- 迭代流程规范：**OpenSpec**（`openspec/`，见 §2.5），默认所有功能改动走 OpenSpec 四阶段
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

---

### 2.5 OpenSpec 迭代流程（默认）

> 本项目使用 **OpenSpec** 作为**默认迭代流程**。任何"修改行为 / 新增功能 / 重构"类工作都应走 OpenSpec 四阶段；纯文本/单行补丁、CI 配置、紧急 hotfix 可豁免。

#### 2.5.1 四阶段流程

| 阶段 | Skill | 何时调用 | 产出 |
|------|-------|---------|------|
| 1. 探索 | `openspec-explore` | 接到模糊需求 / 想法，先澄清目标与边界 | 设计方向（不进 git） |
| 2. 提案 | `openspec-propose` | 方向清晰，一次性铺齐 design / specs / tasks | `openspec/changes/<id>/{proposal.md, tasks.md, design.md, specs/<cap>/spec.md}` |
| 3. 实施 | `openspec-apply-change` | 按 tasks.md 逐项实现（§2.2 TDD/commit/push 节奏） | 实际代码 + 测试 + 文档 |
| 4. 归档 | `openspec-archive-change` | 所有 tasks 勾选完成、`mvn test` 全绿后 | delta spec 合并到 `openspec/specs/`，change 标记 completed |

#### 2.5.2 目录布局

```text
openspec/
├── config.yaml                        # 上下文 + 规则（proposal scope ≤20 行、specs SHALL 规范、tasks < 4h）
├── specs/<capability>/spec.md         # 主 spec（被 archive-change 累积）
└── changes/
    └── <change-id>/
        ├── proposal.md                 # Why / What Changes / Impact / Out of Scope
        ├── design.md                   # 技术设计（接口、数据流、边界）
        ├── tasks.md                    # T1/T2/T3... 任务清单（每项 ≤4h）
        └── specs/<capability>/spec.md  # delta spec（用 ## ADDED/MODIFIED/REMOVED Requirements）
```

#### 2.5.3 与本项目其他规则的衔接

| 本项目规则 | 在 OpenSpec 中的体现 |
|-----------|---------------------|
| §2.2 TDD | 每个 task 内仍执行"测试先红 → 实现 → 转绿"；tasks.md 每项含 `<task>` 测试步骤 |
| §2.2 commit 即 push | tasks.md 每项 commit 后立即 push；用中文 Conventional Commits |
| §2.1 成本豁免 | OpenSpec change 内部仍按里程碑/M 分摊汇报；MiniMax 模型不受 5 元红线 |
| §3 关键决策 | change 内的 design.md 不得违反 JDK17 / Fail-Closed / JSONL 0700 / 无 Lombok 等 |
| jacoco 门禁 | `mvn verify` 在 apply-change 收尾时必跑，LINE≥80% / BRANCH≥70% |

#### 2.5.4 强制门禁

| 场景 | 必须做 |
|------|-------|
| 接到新需求 | **必须先 `openspec-explore`** 澄清再动手；不允许直接进 `openspec-apply-change` 跳过设计 |
| 改完一个 change | **必须 `openspec-archive-change`** 收尾；不允许留 `openspec/changes/<id>/` 未归档导致下次 session 看到一堆"已完成但未归档" |
| archive 后 | delta spec 已合并到 `openspec/specs/`，下次 session 才能看到新行为 |
| 提案 scope | 超过 20 行 → 拆 change（每个 change 一周内可完成） |
| task 颗粒度 | 单 task > 4h → 拆 |

#### 2.5.5 适用/豁免清单

| 工作类型 | 是否走 OpenSpec |
|---------|---------------|
| 新增 slash 命令 / 新增 Tool / 新增 provider | ✅ 走 |
| 重构已有模块（接口签名变更） | ✅ 走 |
| 性能优化（无 API 变更） | ✅ 走（小 change） |
| 文档补充 / 教程 | ❌ 直接 commit |
| CI / 工程脚本调整 | ❌ 直接 commit |
| 安全修复（gitleaks 规则调整等） | ❌ 直接 commit（hotfix） |
| 测试用例补全 | ❌ 直接 commit |
| typo / 注释微调 | ❌ 直接 commit |

#### 2.5.6 快速命令

| 命令 | 作用 |
|------|------|
| 接收大需求 | 先 `openspec-explore` 跑一轮 → 用户确认方向 → `openspec-propose` 一键铺齐 |
| 接收明确任务 | 直接 `openspec-apply-change <change-id>` |
| 完成全部 tasks | `mvn verify` → 全绿后 `openspec-archive-change <change-id>` |
| 调整未归档 change | `openspec-sync-specs <change-id>`（不 archive，只同步 spec） |

#### 2.5.7 当前 OpenSpec 状态

`openspec/changes/` 下的每个目录就是一个 change；completed 后应 archive 到 `openspec/archive/`。下次 session 进入项目**先看一眼** `openspec/changes/` 知道哪些是 WIP、哪些该 archive。

---

### 2.6 测试文档组织规范（每次测试必守）

> 每次测试结束都必须按此规范落文档，保证交付件完整、可追溯、不串批。

#### 2.6.1 目录结构

`docs/test-agent-demo/` 作为测试文档仓库，其下**每次测试一个带时间戳的子目录**（带日期前缀 + 批次语义名）：

```text
docs/test-agent-demo/
├── test-guide.md                            # ① 测试指南/登记表（记录每次测试目标+归档情况，见 §2.6.5）
├── <YYYY-MM-DD>-<批次语义名>/          # 每次测试一个目录（如 2026-08-30-web-ui-e2e）
│   ├── test-design.md                    # ① 测试设计文档
│   ├── test-cases.md                     # ② 用例输出文档
│   ├── test-report.md                    # ③ 测试报告文档
│   └── test-review.md                    # ④ 测试过程完整复盘文档
└── <YYYY-MM-DD>-<批次语义名>/            # 历史批次同样组织
```

#### 2.6.2 单次测试交付件（四件套）

| 顺序 | 文件 | 内容 | 说明 |
|:----:|------|------|------|
| ① | `test-design.md` | 测试范围、目标、环境、策略、用例矩阵、退出标准（DoD） | 测试设计 |
| ② | `test-cases.md` | 全量详细用例表（编号/前置/步骤/预期/优先级）+ 落地情况 | 用例输出（可与设计共用，但要能独立追溯） |
| ③ | `test-report.md` | 实际执行结果、环境适配、缺陷清单、覆盖率 | 测试报告 |
| ④ | `test-review.md` | 完整复盘：流程回顾、问题与根因、做得好的/可改进、交付物 | 过程复盘 |

> 四件套**相互独立**，各司其职；允许 `test-cases.md` 引用 `test-design.md` 的用例表以减少重复，但须注明来源。

#### 2.6.3 强制规则

- 每次测试建立一个**带时间戳的独立子目录**，四件套放各自批次目录内，**不得与其它批次混放**。
- 批次名用 `<YYYY-MM-DD>-<语义名>`（如 `2026-08-30-web-ui-e2e`、`2026-08-29-agent-v01-full-test`）。
- 测试完成后**必须补齐四件套**，缺一不可；后续补写复盘/用例时须说明补写时间与原因。
- 时间戳用**测试执行起始日**，避免同一批多次运行产生多个目录。

#### 2.6.4 与其它规则衔接

- 测试文档统一放 `docs/test-agent-demo/`（与 `docs/design/` 设计文档分开）。
- docs 下 md 遵守 §2.4 Mermaid 兼容性规范。
- 「测试用例补全」类工作按 §2.5.5 豁免 OpenSpec，可直接 commit。

#### 2.6.5 测试指南登记表（test-guide）

`docs/test-agent-demo/test-guide.md` 是**测试总索引**，登记每次测试的目标与归档情况。

- 每次测试归档后，**必在 `test-guide.md` §1 登记表追加一行**（批次目录、测试主题/目标、日期、用例数、结果、四件套✅、状态=已归档）。
- 在 §2 追加该批次的详情小节（目标、执行要点、关键发现、四件套、归档状态）。
- 后续每次测试都按此登记，保证可追溯、不遗漏。

---

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
> - v0.1.3（2026-08-30）：§1 测试文档路径改为批次目录；新增 §2.6 测试文档组织规范（每次测试一个带时间戳子目录 + 四件套 test-design/test-cases/test-report/test-review）
> - v0.1.2（2026-08-26）：§1 加 OpenSpec 路径索引；新增 §2.5 OpenSpec 迭代流程（默认）：四阶段（explore → propose → apply → archive）、目录布局、与 §2.1/§2.2/§3 的衔接、强制门禁、适用/豁免清单
> - v0.1.1（2026-08-26）：新增 §2.4 Mermaid 8.8.3 兼容性规则（docs/ 文档专属）
> - v0.1.0（2026-08-26）：初版；定义成本红线豁免与实施方法学
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
- **commit 即 push**：本地 commit 完成后**立即** push，不积压等待。
  - 迭代需求在**隔离 worktree** 上作业（见 §2.7，唯一允许的隔离方式），此时 push 到**该分支**（`git push -u origin feat/<change-id>`），**不推 `main`**；
  - worktree 里测试全绿、按 §2.7.5 门禁合并回 `main`、并在 `main` 上复验通过后，再把 `main` push 到 `origin/main`。
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
| jacoco 门禁 | `mvn verify` 在 apply-change 收尾时必跑。阈值（LINE≥80% / BRANCH≥70%）、考核方式（`PACKAGE` 逐包独立考核；`includes` 必须**成对**写「根包」与「根包 `.*`」，只写其一会静默漏检）与排除清单的**真源**是 `openspec/specs/testability/spec.md` 的《覆盖率门禁》Requirement |

#### 2.5.4 强制门禁

| 场景 | 必须做 |
|------|-------|
| 接到新需求 | **必须先 `openspec-explore`** 澄清再动手；不允许直接进 `openspec-apply-change` 跳过设计 |
| 接到新需求 | **必须先建 worktree 隔离**（见 §2.7，唯一允许的方式；只建分支不算），不允许直接在 `main` 上迭代 |
| 改完一个 change | **必须 `openspec-archive-change`** 收尾；不允许留 `openspec/changes/<id>/` 未归档导致下次 session 看到一堆"已完成但未归档" |
| change 测试全绿 | **必须按 §2.7.5 门禁合并回 `main`**（合并后要在 `main` 上复验一次）；不允许把已完成的 change 长期挂在分支上 |
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

### 2.7 worktree 隔离（迭代需求**强制**，无例外）

> 🔴 **强制条款（2026-09-22 用户明确要求，每次必须严格执行）：**
>
> **任何迭代需求——新功能 / 行为变更 / 重构 / 性能优化 / 修 bug / 走 OpenSpec 的任何 change——都必须先 `git worktree add` 建独立 worktree，在 worktree 里作业，完成并验证后再合并回 `main`。**
>
> 具体地说：
>
> 1. **唯一允许的隔离方式是 git worktree**。只建分支（`git checkout -b`）**不算隔离**，不允许——分支与 `main` 共享同一个工作目录、同一个 `target/`，多 agent 并行时照样互相踩。
> 2. **接到需求的第一件事就是建 worktree**，在动任何一行代码 / 跑任何一条 `mvn` 之前。不允许「先改着，回头再补 worktree」。
> 3. **绝不允许在 `main` 工作区直接改代码后提交**。走 OpenSpec 的 change 尤其必须如此。
> 4. **作业期间 `pwd` 必须在 `.worktrees/<change-id>` 下**；在主工作区跑构建会踩坏正在运行的应用（见 §2.7.1 第 4 条实际事故）。
> 5. **合并回 `main` 的前提是「worktree 里测试全绿」**——测试没跑通的 worktree，一步也不许往 `main` 上并（门禁见 §2.7.5）。
>
> **违反本条 = 该次改动视为未完成**，必须回退到 worktree 里重做，不允许「这次先这样」。

#### 2.7.1 为什么（实测教训；2026-09-13 三次 + 2026-09-22 一次）

同一工作区里多 agent 并行 + 在主工作区迭代，实际造成过四次事故：

| 事故 | 现象 | 根因 |
|------|------|------|
| 增量编译残留 | 运行期 `NoClassDefFoundError: EditFileTool$Input`，一个工具崩掉整条 SSE 连接 | 应用运行期间对同一 `target/` 执行了构建 |
| 测试类残留 | `NoClassDefFoundError: ToolCallClosureTest$1`（匿名内部类 class 文件缺失） | 两个 agent **在同一 `target/` 并发跑 Maven**，互相踩坏增量编译输出 |
| 误提交他人工作 | 自己的归档提交里混进了另一个 agent 未完成的 change 提案 | 用了 `git add -A` 这类过宽模式 |
| **运行中应用被并发构建打断**（2026-09-22） | Web 应用跑着跑着 `NoClassDefFoundError: io.netty.util.concurrent.DefaultPromise$1` + `ch.qos.logback.classic.spi.ThrowableProxy`，随后连接全部 reset，进程退出码 1 | 应用从主工作区的 `agent-web/target/agent-web.jar` 启动；**另一个 agent 在同一主工作区跑 mvn，把那个 jar 文件整个替换掉**，运行中的 JVM 惰性加载类时从被换掉的 jar 里找不到类 |

第 4 条是**在 main 工作区迭代**的直接代价：应用的 fat jar 与并发构建共用同一个路径，构建一跑应用就废。只要构建发生在独立 worktree 里（各自独立的 `target/`），这条事故就不可能发生。

另有两个直接代价：

- `main` 上存在他人未完成的改动时，`mvn verify` 会因别人的 WIP 变红，**无法判断是不是自己弄坏的**（实测遇到过 `agent-core` 因他人改动编译失败、测试类残留报错）。
- 并行 agent 的未提交文件会与自己的改动**争抢同一个文件**（实测 `AgentLoop.java`、`OpenAiCompatibleMapper.java`、`ChatCommand.java` 都被双方同时改过）。

#### 2.7.2 标准流程

```bash
# 1. 隔离：**必须**建 worktree（唯一允许的方式；不允许只用 git checkout -b）
git worktree add .worktrees/<change-id> -b feat/<change-id>

# 2. 在隔离工作区里走 OpenSpec 四阶段（explore → propose → apply → archive）
cd .worktrees/<change-id>
pwd                                     # 自检：必须在 .worktrees/<change-id> 下

# 3. 每个 task 完成即 commit + push 到**本分支**（不推 main）
git push -u origin feat/<change-id>

# 4. 验证：mvn -o -pl agent-core,agent-web verify + 前端 npx vitest run 全绿

# 5. 测试全绿后合并回 main（门禁见 §2.7.5；能快进就快进，有冲突才 merge commit）
cd <主工作区>
git merge feat/<change-id>
# 合并后在 main 上再验证一次，通过才 push
git push origin main

# 6. 清理（合并成功且 main 复验通过后才做）
git worktree remove .worktrees/<change-id>
git branch -d feat/<change-id>
```

> 第 5 步不是「顺手并过去」，而是一道有门禁的操作——详情见 §2.7.5。

**接到需求时的开工自检（每次必做）**：

| 顺序 | 动作 | 通过判据 |
|:--:|------|---------|
| 1 | `git worktree list` | 目标 `.worktrees/<change-id>` 不存在（存在则先确认是不是自己上次的） |
| 2 | `git worktree add .worktrees/<change-id> -b <type>/<change-id>` | 输出 `Preparing worktree` 且无报错 |
| 3 | `cd .worktrees/<change-id>` | `git rev-parse --show-toplevel` 指向 worktree 路径 |
| 4 | `openspec/changes/<change-id>/` 在 worktree 内建立 | 文件落在 worktree 里，不是主工作区 |

#### 2.7.3 命名约定

| 项 | 约定 |
|----|------|
| 分支名 | `feat/<change-id>`、`fix/<change-id>`、`chore/<change-id>`（与 OpenSpec change id 对齐） |
| worktree 路径 | `.worktrees/<change-id>`（已在 `.gitignore` 中） |
| OpenSpec change | 在分支内照常在 `openspec/changes/<change-id>/` 建立，归档后随分支合并回 `main` |

#### 2.7.4 提交纪律（多 agent 并行时尤其重要）

- **只用显式路径 `git add <path>...`**，禁止 `git add -A` / `git add .`——会把其他 agent 的未提交文件卷进自己的提交（实测发生过）。
- 提交前用 `git status` 核对暂存清单里**没有不属于本次改动的文件**。
- **不修改、不提交、不删除其他 agent 的未提交文件**；发现文件被他人同时改动时先停下问用户。
- 从分支合并/推送前，先确认 `main`（或分支）上的既有失败**不是自己造成的**——对照改动前的基线，必要时在干净 HEAD 上复现一次。

#### 2.7.5 合并回主分支（**测试通过是前提**）

> **worktree / 分支里的 change 改完 → 测试通过 → 合并回 `main`。**
> **测试没通过的分支，一步也不许往 `main` 上并。** 「本地看着没问题」不算通过，必须有命令输出为证（见全局规则 §6「证据先于断言」）。

##### 2.7.5.1 合并前门禁（逐条打勾，缺一不可）

| # | 门禁项 | 判定方式 |
|:--:|--------|---------|
| 1 | 分支上质量门**全绿** | `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` 全绿；前端 `npx vitest run` 全绿；`npx tsc --noEmit` 错误数**不超过基线**（本项目基线 **7** 个既有错误，2026-09-13 补 `vite-env.d.ts` 后从 27 降下来，详见 §2.7.7） |
| 2 | OpenSpec change 已归档 | `openspec/changes/<id>/` 已 archive，delta spec 已并入 `openspec/specs/`（§2.5.4）；`tasks.md` 无未勾选项 |
| 3 | 分支工作区干净 | `git -C .worktrees/<id> status -sb` 无未提交改动；提交清单里没有他人文件（§2.7.4） |
| 4 | **与 `main` 同步后重跑门禁 1** | `main` 可能已被并行 agent 推进：先在分支上 `git merge main`（或 `git rebase main`），**再跑一次门禁 1**。在旧的 `main` 上测绿 ≠ 在新的 `main` 上能绿 |
| 5 | 既有失败已归因 | 门禁 1 若有红色：能在**合并前的干净 HEAD** 上复现 → 既有问题，记录后放行；不能复现 → 是自己弄坏的，**禁止合并** |

> ⚠️ **新建 worktree 的已知环境前置：`agent-web/src/main/resources/static/` 不存在。**
> 该目录是 `frontend-maven-plugin` 在 `generate-resources` 阶段产出的构建物，已在 `.gitignore:47` 排除，
> 因此**任何全新 worktree 里都没有它**；而 `-DskipNpm=true` 又跳过前端构建，于是
> `agent-web/src/test/java/com/example/agent/web/api/WebIntegrationTest.java` 的
> `rootServesIndexHtml`（断言 `GET /` 返回 200）会因为 SPA 首页缺失而拿到 **404 → 门禁 1 变红**。
>
> 这不是代码问题，是全新 worktree 的环境缺口。两种处置（任选）：
>
> ```bash
> # 方案 A（快，推荐用于文档/后端改动）：把主工作区已有的构建产物复制进 worktree
> cp -r ../../agent-web/src/main/resources/static agent-web/src/main/resources/static
> # 方案 B（真跑前端）：不传 -DskipNpm，让 frontend-maven-plugin 走 npm ci + npm run build
> mvn -o -pl agent-core,agent-web verify -Dsurefire.excludes=**/e2e/**
> ```
>
> `static/` 是 gitignored 的，复制进去**不会进提交**（`git status --porcelain` 应保持干净，可据此自检）。

##### 2.7.5.2 合并执行

```bash
# 在主工作区，先确认 main 干净（有他人未提交改动时先问用户，别硬合）
cd E:/claude-projects/agent-demo
git status -sb
git rev-parse HEAD                      # 记下来，回退要用（快进合并没有 merge commit）

# 合并：能快进就快进；需要保留 change 边界时用 --no-ff
git merge feat/<change-id>

# 合并后在 main 上再验证一次——这是最后一道闸
cmd.exe /c "mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**"
cd agent-web/frontend && npx vitest run && cd ../../..

# 复验通过才 push
git push origin main
```

**必须复验的理由**：`main` 上同时挂着别人的改动，分支上的绿只覆盖「分支 + 当时那个 main」的组合。合并后在 `main` 上再跑一次，才能把「谁的改动弄红的」这件事钉死在合并点上。

##### 2.7.5.3 合并后清理（复验通过后立刻做）

```bash
git worktree remove .worktrees/<change-id>
git branch -d feat/<change-id>
git push origin --delete feat/<change-id>   # 若该分支已 push 过
```

清理**必须**在复验通过之后：复验没过时 worktree 还得留着修 bug。

##### 2.7.5.4 失败回退

| 时机 | 处置 |
|------|------|
| 合并后复验失败、**尚未 push** | `git reset --hard ORIG_HEAD` 回到合并前，回分支上修；修完重走门禁 |
| 合并后复验失败、**已 push** | `git revert -m 1 <merge-commit>` 并 push（保留痕迹，不改写公共历史），再回分支上修 |
| 快进合并后想撤销 | 快进没有 merge commit：`git reset --hard <2.7.5.2 里记下的 HEAD>`（已 push 时同样用 `revert`） |
| 冲突无法在分支内干净解决 | 停下问用户，**不要**在主工作区里手工解冲突后合并 |

##### 2.7.5.5 底线

- 同一 change 连续 **3 次**合并到 `main` 后复验失败 → 停止合并，把证据（命令 + 输出 + 复现步骤）整理给用户，由用户决定是回退 `main` 还是继续。
- **不许**为了「让合并看起来成功」而跳过门禁、注释掉失败的测试、放宽 jacoco 阈值、或把失败归给「环境问题」而不给出对照复现。
- 合并是**单向**的：只允许 `分支 → main`。不允许在 `main` 上改完之后再往分支上并。

#### 2.7.6 豁免

| 场景 | 是否需 worktree |
|------|-----------|
| 任何迭代需求（含走 OpenSpec 的 change） | **必须建 worktree**（无例外） |
| 修 bug / 行为变更 / 重构 / 性能优化 | **必须建 worktree** |
| 纯文档 / typo / 注释微调（**不碰任何 `src/` 下的代码**） | 可豁免 worktree，可直接在 `main` 上 commit；**豁免的只是「建 worktree」，不是「测试」**——一旦改了代码就必须建 worktree 并跑门禁 |
| 紧急 hotfix | **仍需 worktree**；若用户明确同意豁免，必须在同一个任务内补建 worktree 或补跑门禁 1，并在回复里说明豁免理由 |

> ⚠️ 判定界限：**只要 `git status` 里出现 `src/` 下的文件改动，就必须先有 worktree**。文档类改动（`docs/`、`AGENTS.md`、`openspec/` 下的 md）才可以走豁免。

#### 2.7.7 tsc 基线的构成与维护

`npx tsc --noEmit` 的错误数是门禁项之一，**当前基线 7**。这个数字在 2026-09-13 从 27 降到 7，原因必须记下来，否则后人会误判：

| 原错误数 | 来源 | 性质 |
|:--:|---|---|
| 16 | `TS2307 Cannot find module '*.module.css'`——全项目每个 CSS module import 各一条 | **假报错**，Vite 构建期正常处理 |
| 1 | `TS2339 Property 'env' does not exist on type 'ImportMeta'` | **假报错**，同一个根因 |
| 7 | `src/api/fs.test.ts` 的 `global`（4 条）、`Sidebar.tsx` 回调类型、`useVoiceChat.test.ts` 的 Mock 签名、`vite.config.ts(97)` 的 test 字段重载 | 真正的既有问题 |

根因是项目缺 `agent-web/frontend/src/vite-env.d.ts`。补上 `/// <reference types="vite/client" />` 与 `/// <reference types="vite-plugin-pwa/client" />` 后，前 17 条一次消失。

**新增组件时注意**：补了该文件之后，"新加一个 `.module.css` 就多一条 TS2307"这条规律**不再成立**。若基线数字再变，先确认是不是又出现了同类假报错，而不要直接认定是自己写错了。

#### 2.7.8 门禁命令与「应用正在运行」的冲突（2026-09-23 实测）

主工作区跑着 web 应用时，`verify` 会在两处撞车，两处的正确处置**完全不同**：

| 撞车点 | 现象 | ✅ 正确处置 | ❌ 错误处置 |
|--------|------|-----------|-----------|
| `spring-boot:repackage` | `Unable to rename 'agent-web.jar' to 'agent-web.jar.original'` | **先停掉应用**（release jar）再跑门禁；测试与 jacoco 在 repackage 之前已跑完，可据此判定用例结果 | 加 `-Dspring-boot.repackage.skip=true` —— 见下方「副作用」 |
| 排障时排查端口占用 | `Port 18080 was already in use` | `Get-NetTCPConnection -LocalPort 18080 -State Listen` 找到 PID，确认是本项目的旧实例后停掉 | 直接换端口启动 —— 会掩盖「旧实例还在跑」这件事 |

**`-Dspring-boot.repackage.skip=true` 的副作用（真实踩过）**：它跳过 repackage 后，`maven-jar-plugin`
产出的**普通 jar 会覆盖掉可执行 fat jar**，manifest 里没有 `Main-Class` / `Start-Class`。
于是 README §7.4 记的启动方式 `java -jar agent-web/target/agent-web.jar` 会静默失效
（`no main manifest attribute`），而**门禁仍然是绿的**——绿色门禁掩盖了启动能力的丢失。
若确实用了该参数，跑完必须补一次真 repackage：

```bash
mvn -o -pl agent-core,agent-web package -DskipTests -DskipNpm=true
# 校验：manifest 必须含 Start-Class: com.example.agent.web.WebApplication
```

### 2.8 外部源码参考目录

后续 agent 接到「参考 xxx 源码」类需求时，应优先在 `E:\claude-projects\` 目录下查找——该目录托管常用源码项目，包括但不限于：

| 项目 | 简介 |
|------|------|
| `agent-demo` | 本项目（Java Claude Code 风格 Agent CLI） |
| `cc-switch` / `channels` / `claude-code-analysis` / `claude-code-gui` | Claude Code 相关辅助项目 |
| `deepseek-harness` / `DeepSeek-Reasonix` / `pi-mono` | DeepSeek / Anthropic Claude 协议封装与衍生 |
| `TencentDB-Agent-Memory` | 长期记忆 / 知识库相关 |
| `FlClash` / `fullvidio` / `nacos` / `openclaw` / `rocket-mq` / `tv-box` | 其它常用参考源码 |

#### 查找约定

- **优先级**：`E:\claude-projects\` 本地优先；找不到再去 GitHub / 官方文档搜索
- **多 agent 并行时**：注意 `E:\claude-projects\` 下可能有其他 agent 同时改动；只用 `git status` / `git diff` 读，不写不删不 commit（§2.7.4）
- **跨项目引用**：禁止在 agent-demo 里引用 `E:\claude-projects\` 其它项目的源码路径（不构成可移植依赖）；如确需复用，参考其设计而非直接 copy
- **新加项目**：在该目录下新建源码项目时，可在本节追加一行登记，便于后续 agent 检索

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
- **迭代需求一律走 worktree 隔离（强制，唯一方式）**（§2.7）：接到需求先 `git worktree add`，在 worktree 内作业；只建分支不算隔离；不在 `main` 上直接改代码；提交只用显式路径 `git add <path>`
- **测试全绿才可合并回 `main`**（§2.7.5）：合并前跑门禁、合并后必须在 `main` 上复验一次，复验通过才 push

---

> 修订记录：
> - v0.1.9（2026-09-23）：§2.5.3 的 jacoco 门禁一行改为指向 `openspec/specs/testability/spec.md §覆盖率门禁`（门禁规则改成有规格锚点，不再只写在本文件里）；同时写明 `includes` 必须成对写根包与子包——`PACKAGE` 元素下 `X` 只中根包、`X.*` 只中子包，实测只写其一会让子包的覆盖缺口静默漏检（fix-jacoco-rule）
> - v0.1.8（2026-09-22）：🔴 **§2.7 由「分支隔离（默认）」升格为「worktree 隔离（强制，无例外）」**（用户明确要求，每次必须严格执行）——新增 5 条强制条款（worktree 是唯一允许的隔离方式、接到需求第一件事就是建 worktree、绝不在 main 上直接改、作业期间 pwd 必须在 worktree 内、测试全绿才可合并）+「违反=该次改动视为未完成」；§2.7.1 事故表新增第 4 条（2026-09-22 运行中应用被同工作区并发构建替换 fat jar 打断，`NoClassDefFoundError: DefaultPromise$1`）；§2.7.2 删掉「或 git checkout -b」并新增「开工自检」四步表；§2.7.6 豁免表按「是否碰 `src/` 代码」重写（修 bug/重构/性能优化一律必须 worktree，紧急 hotfix 也需 worktree）；**§2.7.5.1 新增「新建 worktree 的已知环境前置」**（`static/` 被 gitignore 排除 → 全新 worktree 里 `WebIntegrationTest.rootServesIndexHtml` 会 404 变红，给出两种处置）；§2.2、§2.5.4、§3 同步改为 worktree 强制
> - v0.1.7（2026-09-23）：新增 §2.7.8 门禁命令与「应用正在运行」的冲突——`repackage` 撞 jar 占用该先停应用；`-Dspring-boot.repackage.skip=true` 会把可执行 fat jar 覆盖成普通 jar（启动能力静默丢失而门禁仍绿），若用了必须补真 repackage 并校验 manifest 的 `Start-Class`
> - v0.1.6（2026-09-13）：§2.7.5.1 门禁 1 的 tsc 基线由 27 更正为 **7**；新增 §2.7.7 说明基线的构成与维护（17 条假报错来自缺失的 `vite-env.d.ts`，剩余 7 条才是真既有问题）
> - v0.1.5（2026-09-13）：新增 §2.7.5 合并回主分支（测试通过是前提）——5 条合并前门禁（含同步 main 后重跑、既有失败归因）、合并执行、合并后清理、失败回退表、3 次失败兜底；§2.7 引言加「测试全绿才可合并」；§2.7.2 第 5/6 步指向门禁；原 §2.7.5 豁免顺延为 §2.7.6；§2.2 与 §2.5.4 各加一条；§3 加一条
> - v0.1.4（2026-09-13）：新增 §2.7 分支隔离（迭代需求默认）——分支/worktree 标准流程、命名约定、多 agent 并行的提交纪律（禁用 `git add -A`）、豁免清单；§2.2「commit 即 push」明确为推送当前分支；§2.5.4 强制门禁加一行；§3 加一条
> - v0.1.3（2026-08-30）：§1 测试文档路径改为批次目录；新增 §2.6 测试文档组织规范（每次测试一个带时间戳子目录 + 四件套 test-design/test-cases/test-report/test-review）
> - v0.1.2（2026-08-26）：§1 加 OpenSpec 路径索引；新增 §2.5 OpenSpec 迭代流程（默认）：四阶段（explore → propose → apply → archive）、目录布局、与 §2.1/§2.2/§3 的衔接、强制门禁、适用/豁免清单
> - v0.1.1（2026-08-26）：新增 §2.4 Mermaid 8.8.3 兼容性规则（docs/ 文档专属）
> - v0.1.0（2026-08-26）：初版；定义成本红线豁免与实施方法学
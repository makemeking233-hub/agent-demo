# 测试过程复盘 — fix-stale-model-fallback

- 批次目录：`docs/test-agent-demo/2026-09-18-fix-stale-model-fallback/`
- 复盘日期：2026-09-18
- 相关交付：`test-design.md` / `test-cases.md` / `test-report.md` / 本文件

## 1. 流程回顾

| 阶段 | 动作 | 结果 |
|------|------|------|
| 接单 | 用户给出代码级线索（`App.tsx` 与 `ChatController.resolveModel` 同写 `deepseek-chat`）并附本机 `/api/chat/models` 实测列表 | 线索成立，但**不止两处**：自查发现第 6 处（`WecomMessageDispatcher`），且根因是「双真源」而非「常量写错」 |
| 冲突评估 | 发现并行 agent 正在同一批文件上作业（`ChatController` / `ChatStreamService` / `AgentLoop`） | 按 `AGENTS.md §2.7.4` 停下，用弹框向用户报冲突面并给出三种处置；用户选「立即开工 + 授权适配其测试」 |
| 隔离 | `git worktree add .worktrees/fix-stale-model-fallback -b fix/stale-model-fallback main` | 与 4 个并行 worktree 互不干扰 |
| 提案 | OpenSpec 四阶段第 2 步，一次性铺齐 proposal / design / tasks + 三个能力域的 delta spec | `openspec validate` 通过 |
| 实施 | 按 T1–T6 逐项 TDD（红 → 实现 → 绿 → commit） | 6 个功能提交 + 1 个测试/文档提交 |
| 门禁 | 分支全量门禁 + 在游离 worktree 上取干净 main 基线做对照 | 见 `test-report.md` §1 |
| 收尾 | 归档 change → 合并回 main → main 复验 → push → 清理 | 见本文件 §5 |

## 2. 问题与根因

### 2.1 需求层面

| 问题 | 根因 | 处置 |
|------|------|------|
| 用户给的是「两处硬编码」，实际是 6 处 | 只按报错点搜索，不按**不变量**搜索 | 改为搜索「所有把某个模型 id 当默认/兜底的位置」，并额外发现微信通道那处 |
| 用户的「改掉常量」方案不能根治 | 双真源：列表读 `providers`、校验读 `supported-models` | 用弹框提出「单一真源」方案并说明为何只改常量会复发；用户采纳 |
| 「非法模型该 400 还是静默兜底」是真实分叉 | 关联到项目 §3「Fail-Closed 默认」 | 用弹框让用户拍板，而非自行选边 |

### 2.2 实施层面

| 问题 | 根因 | 处置 |
|------|------|------|
| **假绿**：`-Dtest=A+B` 让 surefire 一个用例都没跑，却报 `BUILD SUCCESS` | 误以为 `+` 是 multiple-pattern 分隔符 | 改用逗号；并把「显式核对 `Tests run:` 计数」作为固定动作——只看 `BUILD SUCCESS` 会被这种情况骗过 |
| 我写的测试断言越界（T-3） | 把「不该出现硬编码值」这一意图，写成了「绝不允许出现某个具体字符串」，忽略了「服务端自己声明它为默认值」这一合法情形 | 收窄为「返回的 id ∈ 目录 ∪ 服务端默认值 ∪ 空串」 |
| 实现与自述契约不一致（T-2） | 契约原文含糊（只写了「除目录为空外」） | 先写清契约再对齐实现 |
| 提交只含删除、不含其余改动（T-4） | `git add` 中夹带已删除文件的路径会让整条命令失败 | `git commit --amend` 补全（未推送，未改写公共历史） |

### 2.3 环境层面

| 问题 | 根因 | 处置 |
|------|------|------|
| worktree 里没有 `node_modules`，vitest 跑不起来 | 新 worktree 不继承依赖 | 先建目录联接复用主工作区依赖（`node_modules/` 在 `.gitignore:37`，不污染 git）；随后发现让 maven 在该 worktree 跑 `npm install` 会**写进主工作区**的依赖目录，改用 `rmdir` 安全拆除联接、让 maven 装一套独立依赖 |
| 门禁一跑 4.5 分钟 | 文档门禁命令默认会做前端 `npm install` + `vite build` | 首次跑不加 `skipNpm` 以生成 `static/`；后续跑按文档命令加 `-DskipNpm=true`（约 2.5 分钟） |
| 不能在主工作区跑构建 | 应用正从主工作区 `target/classes` 运行（`§2.7.1` 记录过事故） | 基线对照改在**游离 worktree** 上做，用完即删 |
| maven 测试向真实 `~/.agent-demo/logs/` 写日志目录（T-5） | 日志根由 logback 解析到真实 `user.home`，`AGENT_DEMO_HOME_PROPERTY` 只覆盖数据目录 | 用 `session.jsonl` 的 `cwd` 字段精确归属，只删本人的 10 个、保留他人 41 个，并导出审计 CSV；缺口本身记录为待单独立项 |

## 3. 做得好的

1. **没有相信「两处」这个前提**。用户的线索准确但范围不全；按不变量而非按报错点搜索，才挖到第 6 处与真正的双真源根因。
2. **冲突面先亮牌再动手**。发现并行 agent 正在改同一批文件时，先停下用弹框给出事实与选项，而不是硬上或默默等待。
3. **拒绝把「测试通过」当作通过**。两次抓住自己制造的假象：一次是 surefire `+` 导致的 0 用例假绿，一次是全量 vitest 报 `1 failed` 时先去看清到底挂的是哪一例（结果挂的是我自己新写的断言，而不是既有噪音）。
4. **既有失败一律先做对照复现**。jacoco `security` 违规与 vitest 的 9 条未处理错误，都在**干净 main 的游离 worktree** 上复现后才判定为既有；并顺带量化出本次改动把 jacoco 违规从 5 条降到 1 条。
5. **数据污染按内容指纹 + 结构字段精确归属**。没有按时间戳「凭感觉批量删」——改用 `session.jsonl` 里的 `cwd` 字段，把归属判据变成可复核的一条规则，并导出审计 CSV。
6. **反向断言**。针对「静默降级」这类缺陷，专门写了「不该发生的事没发生」的用例（非法 model 时 `streams.create` 从未被调用、兜底值必须真的在目录里、前端不得凭空发明 id）。

## 4. 可改进的

1. **一次门禁跑得太晚**。TDD 逐项只跑定向测试（`-Dtest=...`），直到 T7 才跑全量；如果早期并行 agent 的改动与我冲突，会很晚才发现。后续可在每个 commit 后加一次「快速相关包」测试（如 `-Dtest='com.example.agent.web.api.**'`）。
2. **前端依赖用联接是权宜之计**。虽然安全拆除且验证了目标完好，但「共享 `node_modules`」本身有跨工作区缓存污染的理论风险。更好的做法是首次就用 `npm ci` 装独立依赖，或给 maven 的 frontend 插件显式指定 `installDirectory`。
3. **`git add` 的失败被低估**。一条 pathspec 报错就让整条命令失效，而输出里的 `fatal` 淹没在其他 warning 中；差点造成「提交信息与内容不符」。后续要么先 `git status` 核对暂存清单（`§2.7.4` 本来就要求），要么用 `git add -A -- <paths>` 之外的形式避免混入删除路径。
4. **日志根目录隔离缺口未修**。已记录在 `test-report.md` §5.3，建议单独立 change（对应 `observability` 两条 Requirement）。
5. **并行改动的影响面只评估、未量化**。已知另一个 change 在改 `ChatController` / `ChatStreamService`，但没有对「他们合并时会撞哪里」做具体预判记录（本次是在合并前才意识到他们的 `ChatControllerProviderInferenceTest` 第 7 例断言与本 change 直接矛盾）。

## 5. 对标本项目的工程约束

| 约束 | 落实 |
|------|------|
| `§2.2` TDD + commit 即 push | 每项先写测试跑红，再实现，转绿后提交 |
| `§2.5` OpenSpec 四阶段 | explore（读码核实线索）→ propose（validate 通过）→ apply（T1–T6）→ archive |
| `§2.7` 分支隔离 | 全程在 `.worktrees/fix-stale-model-fallback`，未在 main 上改一行 |
| `§2.7.4` 只用显式路径 `git add` | 全部提交均列显式路径；未触碰他人文件 |
| `§2.7.5.1` 门禁 5 | 既有失败在干净 main 上游离复现后才放行 |
| `§2.6` 测试文档四件套 | 本目录四件齐备 + `test-guide.md` 登记 |

## 6. 遗留与移交

| # | 事项 | 建议 |
|:--:|------|------|
| 1 | 并行 change `add-provider-catalog-abstract` 的 `ChatControllerProviderInferenceTest` 按旧构造器 `new ChatController(streams, env)` 编写，且其第 7 例断言「非法 model 静默兜回 `deepseek-chat`」与本 change **直接矛盾**（该断言必须反转，不是改参数能解决） | 合并其分支时需人工处置；已向用户报备 |
| 2 | jacoco `security` 包 branches 0.63（既有） | 建议单独立 change 补 `HomePathGuard` / `TrustedHostFilter` 分支用例 |
| 3 | maven 测试向真实 `~/.agent-demo/logs/` 写目录（既有） | 建议单独立 change：让测试的日志根也走隔离属性 |
| 4 | CLI 路径的同类残留（`AgentLoop.DEFAULT_MODEL`、`AgentConfig.defaults()`、`SlashCommand` 列表仍含 `deepseek-chat`） | 本次按用户选择刻意未动；若需清理建议单独 change |

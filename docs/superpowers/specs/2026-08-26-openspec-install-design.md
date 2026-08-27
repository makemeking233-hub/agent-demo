# OpenSpec 集成设计（agent-demo v0.1）

**日期**：2026-08-26
**作者**：MiniMax-M3
**状态**：待用户 review

---

## 1. 目标

在 `agent-demo` 项目根目录安装 OpenSpec CLI（SDD 框架），初始化 `openspec/` 目录，并通过一个示例 change（`add-resume-command`）走通完整工作流，让后续 v0.2 / v0.3 迭代都通过 OpenSpec 管理 spec / change / archive。

## 2. 背景

- `agent-demo` 是 Claude Code 风格的 Java Agent CLI，v0.1 完整跑通 50 个 Task
- `docs/design.md`（500+ 行）和 `docs/test-design.md`（170+ 行）目前是 spec 真相源，但仅 Markdown
- md-main 已深度研究 OpenSpec（v1.5+ npm 包 `@fission-ai/openspec`），社区采用率 20 万+/周
- DSH = **DeepSeek Harness**（用户当前会话运行的 Agent 平台，端口 3080）

OpenSpec 解决的核心问题：
- spec 跨会话、跨工具持久化
- Brownfield 友好（Delta Spec 增量更新）
- 与 DSH 集成：每次 AI 生成 spec 都注入项目 context

## 3. 范围

### 3.1 包含

- `npm install -g @fission-ai/openspec@latest`（全局 CLI）
- `cd E:\claude-projects\agent-demo && openspec init`（交互式）
  - AI 工具选 **DSH**（DeepSeek Harness）
  - profile 选 **expanded**（解锁 verify / ff / continue / onboard）
- 配置 `openspec/config.yaml` 的 `context:` 段（注入 agent-demo 项目背景）
- 走一个示例 change `add-resume-command`（plan §15 v0.2 第一个目标）
- 所有 spec 进 Git

### 3.2 不包含

- 不写 v0.1 已完成功能的"反向 spec"（plan §8.6.1 建议："不要先文档化整个系统，从第一个真实 change 开始"）
- 不修改 `docs/design.md`（OpenSpec 与 design.md 并存，分别管"行为契约 vs 技术细节"）
- 不动 DSH 全局配置（只写项目级 skills）

## 4. 设计

### 4.1 安装与初始化

```bash
# 1. 全局安装 OpenSpec CLI
npm install -g @fission-ai/openspec@latest
openspec --version    # 验证（应输出 1.5.x）

# 2. 在 agent-demo 根目录 init
cd E:\claude-projects\agent-demo
openspec init
# 交互回答：
#   - AI tool: DSH (DeepSeek Harness)
#   - profile: expanded

# 3. 验证生成的文件
ls -la openspec/           # specs/ changes/ changes/archive/ config.yaml
ls -la .dsh/skills/         # openspec-* 5 个 skill
```

### 4.2 `openspec/config.yaml` 内容

```yaml
schema: spec-driven

context: |
  agent-demo: Claude Code 风格 Java Agent CLI
  技术栈: JDK 17 + Spring Boot 3.2 + Maven 3.9 + picocli 4.7 + JLine3 3.25 + JTokkit 0.6.1
  LLM Provider: DeepSeek (OpenAI 兼容协议)
  文档: docs/design.md (技术设计) / docs/test-design.md (测试设计) / docs/superpowers/plans/2026-08-26-agent-cli-v0.1.md (实施计划)
  v0.1 状态: 已完成 M0-M10 (92 个测试全绿 + jacoco 门禁通过)
  v0.2 候选: /resume /model Ctrl+C 中断 deepseek-reasoner 等
  规范: .codex/rules/Codex本地开发规范-Java篇.md (AOSP 风格 + 阿里 P3C + 华为 CleanCode)
  约定: 中文 commit (chore/feat/fix/refactor/docs) / 中文文档 / commit 即 push
  强制门禁: jacoco LINE >=80% BRANCH >=70%

rules:
  proposal:
    - Keep scope section under 20 lines
  specs:
    - Use SHALL for user-visible behavior only
    - Each Requirement includes 2+ Scenarios (happy + error path)
  tasks:
    - Each task completable in one session (< 4 hours)
    - Always include test step before commit
```

### 4.3 示例 change：`add-resume-command`

按 OpenSpec §9.2 artifact DAG 顺序产出：

#### 4.3.1 `openspec/changes/add-resume-command/proposal.md`

```markdown
# Change: Add /resume Command

## Why
v0.1 SessionStore 只追加写不读取；用户重启 agent 后无法继续上次对话。
v0.2 第一个目标：让用户能 resume 最近一次 session。

## What Changes
- 新增 `/resume` slash 命令：扫描 `~/.agent-demo/sessions/` 最近 .jsonl 文件，反序列化为 MessageHistory，注入 AgentLoop
- SessionStore 加 `loadLatest()` 方法（v0.1 已加 `load(id)` 占位，实现完）
- SlashCommand 加 `/resume` 分支（v0.1 已有 /help /clear /quit /history 4 个）

## Impact
- 受影响 spec: `cli` (新 domain)
- 受影响代码: SessionStore / SlashCommand / AgentLoop / ChatCommand
- 估计: 1.5 天 (T1 SessionStore.loadLatest / T2 SlashCommand / T3 AgentLoop / T4 测试+doc)

## Out of Scope
- session 列表 UI（v0.3）
- 跨 session 关键词检索（v0.3 + sideQuery）
- session 导出 / 导入（v0.4）
```

#### 4.3.2 `openspec/changes/add-resume-command/specs/cli/spec.md`（delta）

```markdown
## ADDED Requirements

### Requirement: Session Resume
The system SHALL restore conversation history from the most recent session file on `/resume` command.

#### Scenario: Most recent session exists
- GIVEN the user has completed at least one previous session (JSONL file in `~/.agent-demo/sessions/`)
- WHEN the user issues `/resume`
- THEN the system loads the most recent session file by mtime
- AND deserializes all entries into the current MessageHistory
- AND the next user turn continues from where the previous session ended

#### Scenario: No previous session
- GIVEN no session files exist in `~/.agent-demo/sessions/`
- WHEN the user issues `/resume`
- THEN the system prints "无历史会话" (no history message)
- AND no error is raised
- AND the REPL continues with an empty history
```

#### 4.3.3 `openspec/changes/add-resume-command/design.md`

```markdown
# Design: Add /resume Command

## Architecture
```
[User: /resume]
    ↓
ChatCommand.handleLine()
    ↓
SlashCommand.dispatch() → /resume 分支
    ↓
SessionStore.loadLatest() → 最新 JSONL → 反序列化为 List<SessionEntry>
    ↓
MessageHistory.replaceAll(entries → Message records)
    ↓
AgentLoop.setHistory(newHistory)
```

## Files Changed
- `src/main/java/.../session/SessionStore.java`: 加 `loadLatest()` 方法
- `src/main/java/.../cli/SlashCommand.java`: 加 `/resume` case
- `src/main/java/.../agent/MessageHistory.java`: 加 `replaceAll(List<Message>)` 方法
- `src/test/java/.../session/SessionStoreTest.java`: 加 loadLatest 测试
- `README.md`: 增补 /resume 命令说明

## Key Design Decisions
- **复用现有 SessionStore 反序列化逻辑**（v0.1 已实现 `flush` + `load` 框架）
- **history 替换而非合并**：避免双 session 数据混淆（Q10 决议）
- **mtime 排序**：用文件 mtime 而非文件名（更鲁棒）
```

#### 4.3.4 `openspec/changes/add-resume-command/tasks.md`

```markdown
# Tasks: Add /resume Command

- [ ] T1: SessionStore.loadLatest() — 扫描 sessions/ 找 mtime 最大的 .jsonl 并反序列化
- [ ] T2: SlashCommand /resume 分支 + ChatCommand 集成
- [ ] T3: MessageHistory.replaceAll() + AgentLoop 切换
- [ ] T4: SessionStoreTest + SlashCommandTest + ChatCommand 集成测试
- [ ] T5: 跑 mvn test + mvn verify（覆盖率门禁）+ 修 README
- [ ] T6: git add + commit + push + 验证 github 远端
```

### 4.4 DSH 集成

DSH 集成路径（OpenSpec 1.5+ 推测，未验证）：
- Skills 写入 `.dsh/skills/openspec-propose/SKILL.md` 等 5 个
- Commands 写入 `.dsh/commands/opsx/propose.md` 等

**降级方案**：如果 OpenSpec init 不识别 DSH 工具名，手动：
- 把生成的 skills 从 `.claude/skills/` 复制到 `.dsh/skills/`
- DSH 应该自动发现（与 Claude Code 相同的 SKILL.md 规范）

## 5. 测试策略

- `openspec validate add-resume-command` 必须通过
- 完整 4 artifact 链（proposal + specs + design + tasks）必须存在
- T5 后 `mvn test` 全绿 + `mvn verify` 覆盖率门禁通过
- 至少 3 个新单元测试（SessionStore.loadLatest / SlashCommand /resume / MessageHistory.replaceAll）

## 6. 风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| `npm install -g` 失败（无 nodejs / 权限） | 整个安装失败 | 提前 `node -v` 验证；用 `corepack` 备用 |
| `openspec init` 不识别 DSH | Skills 装错位置 | 手动从 `.claude/skills` 复制到 `.dsh/skills` |
| 示例 change 写得不好成为 v0.2 阻碍 | 中等 | T1 完成后立即 `openspec validate`；如有问题就 `openspec archive` 后重写 |
| 现有 design.md 与 OpenSpec 冲突 | 低 | OpenSpec 走"行为契约"（What/Scenario），design.md 走"技术细节"（How），互补不冲突 |

## 7. 交付物

- `package.json` 留 npm 全局，无项目级 package.json（避免污染 Maven 项目）
- `openspec/` 整个目录入 Git
- `.dsh/skills/openspec-*` 入 Git
- 1 个 commit：chore(tooling): install OpenSpec + init + add-resume-command example

## 8. 回退方案

如果 OpenSpec 与当前 v0.1 工作流冲突，方案：
- `openspec init` 生成的目录不进 Git（修改 `.gitignore`）
- 仅用 OpenSpec CLI 作为本地 spec 工具，不影响 v0.1 已完成的 92 个测试
- 设计文档继续用 `docs/design.md`（唯一真相源）

---

## 决策点（请用户确认）

1. ✅ **AI 工具选 DSH**（不是 Codex）—— 已确认
2. **示例 change 选 `add-resume-command`** —— plan §15 v0.2 第一个目标，相对独立
3. **profile 选 expanded**（多装一些 slash command）—— 灵活
4. **不进 Git 的话回退成本**：0（OpenSpec 是叠加层，不影响 v0.1）

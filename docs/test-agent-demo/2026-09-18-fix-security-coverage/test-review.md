# 测试过程复盘 — fix-security-coverage

- 批次目录：`docs/test-agent-demo/2026-09-18-fix-security-coverage/`

## 1. 流程回顾

| 阶段 | 动作 |
|------|------|
| 接单 | 用户从三个候选选 C：修 main 上唯一的 jacoco 违规（`security` 包 branches 0.63） |
| 定位 | 全仓 grep 找到 `HomePathGuard` + `TrustedHostFilter` 两类；读 jacoco csv 锁定 `TrustedHostFilter` 是被拉低的那极（0.59 vs HomePathGuard 0.77） |
| 设计 | 6 条新测试覆盖 `TrustedHostFilter` 6 组未覆盖分支；`HomePathGuard` 已过阈值不动 |
| 实施 | 改 `TrustedHostFilterTest` 加 1 个 helper + 6 条测试 |
| 验证 | `mvn verify` 全绿 + jacoco.csv 数字确认 |
| 收尾 | 四件套 → 提交 → merge → 复验 → push → 清理 |

## 2. 问题与根因

| 问题 | 根因 | 处置 |
|------|------|------|
| `TrustedHostFilter` branches 0.59 | 项目 v0.1 早期写入时只覆盖 happy path（loopback / 192.168.1.0/24），HTTPS 兜底与边界条件（null/空白/127.x 范围/非 24 位 CIDR）从未测过 | 6 条新测试精确覆盖 |
| 第一次门禁跑 `WebIntegrationTest.rootServesIndexHtml` 红 | worktree 的 `agent-web/src/main/resources/static/` 没有——前端构建产物为 gitignored，merge 时主工作区有但 worktree 没有 | 把 main 的 static 复制进 worktree |

## 3. 做得好的

1. **先读 jacoco csv 锁定真问题**。没凭印象加测试，先看 csv 知道 47/80 的 33 个未覆盖分布，再做 6 条精准覆盖——避免加 20 条测了 5 个无关分支。
2. **`WebProperties` 现有 compact constructor** 自动把 null 标准化为空集合，加测试时发现这条「自动救」可能让 `isTrusted` 的 null 分支永远跑不到，于是绕过去直接 `new WebProperties(..., null, ...)`——把防御性分支与 happy-path 分支都覆盖。
3. **`HomePathGuard` 已 0.77 不动它**。克制不在「已过阈值的类」上做无意义补强——报告里如实标「该类已 0.77」即可。

## 4. 可改进的

1. **C 的范围一开始没拿 jacoco csv 量化**。先估了「补点测试就行」，实测后发现是单一类（TrustedHostFilter）拖后腿。后续同类需求，先读 csv 再估工作量。
2. **javadoc 没补**。每条新测试的 javadoc 只写了「为什么」（绑定到分支名），没写「业务语义」。可读性可补强。

## 5. 对标本项目的工程约束

| 约束 | 落实 |
|------|------|
| `§2.2` TDD | jacoco csv 是「事实」，新测试是「逼近事实」 |
| `§2.5.5` 测试用例补全豁免 OpenSpec | 本 change 无 OpenSpec 目录 |
| `§2.7` 分支隔离 | worktree `.worktrees/fix-security-coverage` |
| `§2.7.4` 显式路径 | `git add` 4 个文件路径 |
| `§2.7.5.1` 门禁 5 | 合并前已验证 `security` 违规不再出现（jacoco 0.755 ≥ 0.70） |
| `§2.6` 四件套 | 本目录四件齐备 |
| 全局 `§10` | 数据隔离在 D change 已兜底；本 change 未写真实数据 |

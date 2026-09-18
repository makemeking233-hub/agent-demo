# 测试报告 — fix-security-coverage

- 批次目录：`docs/test-agent-demo/2026-09-18-fix-security-coverage/`
- 执行日期：2026-09-18
- 分支：`fix/security-coverage`（worktree `.worktrees/fix-security-coverage`）

## 1. 执行结果

| 项 | 合并前 main (`f840d5d`) | 本分支 | 判定 |
|----|---|---|:--:|
| agent-core | 528 / 0 | **528 / 0** | ✅ |
| agent-web | 373 / 0 | **379 / 0**（+6 新增 security 测试） | ✅ |
| jacoco `security` 包 | **branches 0.63 < 0.70**（违规） | **branches 0.755 ≥ 0.70** | ✅ |
| jacoco `mvn verify` | BUILD FAILURE（仅该违规） | **BUILD SUCCESS** | ✅ |
| `TrustedHostFilterTest` | 8/0 | **14/0**（+6 TF-01..06） | ✅ |

## 2. 按类的覆盖率变化

| 类 | 合并前 branches | 本分支 branches | 变化 |
|----|:--:|:--:|:--:|
| `HomePathGuard` | 23/30 = 0.767 | 23/30 = 0.767 | — |
| `TrustedHostFilter` | 47/80 = 0.59 | **60/80 = 0.75** | +13 分支（+0.16） |
| `HomePathGuard.ResolvedPath`（record） | n/a | n/a | — |
| `HomePathException`（record） | n/a | n/a | — |
| **包合计** | **70/110 = 0.636** | **83/110 = 0.755** | **+0.119** |

## 3. 新增 6 条用例执行明细

| 用例 | 结果 | 覆盖分支 |
|------|:--:|----------|
| TF-01 httpsLocalhostAllowedEvenIfNotInTrustedList | ✅ | L60 复合短路真、`isHttpsLocalhost` 4 项 OR |
| TF-02 httpsEnabledButRemoteNotLocalhostFallsThroughToTrustedCheck | ✅ | L60 复合短路假分支 |
| TF-03 nullTrustedHostsListDeniesAllNonLoopback | ✅ | `isTrusted` 的 `null \|\| isEmpty()` 真 |
| TF-04 loopback127xRangeMatchViaStartsWithPrefix | ✅ | `isLoopback` 的 `startsWith("127.")` 真分支（精确匹配分支短路之前未触发） |
| TF-05 trustedHostsEntryWithOnlyWhitespaceSkippedNotMatched | ✅ | `isTrusted` 的 `rule = rule.trim(); rule.isEmpty()` 真 |
| TF-06 cidr25PrefixMatchAndMissCoverRestBitsBranch | ✅ | `matchIpv4Cidr` 的 `restBits > 0 && fullBytes < 4` 真分支及掩码比对 |

## 4. 缺陷清单

### 4.1 被测缺陷（已修）

| # | 缺陷 | 严重度 |
|:--:|------|:--:|
| C-1 | `TrustedHostFilter` branches 0.59 < 0.70，33 个未覆盖分支集中在 HTTPS 兜底、null 信任列表、CIDR `/25` 掩码、`startsWith("127.")`、空白规则 trim | 🟡 中（仅阻塞 `mvn verify`） |

### 4.2 未完成项（如实记录）

| # | 项 | 状态 |
|:--:|------|------|
| U-1 | `HomePathGuard` 的 `toRealPath` IOException catch（构造器 L37、`resolveMissingForMkdir` L126、`resolveWithinHome` L92） | 未覆盖。需 OS 特定路径（如 Windows NUL）或符号链接环才能可靠触发 |
| U-2 | `isUnderHome` POSIX 分支 | 未覆盖。Windows 测试机上 `os.name` 含 "win"，短路到 Windows 分支 |
| U-3 | `isHttpsLocalhost` / `isLoopback` / `resolveRemote` 的 null 输入分支 | 未覆盖。上游保证非 null |
| U-4 | `ipv4ToBytes` 的非法 IPv4 字符串分支 | 未覆盖 |

U-1..U-4 在 `HomePathGuard` 已 0.77 / `TrustedHostFilter` 已 0.75 的情况下，**对消除「main 上唯一剩余的 jacoco 违规」无影响**。

## 5. 数据清理（全局规则 §10）

- worktree 的 `target/test-home`、`target/test-data*`、`target/site/jacoco` 等均位于 gitignore 的 `target/` 内
- 本次测试无 `~/.agent-demo` 写入（D change 已兜底）：用 `cwd` 字段核对 65 个真实 session 文件未变

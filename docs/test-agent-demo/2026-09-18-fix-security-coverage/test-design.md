# 测试设计文档 — fix-security-coverage

- 批次目录：`docs/test-agent-demo/2026-09-18-fix-security-coverage/`
- 测试日期：2026-09-18
- 对应分支：`fix/security-coverage`

## 1. 测试范围

### 1.1 被测问题

合并前 main HEAD `f840d5d` 在 `mvn verify` 上唯一遗留的 jacoco 违规：

| 包 | 违规 | 说明 |
|----|------|------|
| `com.example.agent.web.security` | branches covered 0.63 < 0.70 | main 上唯一的 jacoco 失败，多次记录放行 |

包内两类：

| 类 | 旧 branches | 说明 |
|----|------|------|
| `HomePathGuard` | 23/30 = 0.77 | 已达阈值，无须新增 |
| `TrustedHostFilter` | 47/80 = 0.59 | 是被拉低的那一极；33 个未覆盖分支集中在 HTTPS-localhost 兜底、null 信任列表、CIDR `/25`（restBits 分支）、`startsWith("127.")`、空白规则 trim 等 |

### 1.2 本次覆盖的行为

| # | 行为 | 期望 |
|:--:|------|------|
| B1 | HTTPS profile + localhost 远端 + 不在 trusted | 放行（走 https 兜底） |
| B2 | HTTPS profile + 非 localhost 远端 | 不走 https 兜底，按 trusted 判断 |
| B3 | `trustedHosts == null`（绕过 compact ctor 的 null 标准化后） | 走 isTrusted 的 deny 分支 |
| B4 | `127.0.0.99` / `127.255.255.254` | 走 `isLoopback` 的 `startsWith("127.")` 分支（不是精确匹配） |
| B5 | 信任列表里只有空白字符的条目 `"  "` | trim 后为空 → 跳过；非 loopback 远端被拒 |
| B6 | CIDR `/25` 命中（`192.168.1.50`）与不命中（`192.168.1.200`） | 走 `restBits > 0 && fullBytes < 4` 的真分支 |

### 1.3 不在范围内

- `HomePathGuard` 的 IOException catch 分支（`toRealPath` 抛 IOException）——需要触发 OS 特定异常，跨平台写测试得不偿失；且该类已 0.77，单独提一票风险不大。
- `isUnderHome` 的 POSIX 分支（Windows 测试机上 `os.name` 含 "win"，短路的 Windows 分支被覆盖；POSIX 分支需要 Linux CI 才能覆盖）。
- `resolveRemote` / `isLoopback` / `isHttpsLocalhost` 的 `ip == null` 分支——上游路径保证 ip 非 null。
- `ipv4ToBytes` 的 `parts.length != 4` / `v < 0 || v > 255` / NumberFormatException 分支——需要构造非法输入，目前测试 fixtures 全是合法 IPv4。

## 2. 设计

不重构 `isUnderHome` / `isHttpsLocalhost` 等私有方法为可注入参数（避免越界改动 production 代码）；用 6 条新测试覆盖**可达的**最高权重分支。每次新增一条断言：**布分支**或**空集合**这类「不命中」的反向断言，**必须**存在——否则「全跑了」与「全跑了并覆盖了边界」无法区分。

## 3. DoD

- [ ] 新增 6 条测试全绿
- [ ] `mvn verify` 在干净 worktree 上无 jacoco 违规
- [ ] `TrustedHostFilter` branches ≥ 0.70
- [ ] security 包整体 branches ≥ 0.70
- [ ] 全量回归（528 core + 379 web）全绿

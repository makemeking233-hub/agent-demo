# 测试用例 — fix-security-coverage

- 用例来源：`test-design.md`；结果：`test-report.md`

## TF — TrustedHostFilterTest 新增 6 条

| 编号 | 前置 | 步骤 | 预期 | 优先级 |
|:--:|------|------|------|:--:|
| TF-01 | https=true，trusted=空 | remote=127.0.0.1 | 走 HTTPS-localhost 兜底 → 放行（status=null） | P0 |
| TF-02 | https=true，trusted=192.168.1.0/24 | remote=10.0.0.5 | HTTPS 分支 false → 走 trusted 检查 → 403 | P0 |
| TF-03 | trusted=null（绕过 compact ctor 的 null 标准化） | remote=192.168.1.42 | `isTrusted` 的 null 分支 → 403 | P0 |
| TF-04 | trusted=空 | remote=127.0.0.99 / 127.255.255.254 | `isLoopback` 的 `startsWith("127.")` 分支 → 放行 | P1 |
| TF-05 | trusted=List.of("  ") | remote=192.168.1.42 | 空白规则 trim 后空 → 跳过 → 403 | P1 |
| TF-06 | trusted=192.168.1.0/25 | remote=192.168.1.50（命中）/ 192.168.1.200（不命中） | `/25` 的 `restBits > 0` 真分支：命中放行，不命中 403 | P0 |

## 反向断言

- TF-03：trusted=null 与空列表必须行为一致（403）；不能因「空列表短路」而跳过 deny 分支。
- TF-05：空白规则被 trim 后不能错误地匹配任意 IP。
- TF-06：CIDR `/25` 与 `/24` 是不同分支；用 `/25` 才能覆盖 `restBits > 0` 那条。

# 测试用例：web 会话切换 Selenium 自动化验证

> 批次目录：`2026-09-02-session-switch-selenium/`
> 用例表复用 `test-design.md` §4（编号 S1-S4），此处含落地情况与实际结果。

## 1. 用例明细与落地

| 编号 | 用例 | 关键步骤 | 预期 | 优先级 | 落地点 | 结果 |
|:----:|------|---------|------|:------:|--------|:----:|
| S1 | 会话列表非空 | 打开 `http://127.0.0.1:18080/`，等 2s，数 `aside button[class*='item']` | 侧边栏渲染 ≥1 个会话项（真实会话） | P0 | `web_session_switch_test.py` | ✅ PASS |
| S2 | 点击会话加载历史 | 找到标题含 `hi` 的会话项并点击，等待对话区出现 `hi` | 对话区加载出该会话首条 user 消息 | P0 | 同上 | ✅ PASS |
| S3 | 切换到另一会话 | 找到 `go` 会话项点击，等待对话区出现 `go` | 对话区切换为 `go` 历史 | P0 | 同上 | ✅ PASS |
| S4 | 切换生效（对话区随会话更新） | 完成 S2/S3 后断言 | 对话区随所选会话更新，不残留上一会话主导 | P0 | 同上 | ✅ PASS |

## 2. 落地脚本

- `tools/web_probe_session.py`：DOM 探查（会话列表项结构、数量）。
- `tools/web_session_switch_test.py`：会话切换主测试（S1-S4）。
- 两者在 `tools/`，已被 `.gitignore` 忽略（本地临时验证工具，不进 git）。

## 3. 执行结果

```text
PASS - 会话列表非空（60 个）
PASS - 找到目标会话1: hi
PASS - 点击会话[hi]后对话区加载出历史消息
PASS - 切换到会话[go]后对话区更新
PASS - 对话区随会话切换更新
```

全部 5 条 PASS（含 S1-S4 对应断言 + 辅助断言），无 FAIL。

> 说明：控制台输出的中文乱码（如 `���ѽ`）为 Windows GBK 控制台显示问题，页面实际文本正确（PASS 标记为准）。

## 4. 数据快照

- 后端 `GET /api/sessions` 返回约 60 个会话（含 `hi`/`go`/`你是谁` 等可辨识标题，与会话存档一致）。
- 会话项 DOM：`aside button[class*='item']`，CSS Module 类如 `_item_uneeb_77`。

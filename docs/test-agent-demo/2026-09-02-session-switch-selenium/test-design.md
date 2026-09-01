# 测试设计：web 会话切换功能 Selenium 自动化验证

> 批次目录：`2026-09-02-session-switch-selenium/`
> 执行日期：2026-09-02
> 关联 change：`add-session-switch`（web 侧边栏真实会话列表 + 点击切换加载历史）

## 1. 测试范围与目标

### 1.1 背景

`add-session-switch` change 修复了「web 前端侧边栏点不同会话切换不了」的问题——之前侧边栏是硬编码占位（`PLACEHOLDER_SESSIONS`），`App` 的 `currentSessionId` 未传入 `ChatPanel`，点击只改高亮、不加载对应会话历史。change 通过 `GET /api/sessions`（真实会话列表） + 前端切换加载历史，实现会话切换。本测试用 python + Selenium 做端到端验证。

### 1.2 测试目标

在**真实运行的后端**（`http://127.0.0.1:18080`）上，用 Selenium 打开 web 页面，验证：

1. 侧边栏展示**真实会话列表**（非硬编码占位）。
2. 点击某个会话后，侧边栏切换高亮 + **对话区加载该会话历史**。
3. 点击另一个会话，对话区**随会话切换更新**。

### 1.3 不在范围

- 不验证新建/删除会话（本 change 不涉及）。
- 不验证真实 LLM 回复内容（切换验证聚焦历史加载）。
- 不跑完整 `mvn verify`（本测试是针对会话切换功能的专项 UI 验证）。

## 2. 测试环境

| 项 | 值 |
|----|-----|
| Web 后端 | `java -jar agent-web.jar --server.port=18080`（用户启动，跑最新代码） |
| 页面地址 | `http://127.0.0.1:18080/` |
| 浏览器 | Chrome 151.0.7922.175（chromedriver 由 webdriver-manager 自动匹配 151.0.7922.138） |
| 工具 | python 3.12.6 + selenium 4.48 + webdriver-manager 4.1.2（清华镜像安装） |
| 会话数据 | 后端 `sessions/*.jsonl` 真实存档（`/api/sessions` 返回约 60 个） |

## 3. 测试策略

- **黑盒端到端**：通过 Selenium 驱动真实 Chrome 访问 web 页，从 UI 层面验证会话列表渲染与切换行为。
- **断言方式**：点击会话项（`aside button[class*='item']`）后，轮询对话区（`.list`）文本，断言出现该会话首条 user 消息（title 关键词），证明历史被加载与切换生效。
- **数据挑选**：从会话列表选两个标题可辨识（如 `hi` / `go`）的会话，避免误匹配。
- **驱动管理**：`webdriver-manager` 自动下载匹配 Chrome 版本的 chromedriver，规避手动版本对齐。

## 4. 用例矩阵

| 编号 | 用例 | 前置 | 预期 | 优先级 |
|:----:|------|------|------|:------:|
| S1 | 会话列表非空 | 后端 `/api/sessions` 有数据 | 侧边栏渲染 ≥1 个会话项 | P0 |
| S2 | 点击会话加载历史 | 会话 [hi] 存在且历史非空 | 对话区出现该会话首条消息 | P0 |
| S3 | 切换到另一会话 | 会话 [go] 存在 | 对话区切换为 [go] 历史 | P0 |
| S4 | 切换生效（会话1 文本不再主导） | 已完成 S2/S3 | 对话区随所选会话更新 | P0 |

## 5. 退出标准（DoD）

- S1-S4 全部 PASS（无 FAIL）。
- 观察到会话列表为真实数据（`aside button[class*='item']` 数量 > 1 且含可辨识标题）。
- 后端 health 可达。

## 6. 依赖与限制

- 依赖用户已启动的 web 后端（18080）与真实 `sessions/*.jsonl`。
- 若后端未启动 / `/api/sessions` 为空，S2-S4 无有效会话可选，标记环境前置不满足（非功能缺陷）。
- 控制台中文乱码为 Windows GBK 显示问题，不影响断言（PASS 标记为准）。

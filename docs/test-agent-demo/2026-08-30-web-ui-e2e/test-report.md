# agent-demo Web UI 端到端测试 —— 测试报告

> 所属批次：`2026-08-30-web-ui-e2e`
> 类型：③ 测试报告文档
> 测试设计 / 用例：同目录 `test-design.md` / `test-cases.md`
> 测试日期：2026-08-30

---

## 1. 测试结论（TL;DR）

| 维度 | 结果 | 说明 |
|------|------|------|
| 构建 | ✅ 通过 | `mvn -pl agent-web test -DskipNpm=true` BUILD SUCCESS |
| 自动化 E2E | ✅ **17 用例全绿** | `ThemeToggleE2ETest` 3 + `UiLayoutE2ETest` 14 |
| 覆盖 | 🟡 24 条用例落地 17 条（71%），P0 全落地 | 未落地 7 条依赖真实 LLM/SSE 链路 |
| 关键缺陷 | 🟡 E1 `/logs` 404（后端未加回落前缀） | 已修复 |

---

## 2. 测试环境

| 项 | 值 |
|----|-----|
| JDK | 17 |
| 浏览器 | Chrome `151.0.7922.174` |
| WebDriver | chromedriver `151.0.7922.138`（WebDriverManager 自动下载） |
| 框架 | Selenium 4.25（selenium-java）+ WebDriverManager 5.9.2 |
| 被测服务 | `agent-web` 后端 `http://127.0.0.1:18080` |
| 运行命令 | `mvn -pl agent-web test -Dtest='*E2ETest' -DskipNpm=true` |

---

## 3. 执行结果

| 测试类 | 用例数 | 结果 | 覆盖 |
|--------|:------:|:----:|------|
| `ThemeToggleE2ETest` | 3 | ✅ 全绿 | 主题默认态、toggle 切换、刷新持久化 |
| `UiLayoutE2ETest` | 14 | ✅ 全绿 | 三栏布局、顶栏、会话列表/选中/折叠/新建、输入禁用/计数、Ctrl+Enter/Shift+Enter、slash 提示、/help、空占位、SPA 路由回落 |
| **合计** | **17** | **✅ 全绿** | P0 冒烟 + P1 核心布局/交互 |

> 实测输出：`Tests run: 17, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。

### 3.1 覆盖的关键用户链路

三栏布局外壳、主题切换（亮/暗 + 刷新持久化）、会话列表分组/选中/折叠/新建、底部多行输入（Ctrl+Enter 发送 / Shift+Enter 换行 / 空输入禁用 / 字符计数）、slash 命令提示 / /help、空对话占位、SPA 客户端路由回落。

---

## 4. 环境适配过程（复盘点）

| # | 问题 | 解决 |
|:--:|------|------|
| 1 | `msedgedriver` 下载源 `msedgedriver.azureedge.net` 不可达，`ThemeToggleE2ETest` 报 WebDriverManagerException | 改用 **ChromeDriver**（`chrome-for-testing` 源可达，实测 200） |
| 2 | 本机缓存 chromedriver 142 与 Chrome 151 不匹配 | WebDriverManager 自动下载匹配的 `chromedriver 151.0.7922.138` |
| 3 | `npm run build` 报 `EPERM unlink esbuild.exe`（vite/esbuild 进程锁文件） | 终止 vite/esbuild 残留进程；构建用 `-DskipNpm=true` |
| 4 | `agent-web` 测试编译报 `找不到符号 SessionLogSink`（多模块依赖未解析） | 先 `mvn -pl agent-core install`，再构建 agent-web |
| 5 | Maven 3.6.1 对 `-De2e.web.base=http://...` 解析异常 | `E2EBase` 用内置默认值 `WEB_BASE=http://127.0.0.1:18080`，不依赖命令行传 URL 属性 |

---

## 5. 发现的缺陷与遗留问题

| # | 问题 | 严重级 | 状态/建议 |
|:--:|------|:------:|------|
| E1 | `/logs` 路由返回 404（SPA 客户端回落未加 `/logs` 前缀） | 🟡 | **已修复**（提交 `d08d910`）；但前端 `/logs` 页渲染本身未实现 |
| E2 | Composer 发送按钮无 `aria-label`（仅 CSS module hash 类名） | 🟢 | 已用 XPath sibling 定位绕过；建议前端补 `data-testid` |
| E3 | 本机 chromedriver 缓存 142 与 Chrome 151 不匹配 | 🟢 | 已由 WebDriverManager 联网下载匹配版解决；CI 应预置匹配 driver |
| E4 | `agent-web/pom.xml` 残留临时 `testExcludes`（跳过 3 个测试） | 🟡 | 已确认移除，3 个测试恢复通过（agent-core install 后） |

---

## 6. 覆盖率对照

本批 24 条用例中 **落地 17 条（71%）**，P0 全落地（5 条）。未落地 7 条：

- **依赖真实 LLM/SSE**：助手消息渲染(018)、markdown 渲染(019)、abort 按钮出现(020)/恢复(021)——无 API key 时后端 degraded，无法稳定触发完整对话流。
- **API 层**：健康检查(022)——由后端集成测试覆盖。
- **前端未实现**：日志查看页(023)——本轮仅补后端回落，前端 `/logs` 页渲染未实现。

--- 

---

## 6.5 LLM 回复链路验证（附加，2026-08-30）

本批 E2E 仅覆盖 UI 静态链路；本节补充验证「发送 → 模型调用 → SSE 回复」的真实端到端链路（此前因无 key 留待有 key 环境，现已验证）。

**前置**：web 启动需激活 `local` profile 以读到 `application-local.yml` 的 key：

```bash
mvn -pl agent-web spring-boot:run -Dspring-boot.run.profiles=web,local
# IDEA 运行配置加: --spring.profiles.active=web,local
```

**验证结果（web,local 启动 + key 就位）**：

| 步骤 | 结果 |
|------|------|
| `GET /api/health` | ✅ `200 {"status":"ok"}`（不再 degraded） |
| `POST /api/chat/send` | ✅ `200 + stream_id` |
| `GET /api/chat/stream/{id}` | ✅ SSE 返回 `message_start` → `message_delta`（真实中文回复）→ `message_stop` |

**结论**：基本读写链路正常，模型可被调用并返回回复。**前提是激活 `local` profile 读到 key**。

> ⚠️ 关联缺陷：`HealthController.isProviderConfigured()` 原只检查 `DEEPSEEK_API_KEY` 环境变量，导致即使 `application-local.yml` 填了 key 也误报 `degraded/provider_not_configured`。已修复为与 `WebRuntimeConfig` 同一 key 优先级（env → `agent.provider.api-key`）。见提交。

---
## 7. 结论与建议

本次前端 UI E2E 测试**跑通了关键用户链路**（17 用例全绿），验证了三栏布局、主题切换、会话交互、输入与 slash 命令、SPA 路由回落等核心行为，并**发现并修复了 `/logs` 404** 缺陷。

**下一步建议**：
1. 在有真实 API key 的环境补「完整对话流」E2E（SSE 渲染、abort、工具/权限卡片、markdown）。
2. 前端补 `/logs` 路由与发送按钮 `data-testid`，提升可测性与功能完整性。
3. CI 预置与 Chrome 匹配的 chromedriver，避免联网下载失败。

> 详细复盘见同目录 `test-review.md`。


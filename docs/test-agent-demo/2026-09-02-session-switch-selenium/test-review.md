# 测试过程复盘：web 会话切换 Selenium 自动化验证

> 批次目录：`2026-09-02-session-switch-selenium/`
> 复审时间：2026-09-02

## 1. 流程回顾

1. 确认后端 `http://127.0.0.1:18080` health 可达 + `GET /api/sessions` 返回真实会话（约 60 个）。
2. 检查 python/selenium 环境：发现 selenium 未装，尝试安装（官方源 SSL 失败 → 清华镜像成功）。
3. 编写/运行 DOM 探查脚本（`web_probe_session.py`）：确认会话项为 `aside button[class*='item']`，60 个。
4. 编写/运行切换主测试（`web_session_switch_test.py`）：S1-S4，全部 PASS。
5. 按 AGENTS.md §2.6 落四件套到 `docs/test-agent-demo/2026-09-02-session-switch-selenium/`。

## 2. 问题与解决

### 2.1 python 无 selenium，pip 官方源安装失败（SSL EOF）
- **现象**：`pip install selenium webdriver-manager` 报 `SSLError(SSLEOFError)`，源 `files.pythonhosted.org` 连不通。
- **根因**：网络到 pythonhosted 不可达（与 git fetch 的 `Connection reset` 同类网络问题）。
- **解决**：改用清华镜像 `-i https://pypi.tuna.tsinghua.edu.cn/simple`，成功安装 selenium 4.48 + webdriver-manager 4.1.2。

### 2.2 chromedriver 版本不匹配
- **现象**：本地 chromedriver（142.0.7444.175）驱动 Chrome 151 时，Chrome 启动崩溃（控制台输出 0x 地址）。
- **根因**：chromedriver 142 与 Chrome 151 版本差过大。
- **解决**：改用 `webdriver-manager` 自动下载匹配版（151.0.7922.138），驱动正常。

### 2.3 控制台中文乱码
- **现象**：PASS/FAIL 旁的中文（如 `���ѽ`）显示乱码。
- **根因**：Windows 控制台 GBK 编码显示 UTF-8 中文。
- **解决**：不影响断言——以 `PASS`/`FAIL` 标记为准；页面实际文本正确。

## 3. 做得好的

- 分层验证：先 DOM 探查确认元素结构，再写主测试，降低盲改风险。
- 环境快速适配：官方源失败迅速切清华镜像；chromedriver 不匹配由 webdriver-manager 自动解决。
- 断言聚焦 UI 可观察行为（对话区文本），不依赖真实 LLM 回复内容（不可控）。

## 4. 可改进

- 测试脚本用 CSS Module hash 类（`_item_*`），对类名抖动敏感；可改用 `data-testid` 或语义选择器，未来更稳。
- 会话挑选用标题关键词（`hi`/`go`），若会话 title 重复可能误选；可增强为按 index 或精确 id。
- 未在 CI 固化该 Selenium 测试（依赖已启动后端）；后续可并入 `E2EBase` Java 体系或做成可选 profile。

## 5. 交付物

| 文件 | 说明 |
|------|------|
| `docs/test-agent-demo/2026-09-02-session-switch-selenium/test-design.md` | 测试设计 |
| `docs/test-agent-demo/2026-09-02-session-switch-selenium/test-cases.md` | 用例输出（含实际 PASS 结果） |
| `docs/test-agent-demo/2026-09-02-session-switch-selenium/test-report.md` | 测试报告 |
| `docs/test-agent-demo/2026-09-02-session-switch-selenium/test-review.md` | 过程复盘 |
| `tools/web_probe_session.py` / `tools/web_session_switch_test.py` | 自动化脚本（tools/，gitignore） |

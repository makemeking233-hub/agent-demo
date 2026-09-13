# 测试设计：add-models-dropdown-v0

## 1. 范围

本批次测试覆盖 OpenSpec change `add-models-dropdown-v0` 的所有 49 个 task 实施落地情况，重点验证：

1. 后端 `AgentLoop.reasoningEffort` 透传到 ChatRequest.extra
2. 三 Provider（OpenAI / Anthropic / DeepSeek）正确处理 `reasoning_effort`
3. Web 后端 `SendRequest.reasoningEffort` 透传到 AgentLoop
4. `ModelsResponse.reasoningEfforts` 数组正确返回
5. CLI `/effort <low|medium|high>` 命令
6. 前端 ModelSelect + ReasoningEffortSelect + Dropdown 组件
7. localStorage 持久化 + 跨会话沿用

## 2. 测试目标

| ID | 目标 | 验收标准 |
|---|---|---|
| G1 | 后端 reasoningEffort 完整数据流 | Java 单元测试覆盖从 `AgentLoop.setReasoningEffort()` → `ChatRequest.extra` → Provider 解析全链路 |
| G2 | 三 Provider effort 适配正确 | OAI 透传 / Anthropic 折算 / DeepSeek 忽略，三档测试齐全 |
| G3 | Web 后端 reasoningEffort 透传 | `SendRequest` 字段 → `ChatStreamService.create()` → `AgentLoop.setReasoningEffort()` 全链路 |
| G4 | ModelsResponse.reasoningEfforts 数组 | supported-models 配置变更后下拉选项正确 |
| G5 | CLI `/effort` 命令 | 4 个白名单值 + 非法值，setter 回调正确 |
| G6 | 前端 Dropdown 通用组件 | trigger 渲染 / 点击打开 / 选项选中 / 外部点击关闭 / Esc / 键盘导航 / disabled |
| G7 | 前端 ModelSelect + ReasoningEffortSelect | 拉列表 / 渲染当前 / 选中触发 / 空数组隐藏 |
| G8 | TopBar / Composer 集成 | ModelSelect 在 TopBar 显示；ReasoningEffortSelect 在 Composer 状态栏条件渲染 |
| G9 | localStorage 持久化 | 初始化读 / 写回 / 非法值 fallback |
| G10 | OpenSpec artifacts 完整性 | 4 个 artifacts 全部 validate 通过 |

## 3. 测试环境

| 项 | 详情 |
|---|---|
| Java | JDK 17 + Maven 3.9 |
| Spring Boot | 3.2 |
| 测试框架 | JUnit 5（Jupiter）+ Mockito + Reactor Test + AssertJ |
| 前端 | Node.js + Vite + Vitest + @testing-library/react |
| WireMock | 3.x（集成测试） |
| Profile | `web`（启用 Web 后端） |
| Sandbox | `danger-full-access`（文件 / 网络无限制） |

## 4. 测试策略

| 层级 | 测试类型 | 工具 | 覆盖范围 |
|---|---|---|---|
| L1 | 单元测试 | JUnit + Mockito | AgentLoop / Provider / Mapper / Controller / SlashCommand |
| L2 | 组件测试 | Vitest + @testing-library/react | Dropdown / ModelSelect / ReasoningEffortSelect |
| L3 | 集成测试 | Spring WebTestClient | SendRequest → Controller → Stream |
| L4 | E2E | Selenium（Playwright 待替换） | UI 完整流程（v0.1 既有，已知在沙箱 npm 失败） |

## 5. 用例矩阵

| # | 场景 | 期望 | 优先级 | 自动化 |
|---|------|------|--------|--------|
| TC-01 | AgentLoop 默认 reasoningEffort() == null | null | P0 | ✅ AgentLoopTest |
| TC-02 | AgentLoop.setReasoningEffort("high") 后 getter == "high" | "high" | P0 | ✅ AgentLoopTest |
| TC-03 | OpenAi o1 + extra.reasoning_effort="high" → body 含 "reasoning_effort":"high" | true | P0 | ✅ OpenAiCompatibleMapperTest |
| TC-04 | OpenAi o1 + extra 无 reasoning_effort → body 含 "reasoning_effort":"medium" | true | P0 | ✅ OpenAiCompatibleMapperTest |
| TC-05 | OpenAI gpt-4o + extra.reasoning_effort="high" → body 不含 reasoning_effort | true | P0 | ✅ OpenAiCompatibleMapperTest |
| TC-06 | Anthropic opus-4 + reasoningEffort="low" → body 含 "budget_tokens":1024 | true | P0 | ✅ AnthropicProviderTest |
| TC-07 | Anthropic opus-4 + reasoningEffort="medium" → body 含 "budget_tokens":4096 | true | P0 | ✅ AnthropicProviderTest |
| TC-08 | Anthropic opus-4 + reasoningEffort="high" → body 含 "budget_tokens":16384 | true | P0 | ✅ AnthropicProviderTest |
| TC-09 | ModelsController 默认返回 deepseek-chat/empty + reasoner/三档 | true | P0 | ✅ ModelsControllerTest |
| TC-10 | ModelsController 自定义 supported-models=o1,o1-mini 触发 supportsReasoning | true | P0 | ✅ ModelsControllerTest |
| TC-11 | SendRequest JSON 含 "reasoning_effort":"high" 反序列化 | true | P0 | ✅ ModelsControllerTest + WebIntegrationTest |
| TC-12 | CLI `/effort high` 触发 setter 回调 | true | P0 | ✅ SlashCommandTest |
| TC-13 | CLI `/effort extreme`（非法）不触发 setter | null | P0 | ✅ SlashCommandTest |
| TC-14 | Dropdown trigger 渲染当前 value label | true | P1 | ✅ Dropdown.test.tsx |
| TC-15 | Dropdown 点击外部关闭 | true | P1 | ✅ Dropdown.test.tsx |
| TC-16 | Dropdown Esc 关闭 + 键盘导航 | true | P1 | ✅ Dropdown.test.tsx |
| TC-17 | ReasoningEffortSelect supportsReasoning=false 渲染 null | null | P1 | ❌ 手测（vitest 在沙箱 npm 失败） |
| TC-18 | TopBar 显示 ModelSelect | true | P1 | ❌ 手测 |
| TC-19 | Composer 状态栏条件渲染 ReasoningEffortSelect | true | P1 | ❌ 手测 |
| TC-20 | localStorage 初始化（持久化有效） | true | P1 | ❌ 手测 |
| TC-21 | localStorage 非法 model → fallback deepseek-chat | true | P1 | ❌ 手测 |

## 6. 退出标准（DoD）

- [x] agent-core 单测全绿（418 通过 + 1 skip，0 失败）
- [x] agent-web 单测全绿（170 通过 + 1 skip，0 失败）
- [ ] 前端 vitest 全绿（**沙箱 npm ci 失败，未跑**）
- [ ] playwright/selenium E2E 全绿（**沙箱环境不支持**）
- [x] OpenSpec 4 个 artifacts validate 通过
- [x] worktree 分支 `feat/add-models-dropdown-v0` 已 push 到 origin
- [ ] docs 四件套（test-design / test-cases / test-report / test-review）完成
- [ ] test-guide.md §2.10 登记
- [ ] archive add-models-dropdown-v0 后合并回 main

## 7. 风险与遗留

| 风险 | 缓解 |
|------|------|
| 沙箱 npm ci 失败 → 前端 vitest 未跑 | 沙箱环境问题；下 session 需手动跑 `cd agent-web/frontend && npx vitest run` |
| Sandbox npm 失败 → frontend build 未产出 | `WebIntegrationTest.rootServesIndexHtml` 失败（与我代码无关） |
| `EditFileTool$Input` 增量编译残留 | 与其他 agent 并行 build 同一 target；worktree 隔离后已规避 |
| UiLayoutE2ETest 中文 aria-label 编码问题 | 与本 change 无关；Selenium 浏览器环境 |
| Add-provider-catalog-abstract 未实现 | v0.2 工作；本 change 给出最小可行版本 |

---

**修订记录**：

- v0.1（2026-09-13）：初版（add-models-dropdown-v0）
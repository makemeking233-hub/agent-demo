# `2026-09-04-reasoning-thinking/` — Reasoning Thinking 测试设计

> change: `openspec/changes/add-reasoning-thinking-streaming/`

## 1. 测试目标

把 `deepseek-reasoner` / `OpenAI o1` / `Anthropic Claude extended thinking` 等 reasoning model 的推理过程**真正流到前端**：后端 SSE 增 `message_delta.delta_type="thinking"`、前端 `<ThinkingCollapse />` 折叠展示、thinking 进 history + 单独计费。

## 2. 测试矩阵

| 维度 | 工具 | 覆盖 |
|---|---|---|
| 单元 | vitest | ThinkingCollapse 5 / MessageBubble.thinking 4 = 9 个新 |
| 集成 | vitest | StreamChunkThinkingDelta 5 / DeepSeek 2 / OpenAI 7 / Anthropic 7 = 21 个新 |
| 端到端 | 手动 | `npm run dev` + `mvn spring-boot:run` + 切 `/model reasoning` + 观察折叠区块（v0.x 不写 Playwright） |

## 3. DoD

- [x] 后端 agent-core 343/343 全绿（+21 新测试）
- [x] 后端 agent-web 153/153 全绿（无回归）
- [x] 前端 104/104 全绿（+9 新测试）
- [x] openspec change archived

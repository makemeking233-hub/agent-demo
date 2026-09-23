# align-model-catalog

## Why

`agent-web` 的模型目录是 `application-web.yml` 里手写的静态基线，已经和 DeepSeek 官方
`GET https://api.deepseek.com/models` 漂离开了。2026-09-23 实测官方只返回 **2 个** 模型：

| 官方 id | 显示名 | 上下文 | 最大输出 | 输入模态 | 档位（默认） |
|---|---|---|---|---|---|
| `deepseek-flash` | DeepSeek-V4.1-Flash | 1,048,576 | 393,216 | text, image | low / high / max（high） |
| `deepseek-v4-pro` | DeepSeek-V4-Pro | 1,048,576 | 393,216 | text | low / high / max（high） |

而项目里写着 **3 个** 模型（`deepseek-v4-flash` / `deepseek-reasoner` / `deepseek-v4-pro`），其中：

- `deepseek-v4-flash` 是官方已改名的旧 id（显示名同样停在 `DeepSeek-V4-Flash`，缺 `.1`）
- `deepseek-reasoner` 是**项目虚构的** id，官方根本不存在
- 档位配成 `low / medium / high`——`medium` 不是官方档位，真正的 `max` 反而没有
- `deepseek-v4-flash` 标了 `supports-reasoning: false`，与官方（支持三档）直接矛盾

`agent-core` 侧的常量同样停在旧世代：`DeepSeekProvider.CONTEXT_WINDOW = 128_000`、
`MAX_OUTPUT = 8_192`，`AgentLoop` 每轮硬发 `max_tokens: 8192`。于是**上下文压缩阈值按 128K
计算、每轮输出被压到 8K**，与官方 1M / 384K 的能力差一个数量级——用户在前端选了模型也拿不到
该模型真正的能力。

`DeepSeekProvider` 的类注释还写着「DeepSeek 不接受 `reasoning_effort` 参数」。这条早已不成立：
`AgentLoop.toRequest()` 把 effort 写进 `ChatRequest.extra`，
`OpenAiCompatibleMapper.toRequestBody()` 结尾 `body.putAll(req.extra())`，参数确实到达上游；
注释与代码事实相反，会误导后续改动。

## What Changes

1. `application-web.yml` 的 `agent.chat.providers` 按官方 `/models` 重写为 2 个模型，档位
   `low / high / max`，新增每模型 `default-reasoning-effort: high`；`default-model` 指向
   `deepseek-flash`。
2. 档位真源统一为 `low / high / max`、默认 `high`：`SlashCommand.SUPPORTED_EFFORTS`、
   前端 `lib/model-selection.ts` 的 `DEFAULT_EFFORT`、`ModelSelect` 换模型时的档位兜底。
3. `DeepSeekProvider.CONTEXT_WINDOW` → `1_048_576`、`MAX_OUTPUT` → `393_216`；`AgentLoop`
   每轮 `max_tokens` 由硬编码 8192 改为**当前 provider 的 `maxOutputTokens()`**。
   `ContextCompressor` 阈值已按 `contextWindow() - maxOutputTokens() - buffer` 动态计算，
   常量一改自动跟随，无需改其代码。
4. 旧名彻底删除：`deepseek-v4-flash` / `deepseek-reasoner` / `deepseek-chat` 从目录、
   `AgentConfig.defaults()`、`AgentLoop.DEFAULT_MODEL`、`SlashCommand` 别名表、
   `DeepSeekVoiceCorrectionService` / `DeepSeekWebSearchProvider` 默认模型及注释中移除。
5. 启动期漂移自检：新增只读 WARN 校验，拉官方 `/models` 比对 id 集合与档位，不一致打 WARN
   并列出双向差异；**不阻断启动**，网络失败 / 无 key 时静默跳过。
6. 修正 `DeepSeekProvider` 关于 `reasoning_effort` 的过期注释。

## Impact

- 受影响能力：`provider-catalog`（目录数据、档位、上下文/输出上限）、`cli`（默认模型、
  `/model` 别名、`/effort` 白名单）、`web-ui`（`/api/chat/models` 示例与档位）
- 受影响代码：`application-web.yml`、`ProviderCatalog*` / `ModelCatalog` / `ModelEntry` /
  `ModelsResponse`、`DeepSeekProvider`、`AgentLoop`、`SlashCommand`、`AgentConfig`、
  `DeepSeekVoiceCorrectionService`、`DeepSeekWebSearchProvider`、前端 `App.tsx` /
  `lib/model-selection.ts` / `ModelSelect.tsx`
- 行为变更（BREAKING，用户已确认）：
  - 旧 id 不再是合法模型，请求携带旧 id 会被 400 拒绝（`ChatController.resolveModel` 既有
    fail-closed 逻辑，本次只是让目录不再包含它们）
  - 前端 localStorage 存了旧 id 时由 `resolveModelSelection` 规整到服务端默认模型
  - `medium` 不再合法；每轮 `max_tokens` 从 8192 提到 384K
- 风险：`max_tokens: 393216` 每轮发出，若上游对超限值返回 400 则整体不可用 —— tasks 中安排
  了真实 API 冒烟验证作为放行前置
- 成本 / 延迟：单轮输出上限抬高意味着最坏情况输出成本与耗时显著上升（用户已确认接受）

## Out of Scope

- 前端选择器重构：两层菜单 / 档位下拉已存在，本次只改它消费的数据与默认值
- 把 `context_window` / `max_output_tokens` / `input_modalities` 加进 `ModelEntry` 并在
  UI 展示
- 图片（vision）模态接线：官方给 `deepseek-flash` 标了 `image` 输入，但项目尚无图片上传链路
- 用官方 `/models` 替代静态基线作运行时真源（仍是基线为真源，官方仅用于启动自检）
- `OpenAiCompatibleMapper` 中 OpenAI o 系列的默认 `reasoning_effort: "medium"`
  （那是 OpenAI 自己的档位，与 DeepSeek 无关）
- `AgentConfig.Provider.maxOutputTokens`（8192）：它只喂 `DeepSeekWebSearchProvider` 的
  搜索总结长度，与每轮对话 `max_tokens` 无关

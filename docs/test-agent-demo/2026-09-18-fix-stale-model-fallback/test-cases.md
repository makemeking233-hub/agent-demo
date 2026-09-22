# 测试用例文档 — fix-stale-model-fallback

- 批次目录：`docs/test-agent-demo/2026-09-18-fix-stale-model-fallback/`
- 用例总来源：`test-design.md` §5 用例矩阵；本文件为可独立追溯的全量明细
- 执行结果见 `test-report.md`（本文件只列用例与预期，不含结论）

## 1. 用例编号规则

`<域>-<序号>`：`MC` = ModelCatalog，`MD` = ModelsController / 启动校验，`CR` = ChatController 模型解析，`HT` = HTTP 集成，`LG` = 成功回合日志，`WC` = 微信通道，`FE` = 前端兜底解析。

## 2. 用例明细

### 2.1 MC — `ModelCatalogTest`（按 id 跨 provider 查找）

| 编号 | 前置 | 步骤 | 预期 | 优先级 |
|:--:|------|------|------|:--:|
| MC-01 | 目录含 deepseek(2 model) + openai(1 model) | `modelById("deepseek-v4-flash")` | present，id 相符 | P0 |
| MC-02 | 同上 | `modelById("o1")`（在第二个 provider） | present | P0 |
| MC-03 | 同上 | `modelById("deepseek-chat")` | **empty**（已停用且不在目录） | P0 |
| MC-04 | 同上 | `modelById(null)` | empty，不抛异常 | P1 |
| MC-05 | 空目录 | `modelById("deepseek-v4-flash")` | empty | P1 |
| MC-06 | provider 的 `models` 为 null（yaml 漏写字段） | `modelById("anything")` | empty，**不 NPE** | P1 |
| MC-07 | 两 provider | `modelIds()` | `[deepseek-v4-flash, deepseek-reasoner, o1]`（provider 序→model 序） | P1 |
| MC-08 | 空目录 | `modelIds()` | 空列表 | P2 |
| MC-09 | 首个 provider 的 `models` 为 null | `modelIds()` | 只返回第二个 provider 的 id | P2 |

### 2.2 MD — `ModelsControllerTest`（端点 + 启动校验）

| 编号 | 前置 | 步骤 | 预期 | 优先级 |
|:--:|------|------|------|:--:|
| MD-01 | deepseek provider | `list()` | `providers` 1 项，含 v4-flash，`supportsReasoning=false`、efforts 空 | P0 |
| MD-02 | 同上 | `list()` | reasoner 的 efforts = `[low, medium, high]` | P0 |
| MD-03 | 空目录 | `list()` | `providers` 空 | P1 |
| MD-04 | 同上 | `list()` | 平铺 `models[]` 与 `providers[].models[]` 的 id 集合一致 | P1 |
| MD-05 | deepseek + openai | `list()` | provider 顺序按 yaml 书写序 | P2 |
| MD-06 | `supportsReasoning=false` 但 efforts 非空 | `init()` | `IllegalStateException` | P0 |
| MD-07 | effort 的 id 为空白 | `init()` | `IllegalStateException` | P0 |
| MD-08 | `default-model = deepseek-chat`（不在目录） | `init()` | `IllegalStateException`，消息含该 id 与 `default-model` | P0 |
| MD-09 | `default-provider = minimax`（不在目录） | `init()` | `IllegalStateException`，消息含该 id 与 `default-provider` | P0 |
| MD-10 | `default-model` 存在但属于**另一个** provider | `init()` | `IllegalStateException` | P1 |
| MD-11 | 默认 provider/model 都能匹配 | `init()` | 正常；`defaultModel()` / `defaultProvider()` 返回配置值 | P0 |
| MD-12 | 合法目录 + 默认值 | `list()` | 响应带 `defaultProvider` / `defaultModel`；且 `defaultModel` ∈ `models[]` | P0 |

### 2.3 CR — `ChatControllerModelResolutionTest`（模型解析 / 非法拒绝）

| 编号 | 前置 | 步骤 | 预期 | 优先级 |
|:--:|------|------|------|:--:|
| CR-01 | key 已配、workspace 存在 | `send(model=null)` | 200，`model` = `default-model`，透传值一致 | P0 |
| CR-02 | 同上 | `send(model="   ")` | 200，`model` = `default-model` | P0 |
| CR-03 | 同上 | 兜底路径的透传值 | 该值经 `modelById` 校验后 **present**（兜底值必须真的在目录里） | P0 |
| CR-04 | 同上 | `send(model="deepseek-reasoner")` | 200，回显与透传值均为该值 | P0 |
| CR-05 | 同上 | `send(model="deepseek-v4-pro")` | 响应回显 == 服务端实际使用值 | P1 |
| CR-06 | 同上 | `send(model="deepseek-chat")` | **400**，`error=invalid_model`，`requested` 回显，**无 `stream_id`** | P0 |
| CR-07 | 同上 | 同上响应的 `supported` | 恰为目录中 3 个 id | P0 |
| CR-08 | 同上 | `send(model="deepseek-chat")` 后校验协作者 | `streams.create` / `streams.start` **从未被调用** | P0 |
| CR-09 | 同上 | `send(model="gpt-4o")` | 400，且未创建流（未知模型同样不静默改道） | P0 |
| CR-10 | `content` 为空 + 非法 model | `send()` | 仍返回 `content_empty`（既有校验优先级不变） | P1 |

### 2.4 HT — `ChatSendModelHttpTest`（HTTP 集成 + Spring 装配）

| 编号 | 前置 | 步骤 | 预期 | 优先级 |
|:--:|------|------|------|:--:|
| HT-01 | 真实 Spring 上下文（RANDOM_PORT） | `GET /api/chat/models` | 200；`defaultModel` 存在且 ∈ `models[]` | P0 |
| HT-02 | 同上 | `POST /api/chat/send` 不带 `model` | 200；响应 `model` == `defaultModel` | P0 |
| HT-03 | 同上 | `POST /api/chat/send` 带 `model=deepseek-chat` | **400**；`error=invalid_model`；`supported` 非空；**无 `stream_id`** | P0 |
| HT-04 | 同上 | `POST /api/chat/send` 带 `model=deepseek-reasoner` | 200；响应 `model` 原样回显 | P0 |

> HT 组的额外价值：本次给 `ChatController` / `ModelsController` / `WecomMessageDispatcher` 都加了构造器依赖，
> 只有启动真实上下文才能证明 Spring 装配得起来。

### 2.5 LG — `ChatStreamServiceSuccessObservabilityTest`

| 编号 | 前置 | 步骤 | 预期 | 优先级 |
|:--:|------|------|------|:--:|
| LG-01 | `processTurn` 正常返回 | `start()` 后查日志 | 出现 INFO，含 `turn completed` 与 `stream=` / `session=` / `workspace=` / `model=` | P0 |
| LG-02 | 同一服务上先成功回合、后失败回合 | 查两条日志 | 两条都含同一组四个键名，且 `model=` 值一致（成功/失败对称） | P1 |

### 2.6 WC — `WecomMessageDispatcherModelTest`

| 编号 | 前置 | 步骤 | 预期 | 优先级 |
|:--:|------|------|------|:--:|
| WC-01 | 配置默认模型 = `deepseek-v4-pro` | 派发一条 text 事件 | `streams.create` 收到的 model == 配置默认值，且 **≠ deepseek-chat** | P0 |
| WC-02 | 同上目录 | 直接查目录 | 配置默认值 present；`deepseek-chat` **empty** | P1 |

### 2.7 FE — `model-selection.test.ts`（前端兜底解析）

| 编号 | 前置 | 步骤 | 预期 | 优先级 |
|:--:|------|------|------|:--:|
| FE-01 | 目录 3 项 | 历史选择 `deepseek-reasoner` | 保留该值与 effort `high` | P0 |
| FE-02 | 同上 | 历史选择 `deepseek-chat`（已下线） | 回落到服务端 `defaultModel` | P0 |
| FE-03 | 同上 | 无历史选择 | 用服务端 `defaultModel` | P0 |
| FE-04 | 旧版后端不返回 `defaultModel` | 无历史选择 | 退到 `models[0]` | P1 |
| FE-05 | 同上 | 遍历 4 个非法/空历史值 | 解析结果**永远落在目录内** | P0 |
| FE-06 | 服务端默认值不在目录中 | 无历史选择 | 退到 `models[0]` | P1 |
| FE-07 | 空目录 + 有默认值 | 历史选择 `deepseek-chat` | 沿用服务端声明的默认值，`entry` 为 null | P1 |
| FE-08 | 空目录 + 无默认值 | 历史选择 `deepseek-chat` | 返回空串（= 未指定） | P1 |
| FE-09 | 目录 3 项 / 空目录 | 遍历历史值组合 | 返回的 id 只可能来自「目录 ∪ 服务端默认值 ∪ 空串」，**不许凭空发明 id** | P0 |
| FE-10 | reasoner 支持三档 | 历史 effort = `ultra`（不在档位内） | 回落到该模型首档 `low` | P1 |
| FE-11 | v4-flash 不支持 reasoning | 历史 effort = `high` | 回落到 `medium` | P1 |

## 3. 反向断言清单

本次缺陷的特点是「静默降级」，所以**只断言正向结果会漏掉它**。以下用例断言「不该发生的事没发生」：

| 用例 | 反向断言 |
|------|----------|
| MC-03 | `deepseek-chat` 必须查不到 |
| CR-08 | 非法 model 时 `streams.create` 从未被调用 |
| CR-09 | 未知模型同样不创建流 |
| CR-03 | 兜底值必须真的在目录里（不能是第二个非法常量） |
| WC-01 | 实际下发的 model ≠ 硬编码 id |
| FE-02 / FE-05 / FE-09 | 前端不得发出目录外的 id |
| MD-08 / MD-09 | 默认值非法时启动必须失败，而不是带病运行 |

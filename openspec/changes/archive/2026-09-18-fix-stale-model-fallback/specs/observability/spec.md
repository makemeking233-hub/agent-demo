## ADDED Requirements

### Requirement: 回合结果上报成功与失败对称

回合执行 SHALL 在成功与失败两条路径上都留下含同一组关联字段的日志记录。成功回合 SHALL 记录一条 INFO，失败回合 SHALL 记录一条 ERROR，两者的字段集合 SHALL 一致，至少包含 `streamId` / `sessionId` / `workspace` / **模型名**。

理由：若只有失败路径记录模型名，则「前端显示/选择的模型」与「实际请求上游的模型」之间的分歧无法通过日志对照，只能依赖客户端抓包——2026-09-18 排查已停用模型 id 被透传一事时正是卡在这里。

#### Scenario: 成功回合记录模型名

- WHEN 一个回合的执行正常返回（无异常）
- THEN 日志出现一条 INFO，含 `turn completed` 与 `stream=` / `session=` / `workspace=` / `model=` 四项
- AND `model` 为该回合实际使用的模型 id

#### Scenario: 成功与失败记录的字段可对照

- WHEN 同一会话先后出现一个成功回合与一个失败回合
- THEN 两条日志可用 `stream=` / `session=` / `workspace=` / `model=` 四个键直接对照
- AND 两条记录中同一字段的键名逐字符相同

## 1. 事件模型扩展（observability 基础）

- [x] 1.1 `SessionLogSink` 增加 default 方法：`onContextSnapshot(ContextSnapshot)`、`onSystemEvent(String type, Map<String,Object> payload)`、`onPermissionDecision(Map<String,Object> payload)`；NOOP 不变（测试：新方法可被空实现调用，零副作用）
- [x] 1.2 `SessionLogger` 实现三类新事件写入 `session.jsonl`（含 seq/时间戳）；session header `version` 1→2（测试：写入后 JSONL 行 type 正确、header 含 version:2；旧 reader 兼容）
- [x] 1.3 `AgentLoop.processTurn` 错误路径广播 `system/error`（errorClass + message，截断）（测试：fake provider 抛错 → 事件流含 system/error 且 REPL 不退出）
- [x] 1.4 `ContextCompressor` 压缩成功/失败广播 `system/compact`（beforeTokens/afterTokens/success/summary 截断）（测试：模拟压缩 → 事件字段正确）
- [x] 1.5 `PermissionManager` 裁决结果为 ask/deny 时广播 `permission/decision`（tool/path/decision/reason）（测试：ask 与 deny 各一条事件）
- [x] 1.6 `LlmRetry` 每次重试广播 `system/retry`（attempt/errorClass/errorMsg 截断）（测试：重试 2 次 → 2 条事件，attempt 递增）
- [x] 1.7 `SessionRecorder` 透传三类新事件到 `SessionLogger`（测试：fake sink 收到透传）
- [x] 1.8 本组完成后 `mvn test` 全绿 + commit + push

## 2. context 快照

- [x] 2.1 新增 `ContextSnapshot` record（turn/systemPrompt/memoryInjected/compacted/recentFiles/toolNames/messageCount/estTokens）+ 从 `ChatRequest` 提取的 builder（测试：字段映射正确）
- [x] 2.2 `AgentLoop.toRequest()` 后广播 `context/snapshot`（每轮一次；systemPrompt 按 `snapshotMaxChars` 截断，默认 2000）（测试：一轮含一次快照、长 systemPrompt 被截断带标记）
- [x] 2.3 `AgentConfig.Logging` 增加 `snapshotMaxChars`（默认 2000），ConfigLoader 解析（测试：yaml 缺省用默认值）
- [x] 2.4 本组完成后 `mvn test` 全绿 + commit + push

## 3. 敏感信息脱敏

- [x] 3.1 新增 `log/Redactor.java`：正则覆盖 `sk-[A-Za-z0-9]{16,}`、`Bearer <token>`、`apiKey/api_key 键值对`，替换为 `***REDACTED***`（测试：三种格式各自命中；普通文本不误伤）
- [x] 3.2 `SessionLogger` 四个文件所有写路径统一过 `Redactor`（测试：四个文件均无明文 key；`logging.enabled=false` 不初始化 Redactor）
- [x] 3.3 本组完成后 `mvn test` 全绿 + commit + push

## 4. 日志保留策略

- [x] 4.1 `AgentConfig.Logging` 增加 `retentionMaxAgeDays`（默认 30）/ `retentionKeepSessions`（默认 50），ConfigLoader 解析（测试：缺省默认值）
- [x] 4.2 新增 `log/SessionRetentionCleaner`：新建会话时清理过期目录 + 超数量删最旧，单目录失败只 WARN（测试：过期删除、数量上限、删除失败跳过）
- [x] 4.3 `SessionLogger` 构造时调用 cleaner（测试：构造后旧目录被清）
- [x] 4.4 logback.xml 改 `SizeAndTimeBasedRollingPolicy`（10MB × 7 天）（测试：配置加载不报错；`mvn test` 日志正常）
- [x] 4.5 本组完成后 `mvn test` 全绿 + commit + push

## 5. 日志驱动测试基建

- [x] 5.1 新增测试工具 `SessionEventAssertions`：读 JSONL、按 type 过滤、按 seq 断言顺序、字段断言、易变字段归一化（timestamp/seq/callId）（测试：工具自身单测）
- [x] 5.2 golden 事件样例：`src/test/resources/e2e/events/` 下放"读文件 → 工具调用 → 回复"的规范化事件序列（测试：样例可被断言工具解析）
- [x] 5.3 E2E 用例：wiremock 跑一轮含工具调用的对话，断言事件类型序列与 golden 一致（归一化后）（测试：E2E 通过）
- [x] 5.4 本组完成后 `mvn test` 全绿 + commit + push

## 6. 会话回放

- [x] 6.1 新增 `log/SessionReplay.java`：读 `session.jsonl` 重建 `MessageHistory`（跳过 context/snapshot 与 system/*；未知 type 跳过不报错）（测试：单轮重建、工具轮重建、未知事件跳过）
- [x] 6.2 回放工具集成测试：真实 SessionLogger 写一轮 → SessionReplay 读回 → 消息顺序一致（测试：通过）
- [x] 6.3 本组完成后 `mvn test` 全绿 + commit + push

## 7. Web UI 日志查看

- [x] 7.1 agent-web 新增 `LogController`：`GET /api/logs/sessions`、`GET /api/logs/sessions/{id}/events?offset=&limit=`、`GET /api/logs/sessions/{id}/files/{name}`；`{id}`/`{name}` 白名单校验防路径穿越；沿用 trusted-hosts（测试：列会话/分页/白名单 400/不存在 404）
- [x] 7.2 前端新增 `/logs` 路由：会话列表页 + 会话详情页（事件流表格、类型过滤、事件/聊天/工具视图切换、空态）（测试：vitest 组件测试）
- [x] 7.3 本组完成后 `mvn test` + 前端测试全绿 + commit + push

## 8. 文档与收尾

- [x] 8.1 更新 `docs/design/logging-design.md`：新事件类型表、context 快照、脱敏、保留策略、回放、Web 日志 API
- [x] 8.2 更新 `docs/test-agent-demo/test-design.md`：日志驱动测试与回放的测试设计
- [x] 8.3 全量 `mvn verify`（含 jacoco LINE≥80% / BRANCH≥70%）通过
- [x] 8.4 最终 commit + push，汇报里程碑






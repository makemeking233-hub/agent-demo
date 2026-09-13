## 1. agent-core：分档与归档器

- [x] 1.1 新增 `SessionAgeBucket`（recent / last_week / within_month / earlier，左闭右开，按相对天数）
- [x] 1.2 `SessionStore` 新增 `SessionFile(id, lastModifiedMillis, sizeBytes)` 与 `listSessionFiles` / `listArchivedFiles` / `loadFile`
- [x] 1.3 新增 `SessionAutoArchiver.archiveStale(dir, retention, nowMillis, isActive)`：超期归档、跳过活动会话、单失败不影响其余、`nowMillis` 显式传入以便测边界
- [x] 1.4 单测 `SessionAgeBucketTest`（5 例）+ `SessionAutoArchiverTest`（10 例）

## 2. agent-web：调度与配置

- [x] 2.1 `SessionAutoArchiveService`：`ApplicationReadyEvent` 跑一次 + `@Scheduled(fixedDelay)` 周期；遍历所有工作区
- [x] 2.2 `WebApplication` 加 `@EnableScheduling`
- [x] 2.3 `ChatStreamService.isSessionActive(sessionId)` 供跳过进行中的会话
- [x] 2.4 `application-web.yml` 加 `agent.session.auto-archive.{enabled, after-days, interval-ms}`
- [x] 2.5 单测 `SessionAutoArchiveServiceTest`（9 例：开关 / 保留期 / 跳过活动 / 幂等 / 两个入口 / 目录缺失 / 保留期非法）

## 3. 分档随列表返回

- [x] 3.1 `SessionSummaryDto` 加 `bucket`（保留 5 参兼容构造；普通列表为 null）
- [x] 3.2 `SessionController.buildArchivedSummaries` 填 `bucket`
- [x] 3.3 `SessionControllerTest` 补用例：归档项带 `last_week`，普通列表 `bucket` 为 null

## 4. 前端：归档视图按档分组

- [x] 4.1 `api/chat.ts` 的 `SessionSummary` 加 `bucket?: string`
- [x] 4.2 `Sidebar.tsx`：归档视图按 `bucket` 分组、档间由近到远、空档不显示；普通视图仍按工作区分组；缺字段兜底归「更早」
- [x] 4.3 `Sidebar.test.tsx` 补 3 例

## 5. 收尾

- [x] 5.1 **测试隔离**：`WebIntegrationTest` / `LocalKeySendTest` 关掉自动归档，避免跑测试改动真实会话存档
- [x] 5.2 README §10 补自动归档与分档说明
- [x] 5.3 `mvn -o -pl agent-core,agent-web verify` + `npx vitest run` + `npx tsc --noEmit` 错误数不变
- [x] 5.4 `openspec validate auto-archive-stale-sessions --strict` + archive + commit + push

## 6. 验收证据

| 项 | 证据 |
|----|------|
| 超期归档 | `SessionAutoArchiverTest`：8 天前活动的被归档、3 天前的原地保留，侧车 meta 一并移动 |
| 边界 | 恰好 7 天不归档（`>= cutoff` 保留）；`SessionAgeBucketTest` 锁定 7/14/30 天恰好落档 |
| 跳过进行中 | 活动会话不归档且原文保留 |
| 幂等 | 第二次运行扫描为 0、归档列表仍只有 1 个 |
| 开关与保留期 | `enabled=false` 不动作；`after-days=30` 时 10 天前保留、40 天前归档；`after-days<=0` 跳过 |
| 两个入口 | `onStartup()` 与 `scheduled()` 都真实执行归档 |
| 跨工作区 | 遍历 `WorkspaceStore.list`，单工作区失败仅记日志 |
| 分档随列表 | `SessionControllerTest`：推 mtime 到 10 天前 → `bucket=last_week`；普通列表 `bucket` 为 null |
| 前端分组 | `Sidebar.test.tsx`：三档标题按近→远出现、空档「本月」不显示、缺字段兜底「更早」、普通视图不误用分档 |
| 全量回归 | agent-core / agent-web 全绿；前端 133 用例全绿（原 130，+3）；tsc 错误数不变（27，均既有） |

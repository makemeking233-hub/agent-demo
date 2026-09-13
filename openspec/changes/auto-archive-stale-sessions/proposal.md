## Why

会话存档目前只会因为用户手动点「归档」而移动，长期使用后 `sessions/` 里会堆积大量已经不会再打开的历史会话。用户为此需要：**自动把超过一周没活动的会话归档，并让归档列表按时间分档归类**。

现状（evidence）：

- `SessionStore.archive/restore/listArchived` 已具备把 `<id>.jsonl` 与 `<id>.meta.json` 移到 `sessions/.archive/` 的能力，但**只有手动触发路径**（前端 `...` 菜单）。
- `SessionController` 的归档列表按 mtime 降序**扁平**展示，没有任何时间分档。
- 仓库里**没有任何调度基础设施**（无 `@EnableScheduling` / `@Scheduled`），也没有"会话保留策略"配置项。

## What Changes

- **自动归档**：新增 `SessionAutoArchiver`（agent-core，纯函数 + 可注入时间），把 `sessions/` 下最后活动时间早于保留期的会话移到 `.archive/`。
  - **保留期默认 7 天**，按**最后活动时间**（文件 mtime，即会话最后一条记录时间）判定。
  - **跳过正在进行的会话**（存在活动流）——它随时会继续写入。
  - 幂等、单会话失败不影响其余、结果含扫描数与归档 id 列表供日志。
- **触发**：`agent-web` 新增 `SessionAutoArchiveService` —— 应用就绪后跑一次 + 每 6 小时定时；遍历所有工作区。
- **可配置**：`agent.session.auto-archive.{enabled, after-days, interval-ms}`（默认 `true` / `7` / `21600000`）。
- **时间分档**：新增 `SessionAgeBucket` 按相对天数分四档，归档列表项带上 `bucket` 字段，前端在归档视图按档分组：
  | 档 | 条件 | 显示 |
  |----|------|------|
  | `recent` | 不足 7 天 | 最近归档 |
  | `last_week` | 7–14 天 | 上周 |
  | `within_month` | 14–30 天 | 本月 |
  | `earlier` | 超过 30 天 | 更早 |

  > 前三档即用户要的「上周 / 本月 / 更早」；`recent` 是**手动归档**产生的必要补充——用户随时可以手动归档一个刚聊过的会话，若不设该档它会显得"未满一周却落在上周"，语义错误。

无破坏性变更：归档仍是软删除（可恢复），文件布局不变（仍在扁平的 `.archive/`）。

## Capabilities

### New Capabilities

- `session-archive`：会话归档的保留策略、触发时机与时间分档。

### Modified Capabilities

- `web-ui`：归档列表项新增时间分档字段，前端归档视图按档分组。

## Impact

- **agent-core**：新增 `SessionAutoArchiver`、`SessionAgeBucket`；`SessionStore` 增加按文件列出（含 mtime/大小）的能力
- **agent-web**：新增 `SessionAutoArchiveService`、`WebApplication` 开启调度、`SessionSummaryDto` 加 `bucket`、`SessionController` 填分档、`ChatStreamService` 暴露"会话是否活动"、`application-web.yml` 加配置默认值
- **前端**：`Sidebar.tsx` 归档视图按分档分组、`api/chat.ts` 类型加 `bucket`
- **测试**：`SessionAutoArchiverTest`、`SessionAgeBucketTest`、`SessionControllerTest` 扩用例、`Sidebar` 分档分组用例

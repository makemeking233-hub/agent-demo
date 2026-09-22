# settings Specification（rewrite-permission-mode-dsh REMOVED）

## REMOVED Requirements

### Requirement: User can change permission mode

**Reason**：整个权限模式（mode 选择 + 4 档 dsh 命名 + 模式切换 API + escalate + SSE 事件 + session log 持久化 + 默认值）已迁移到新 capability `sandbox-policy`。原 `settings` capability 仅保留 `general.permission.mode` 作为 YAML 持久化点的契约（详见 sandbox-policy §"settings.yaml general.permission.mode 持久化"与 §"模式持久化粒度"）。

**Migration**：

- 行为迁移：所有 mode 行为契约改由 `openspec/specs/sandbox-policy/spec.md` 表达；本文件不再重复。
- 持久化点保留：`general.permission.mode` 仍在 settings.yaml 中；PATCH `/api/settings/general/permission/mode` 仍可用，但值校验与默认行为由 sandbox-policy 定义（plan/ask/danger-full/dontAsk）。
- 实现侧：原 `User can change permission mode` 段（4 档 plan/ask/danger-full/dontAsk 下拉 + Persistence 场景）从本 spec 删除；前端 `PermissionModeSelect.tsx` 与 `useSettingsStore` 对 `general.permission.mode` 的读写契约不变，但具体 4 档语义以 sandbox-policy 为准。
- 文档：`README.md` §"User can change permission mode" 章节随 archive 删除；权限模式行为章节统一指向 sandbox-policy。

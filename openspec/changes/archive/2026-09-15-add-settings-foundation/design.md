# 设计：add-settings-foundation

> 与 [`docs/superpowers/specs/2026-09-15-add-settings-menu-design.md`](../../../docs/superpowers/specs/2026-09-15-add-settings-menu-design.md) §6 同源；本文聚焦 M1 的"How"。

## Context

agent-demo 当前在 `TopBar.tsx:26` 已有 `<button aria-label="设置">` 齿轮按钮，但 `App.tsx:197` 用 `alert("设置 v0.2 接入")` 占位。M1 的目标是把占位替换为**可工作的设置面板基础设施**：

- 后端：能读/写 `~/.agent-demo/settings.yaml`，监听变更并通过 SSE 广播
- 前端：能用 modal 打开设置面板（即使内容暂时为空），并通过 SSE 实时同步

本次不实现具体设置项（外观/权限/语言/Enter 行为）——那是 M2。本次也不实现 Agent 预设/插件/模型菜单——那是 M3。

整体设计约束：
- 与 dsh web 设置面板视觉对齐（左侧 nav + 右侧 content + 居中 modal）
- 不引入新依赖（不引 Zustand/Jotai/Redux/i18n 框架）
- 严格遵守 §3 Fail-Closed 默认与 JSONL 0700 文件权限
- 文件读写有原子性保证 + revision 乐观锁

## Goals / Non-Goals

**Goals**:
- 提供 1 个 GET + 3 个 PATCH REST 端点 + 1 个 SSE 端点（M1 仅 PATCH 三个字段中的基础；M2 增加 enterBehavior 等）
- 提供模块级 `useSettingsStore` + `useSyncExternalStore` 订阅
- 提供 `SettingsModal` shell + 4 项菜单路由（其中"通用"菜单显示 SettingsEmpty 占位；其它三个显示对应占位 M3 接入）
- 提供 App.tsx alert → SettingsModal 的接入
- 端到端测试覆盖：mvn verify 全绿 + npx vitest 全绿 + tsc ≤ 7 + Jacoco ≥ 80/70

**Non-Goals**（M1 范围内不做）：
- 任何具体设置项的写入/读取（外观/权限/语言/Enter 行为 —— M2）
- 任何具体菜单的实质内容（模型/插件/Agent 预设 —— M3）
- reveal 跨平台命令（—— M2）
- 通用设置项之外的菜单内容（—— M3）
- settings.yaml schema 校验规则（—— M2）
- session 级 metadata 读取（—— 另起 change）

## Decisions

### D1. 后端存储位置：`~/.agent-demo/settings.yaml`

**为什么不用 `application.yml`**：改设置需要重启 JVM；与运行时配置耦合，不利于热重载。

**为什么独立 yaml 文件**：单一职责；与现有 `application.yml` 解耦；未来可加 schema 版本号。

**风险**：首次启动若目录不存在要自动创建（`mkdir -p ~/.agent-demo/`）。

### D2. 原子写：写临时文件 + `Files.move` 原子替换

```java
Path tmp = settingsFile.resolveSibling(".settings.yaml.tmp");
Files.writeString(tmp, yamlContent);
Files.move(tmp, settingsFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
```

**为什么**：避免半写状态被文件监听器捕获。

### D3. 文件监听：JDK WatchService + 50ms debounce

**为什么不用 `io.methvin.watcher` 等第三方库**：项目坚持最小依赖；JDK 自带够用。

**风险**：Mac 上 WatchService 在子目录事件多发；用 `ENTRY_MODIFY` + 50ms debounce 合并。

### D4. SSE 端点独立成 `SettingsSseController` 类

**为什么不复用现有 chat SSE**：职责分离；chat SSE 生命周期跟 session 绑定，settings SSE 应跨 session 全局存活。

### D5. 前端 store：模块级单例 + `useSyncExternalStore`（**不引 Zustand**）

```typescript
let store: SettingsStore | null = null;
const listeners = new Set<() => void>();

export function useSettingsStore<T>(selector: (s: SettingsStore) => T): T {
  if (!store) store = createStore();
  return useSyncExternalStore(
    cb => { listeners.add(cb); return () => listeners.delete(cb); },
    () => selector(store!)
  );
}
```

**为什么不引 Zustand**：项目零状态库依赖；store 简单到不值得加依赖。

### D6. Modal 焦点管理：useRef 记忆触发元素

```typescript
const triggerRef = useRef<HTMLElement | null>(null);
function open() {
  triggerRef.current = document.activeElement as HTMLElement;
  setOpen(true);
}
function close() {
  setOpen(false);
  setTimeout(() => triggerRef.current?.focus(), 0);
}
```

**为什么**：关闭时焦点回到触发按钮，符合 ARIA 弹窗规范。

### D7. 关闭路径：ESC + mask click + X 按钮（参考 dsh SettingsRoot.tsx:51/63/86）

**为什么不只支持 X**：mask click 是用户预期；ESC 是快捷键习惯。

### D8. settings.yaml schema v1（M1 仅实现最小集）

```yaml
version: 1
general:
  appearance:    { preference: system }   # 实际值在 M2 写入
  permission:    { mode: ask }            # 同上
  enterBehavior: { mode: send }           # 同上
```

**注意**：M1 仅写 schema 与默认值的 Service；M1 阶段 settings.yaml 存在但**字段值都是默认**。具体写入由 M2 完成。

### D9. PATCH 路径 dot notation：`general.appearance.preference`

```java
class SettingsPath {
  static String[] parse(String path) { /* "a.b.c" → ["a","b","c"] */ }
}
```

**为什么不直接用 Jackson path 表达式**：增加学习成本与依赖；dot notation 够用。

## Risks / Trade-offs

| # | 风险 | 缓解 |
|---|------|------|
| R1 | WatchService 在 Windows / macOS 上行为差异 | T5 单测覆盖三平台 + debounce |
| R2 | SSE 连接断线后丢失中间变更 | T11 实现自动重连 + 重连后 GET 全量同步 |
| R3 | 模块级单例在 React 18 strict mode 下被双调用 | T10 单测覆盖 strict mode |
| R4 | M1 modal 是空壳，reviewer 觉得啥也没干 | proposal.md 已明确"M1 是基础设施 + 空 modal，M2 接入具体项" |
| R5 | PATCH 路径不存在时返回 404 而不是 400 | 区分：路径不存在 404；值非法 400 |
| R6 | `useSyncExternalStore` 在 SSR 下需要 hydration 处理 | 项目是 SPA，无 SSR 风险 |

## Migration Plan

无（新增能力，不涉及迁移）。

## Open Questions

1. **PATCH 是否要支持嵌套路径**（如 `general.appearance`）？目前设计是单字段（`general.appearance.preference`）。M2 再考虑。
2. **settings.yaml 不存在时是否回 200 + 默认值** 还是 404？目前设计是 200 + 默认（更友好）。
3. **SSE 端点要不要 heartbeat（每 30s 发个 `:keepalive`）**？M1 暂不实现；观察连接稳定性再决定。

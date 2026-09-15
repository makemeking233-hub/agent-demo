# 设计：add-settings-general-items

> 与 [`docs/superpowers/specs/2026-09-15-add-settings-menu-design.md`](../../../docs/superpowers/specs/2026-09-15-add-settings-menu-design.md) §7 同源。

## Context

M1 已建好"管道"（REST/SSE/store/modal shell），但 modal 内容区显示占位。M2 的目标是把 4 个具体设置项（外观/权限/语言/Enter 行为）接入，让用户能真正修改并立即生效；同时提供"打开配置文件"按钮（reveal + 复制路径）。

最关键风险点：Composer 改造改了用户快捷键——3 种 mode × 2 种 agent 状态 = 6 用例必须 e2e 兜底。

## Goals / Non-Goals

**Goals**:
- 4 个设置项在 modal 里可读可写，PATCH 后立即更新本地 store + 广播 SSE
- 外观三卡片实际驱动 `<html data-theme>` 切换
- Composer 读 enterBehavior.preference，三种行为分支正确执行
- "在文件管理器中显示"按钮跨平台可用（Win/Mac/Linux）
- 测试：单测全绿 + e2e 通过 + Jacoco ≥ 80/70 + tsc ≤ 7

**Non-Goals**（M2 不做）：
- i18n 实际生效（language 仅 UI 占位 + localStorage 写入）
- session 级 metadata 读取（mode 写入 settings 但 session 创建时读 mode 是另起 change）
- queue 跨会话持久化（YAGNI）
- 真正调起编辑器（用 reveal 替代）
- 任何 tooltip / 重置默认按钮

## Decisions

### D1. 外观：ThemeToggle 改造为壳子

```typescript
// ThemeToggle.tsx 改造后
export function ThemeToggle() {
  const pref = useSettingsStore(s => s.snapshot?.general.appearance.preference ?? "system");
  return <AppearanceCards value={pref} onChange={...} compact />;
}
```

**为什么不重构为 2 个独立组件**：保持 TopBar 的 ThemeToggle 名字不变，避免改动 TopBar.tsx；新组件 AppearanceCards 同时被 TopBar 和 SettingsModal 复用。

### D2. 系统主题监听：`prefers-color-scheme`

```typescript
const mql = window.matchMedia("(prefers-color-scheme: dark)");
useEffect(() => {
  const onChange = () => preference === "system" && applyTheme("system");
  mql.addEventListener("change", onChange);
  return () => mql.removeEventListener("change", onChange);
}, [preference]);
```

**为什么用 hook 而不是 context**：与 useSettingsStore 一致，避免引入额外 React Context。

### D3. 权限模式命名区分

- 既有 `PermissionCard.tsx`：**不动**（agent 工具调用时的权限请求卡片）
- 新增 `PermissionModeSelect.tsx`：设置项的下拉控件

**为什么分开命名**：避免歧义；一个在 chat 流中、一个在设置面板中。

### D4. Language 仅 localStorage

```typescript
// LanguageSelect.tsx
const [lang, setLang] = useState(() => localStorage.getItem("agent-demo:language-preference") ?? "zh");
return <select value={lang} onChange={e => {
  setLang(e.target.value);
  localStorage.setItem("agent-demo:language-preference", e.target.value);
}}>...</select>;
```

**为什么不进 settings.yaml**：v0.2 不引入 i18n 框架，写到 yaml 没意义；后续真正接 i18n 时再迁移。

### D5. Enter 行为：Composer 三分支 + 内存 queue

```typescript
function Composer() {
  const enterMode = useSettingsStore(s => s.snapshot?.general.enterBehavior.mode ?? "send");
  const isAgentBusy = useChatState(s => s.isAgentRunning);
  const [queue, setQueue] = useState<string[]>([]);

  function onEnter(message: string) {
    if (!isAgentBusy) return sendNow(message);
    if (enterMode === "send") return toast("agent 还在跑");
    if (enterMode === "queue") return setQueue(q => [...q, message]);
    if (enterMode === "newSession") return confirmThenNewSession(message);
  }

  // agent 跑完时自动 dequeue
  useEffect(() => {
    if (!isAgentBusy && queue.length > 0) {
      const [next, ...rest] = queue;
      setQueue(rest);
      sendNow(next);
    }
  }, [isAgentBusy]);
}
```

**为什么 v0.2 用 useState 而非真正的 SSE 事件订阅**：现有 chat 已有 isAgentRunning 状态；简化实现。

### D6. Reveal 跨平台：ProcessBuilder + 硬编码白名单

```java
class SettingsRevealController {
  void reveal() {
    String os = System.getProperty("os.name").toLowerCase();
    ProcessBuilder pb;
    if (os.contains("win")) pb = new ProcessBuilder("explorer.exe", "/select," + path);
    else if (os.contains("mac")) pb = new ProcessBuilder("open", "-R", path);
    else pb = new ProcessBuilder("xdg-open", path);  // 仅目录
    pb.start();
  }
}
```

**安全措施**：
- path 限定为 `SettingsFile.resolve()` 返回的绝对路径，**不接受用户输入**
- 不走 shell（直接 exec），规避 §3 shell 黑名单
- 单测覆盖三平台 + 路径遍历攻击测试（构造恶意 path 应被拒绝）

### D7. Composer queue 简化：useState + useEffect 监听 isAgentBusy

**为什么不引入更复杂的事件总线**：项目已有 chat 状态管理；用最小改动接上即可。

## Risks / Trade-offs

| # | 风险 | 缓解 |
|---|------|------|
| R1 | Composer 改造改用户快捷键 | 6 用例单测 + e2e 兜底；queue 简化实现保留 |
| R2 | ThemeToggle 测试因改造失败 | M2 T10 同步更新测试 |
| R3 | reveal 跨平台命令缺失/路径注入 | 硬编码命令 + path 白名单 + 注入测试 |
| R4 | 系统主题切换不响应（prefers-color-scheme 事件不触发） | 单测模拟 matchMedia 变化 |
| R5 | Language 仅 UI 占位被误以为已实现 | T12 行内提示 |
| R6 | queue 在多 tab 间不一致（每 tab 各一份） | v0.2 YAGNI；M3+ 视情况升级到 SSE 同步 |

## Migration Plan

无（仅新增行为，不涉及迁移）。

## Open Questions

1. **Composer queue 是否要持久化**（localStorage）？目前 YAGNI。
2. **reveal 在 Linux 上 `xdg-open` 行为依赖 DE**（Wayland vs X11）？T6 显式标注。
3. **Language 行内提示的措辞**：「语言切换将在后续版本启用完整 i18n 支持」是否要更具体（如时间）？

# 设计：polish-theme-toggle

## Context

TopBar 上当前以 `<AppearanceCards compact />` 直接渲染 3 张卡片，占据 ~280px 横向空间 + 高对比度边框，与相邻的「深色模式开关」和「设置」按钮形成视觉竞争。需要：
1. TopBar 收窄为单个图标按钮（动态 Sun/Moon，跟随当前 preference）
2. 3 张卡片移到 Popover 内，按需展开
3. 卡片视觉更精致（圆角、阴影、间距）

## Goals / Non-Goals

**Goals**:
- TopBar 上 ThemeToggle 仅占 ~32px（一个图标按钮）
- 点击图标 → Popover 弹出显示 3 卡片
- Popover：圆角 12px、阴影、动画、点击外部关闭、Esc 关闭、选中后自动关闭
- 卡片视觉：圆角 12px、阴影、间距更松、hover/selected 状态更明显
- 兼容原 `<ThemeToggle />` API（TopBar 调用点不变）

**Non-Goals**:
- 不改 settings store、不改 backend
- 不改 settings.yaml schema
- 不改 AppearanceCards 在 SettingsModal 内的呈现（保留原标题 + 卡片样式）
- 不引入新的依赖（继续用 lucide-react + 现有 CSS）

## Decisions

### D1. ThemeToggle 改为按钮 + Popover

```tsx
function ThemeToggle() {
  const [open, setOpen] = useState(false);
  const preference = useSettingsStore(s => s.snapshot?.general?.appearance?.preference);
  const Icon = preference === "dark" ? Moon : preference === "light" ? Sun : Monitor;
  return (
    <>
      <button onClick={() => setOpen(!open)} aria-label="切换主题" aria-expanded={open}>
        <Icon size={16} />
      </button>
      {open && <ThemePopover onClose={() => setOpen(false)} />}
    </>
  );
}
```

### D2. ThemePopover 独立组件

- 定位：相对按钮右下角，使用绝对定位 + 计算 top/right
- 内容：渲染 `AppearanceCards`（非 compact）
- 关闭：点击外部 + Esc + 选中后自动关闭
- 视觉：圆角 12px、阴影、白色背景

### D3. AppearanceCards 视觉升级

- 卡片：圆角从 8px → 12px、padding 从 12px 24px → 16px 20px
- 间距：gap 从 8px → 10px
- selected：边框 + 阴影 + 背景色加深
- hover：背景色微调
- 字体：从 13px → 13px（保持），字重保持

### D4. Popover 定位策略

使用 `position: absolute`，相对 ThemeToggle 的容器。容器加 `position: relative`。

```tsx
<div className={styles.wrapper}> {/* position: relative */}
  <button>...</button>
  {open && (
    <div className={styles.popover}> {/* position: absolute; top: 100%; right: 0 */}
      <AppearanceCards />
    </div>
  )}
</div>
```

## Risks / Trade-offs

| # | 风险 | 缓解 |
|---|------|------|
| R1 | Popover 定位与 TopBar 边界冲突 | 用 right: 0 + z-index: 1000，必要时后续可改 floating-ui |
| R2 | 点击外部关闭的逻辑可靠性 | 用 document.addEventListener 监听 mousedown + Popover ref.contains 检查 |
| R3 | Esc 关闭后焦点回归触发按钮 | useRef 保存触发元素，关闭时 .focus() |

## Migration Plan

无（仅 UI 改造）

## Open Questions

1. Popover 关闭后焦点要不要回到按钮？→ **要**（accessibility）
2. Popover 是否需要动画？→ 暂用 CSS transition 0.15s（无 framer-motion）

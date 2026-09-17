# 设计：picker-reveal-only

## Context

powerShell FolderBrowserDialog 启动 3-5s，体验差。简化方案：去掉 dialog 流程，只留路径输入框 + reveal 按钮。

## Goals / Non-Goals

**Goals**:
- modal 打开后立即可输入
- 「在资源管理器中显示」按钮 → 资源管理器打开当前路径（或 home）
- 路径有变化时 name 字段自动 basename
- Esc 关闭 / 取消按钮 / 提交按钮保留
- 体验类似 dsh "Edit path" 路径

**Non-Goals**:
- 不引入新端点
- 不支持 "对话框" 选择（v0.1 简化为路径输入）
- picker-async 端点保留为 deprecated fallback（未来 picker-jna 接入用）

## Decisions

### D1. modal 移除「选择文件夹...」按钮

新布局：
```
┌─────────────────────────────────┐
│ Select Workspace Directory  [X]│
├─────────────────────────────────┤
│ 文件夹：[C:\Users\86184...] [📂] │
│                                 │
│ 工作区名称：[md-main]            │
├─────────────────────────────────┤
│        [取消] [选择此目录]       │
└─────────────────────────────────┘
```

### D2. reveal 按钮

调 `/api/settings/reveal`（explorer /select, ~50ms），无 picker。

### D3. 路径自动 basename

`useEffect([selectedPath])` 监听路径变化 → 自动填 basename 为 name（除非用户已手动改过 name）。

## Risks / Trade-offs

| # | 风险 | 缓解 |
|---|------|------|
| R1 | 用户不知道粘什么路径 | reveal 按钮引导到资源管理器复制 |
| R2 | 路径输错（不存在） | onSubmit 校验 + 后端 409 |
| R3 | 体验不如 dsh in-app browse | v0.1 简化；后续 picker-jna 引入 in-app |

## Migration Plan

无（旧行为被替换）。

## Open Questions

1. 是否同时保留 picker-async 端点作 fallback？→ **保留为 deprecated**

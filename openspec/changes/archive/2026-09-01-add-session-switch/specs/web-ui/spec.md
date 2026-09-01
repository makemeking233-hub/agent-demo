# web-ui Specification (delta)

> 本文件是 `add-session-switch` 的 delta spec。在 archive 时合并到 `openspec/specs/web-ui/spec.md`。

## ADDED Requirements

### Requirement: 真实会话列表与切换

系统 SHALL 在 web UI 侧边栏展示**真实会话列表**（来自后端 `GET /api/sessions`），并允许用户点击某个会话切换——切换后加载该会话的历史并渲染到对话区，`session_id` 随之切换，后续对话复用该会话上下文。

#### Scenario: 侧边栏展示真实会话

- **WHEN** web UI 加载且后端 `/api/sessions` 返回会话列表
- **THEN** 侧边栏展示这些会话（id / 标题 / 预览 / workspace），不再显示硬编码占位

#### Scenario: 点击会话切换并加载历史

- **WHEN** 用户点击侧边栏某个会话
- **THEN** 当前会话高亮切换为该会话，对话区清空后加载该会话的历史（`GET /api/sessions/{id}/messages`）并渲染
- **AND** 后续发送消息复用该 `session_id`，延续该会话上下文

#### Scenario: 无历史会话

- **WHEN** 切换到目录中无消息的会话
- **THEN** 对话区清空并显示空态，不报错

#### Scenario: 后端无会话

- **WHEN** `/api/sessions` 返回空列表
- **THEN** 侧边栏显示空态，新建会话后出现在列表

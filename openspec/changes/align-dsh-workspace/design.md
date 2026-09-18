# Design: align-dsh-workspace

## 1. 现状

`WorkspaceStore` 用目录名 + meta.json 映射 directory → workspace：

```text
<agentDataDir>/
├── sessions/                                # 默认 "agent-demo" workspace
└── workspaces/<name>/
    ├── meta.json                            # {name, dir, created_at}
    └── sessions/<id>.jsonl                  # sessions
```

接口：
- `create(name, dir)` → 校验 + 写 meta.json
- `list(agentDataDir)` → 默认 + 按 name 字母序
- `get(agentDataDir, name)` / `sessionsDirFor(...)` / `exists(...)`

## 2. DSH 完整模型

```text
<agentDataDir>/.dsh/                       （DSH 实际位置不同）
├── workspaces/
│   ├── order.json                        # 持久化的 WorkspaceId 序列（durable order）
│   └── <workspaceId>/
│       │   └── record.json                 # {id, path, title, createdAt, updatedAt, sessionIds[]}
└── sessions/
    └── <sessionId>/
        ├── header.json                   # 包含 cwd（用于 attach 校验）
        └── events.jsonl                  # session log
```

DSH 关键差异：
1. **WorkspaceId vs name**：DSH 用稳定 id（不是 directory name），path/title 与 id 解耦
2. **realpath 规范化**：`create(path)` → `fs.realpath(path)` → 查同 canonical path 已有 record → 复用 or 新建
3. **durable order**：持久化到 `order.json`，启动时初始化（按 session 创建时间 desc），支持 `insertBefore(id, before?)` 拖拽
4. **title 与 path 完全解耦**：title 由 basename 派生但可独立 `setTitle`
5. **missing-dir 容忍**：record 保留 + `status() = 'missing-dir'`
6. **session cwd 校验**：attach 时 `realpath(cwd) === record.path` 才算同 workspace

## 3. 改造后模型

```text
<agentDataDir>/
├── sessions/                                # 默认 "agent-demo" workspace（保持不变）
└── workspaces/
    ├── order.json                        # 持久化的 workspace name 序列（durable order）
    └── <name>/
        ├── meta.json                    # v2: {name, id, path, title, created_at, updated_at, session_ids[]}
        └── sessions/<id>.jsonl         # sessions（保持不变）
```

字段变化（v1 → v2 meta.json）：
- `name` (保留，作为目录名 + 默认 title)
- `id`: UUID（v2 新增；DSH 用稳定 id）
- `path`: realpath 规范化路径（v2 新增；目录可能变）
- `title`: display title（v2 新增；初始 = basename(path)，可改）
- `created_at`: 创建时间 ms（保留）
- `updated_at`: 最近修改时间 ms（v2 新增；自动更新）
- `session_ids[]`: 候选索引（v2 新增；按 cwd 自动 attach，按时间 desc）

v1 → v2 自动迁移：缺字段用默认值补全（id = UUID.randomUUID()，title = basename(path)，updated_at = created_at，session_ids = []）。

## 4. 接口设计

### 4.1 `WorkspaceStore` 新增方法

```java
public record Workspace(
    String name,           // 目录名（保留 v1 字段）
    String id,             // 稳定 UUID（v2 新增）
    Path path,             // realpath 规范化（v2 新增）
    String title,          // display title（v2 新增）
    long createdAt,        // 创建时间
    long updatedAt,        // 更新时间
    Path sessionsDir,      // <ws>/sessions
    List<String> sessionIds, // 候选索引（v2 新增）
    Status status          // OK / MISSING_DIR
) {
    public enum Status { OK, MISSING_DIR }
}

// 新增/重写方法
public static Workspace create(Path agentDataDir, Path dir);   // 自动派生 name + title；同 canonical path 复用
public static void delete(Path agentDataDir, String name);      // 仅删 workspaces/<name>，不动 dir/sessions
public static void rename(Path agentDataDir, String name, String newTitle);
public static void insertBefore(Path agentDataDir, String name, String beforeName);  // null beforeName = 移到末尾
public static Workspace list(Path agentDataDir, String name);   // 含 status
public static List<Workspace> list(Path agentDataDir);          // 按 durable order
public static void ensureOrderInitialized(Path agentDataDir);  // 启动时按 session header cwd 自动 attach + 按 createdAt desc 初始化 order
```

`name` 不再用 `WorkspaceStore.create(name, dir)` —— name 自动从 basename + 唯一序号取（`md-main`、`md-main-2`）。

### 4.2 `WorkspaceController` 新增端点

```java
@PostMapping
public ResponseEntity<?> create(@RequestBody CreateWorkspaceRequest req);
// req: {path: "..."}，name + title 自动派生
// 200 / 400 (invalid_path) / 409 (already exists with same canonical path - 但我们让 DSH 兼容)

@DeleteMapping("/{name}")
public ResponseEntity<?> delete(@PathVariable String name);
// 204 / 404

@PatchMapping("/{name}")
public ResponseEntity<?> rename(@PathVariable String name, @RequestBody RenameRequest req);
// req: {title: "..."}
// 200 / 400 (title_invalid) / 404

@PutMapping("/order")
public ResponseEntity<?> updateOrder(@RequestBody OrderRequest req);
// req: {order: ["md-main", "agent-demo", ...]}
// 200 / 400 (order_invalid)
```

### 4.3 持久化

```jsonc
// workspaces/order.json
{
  "version": 1,
  "order": ["md-main", "agent-demo", "..."]
}

// workspaces/<name>/meta.json (v2)
{
  "version": 2,
  "name": "md-main",
  "id": "uuid-v4",
  "path": "/home/user/projects/md-main",
  "title": "md-main",
  "created_at": 1736700000000,
  "updated_at": 1736700000000,
  "session_ids": []
}
```

## 5. 行为兼容 / Migration

v1 meta.json 缺字段迁移：
- `id` = `UUID.randomUUID()`（一次性）
- `path` = `Paths.get(metaJson.dir).toRealPath()`（IO 异常则保留原 dir）
- `title` = `basename(path)`
- `updated_at` = `created_at`
- `session_ids` = []（启动时不自动 attach，避免破坏既有用户数据；后续可手动 attach 或自动按 cwd 推导）

v1 WorkspaceController `GET /api/workspaces` 返回兼容字段（`name` + `dir` + session count + lastActive）；v2 DTO 加 `id/title/updatedAt/status` 字段（前端可选用）。

v1 WorkspacePickerModal 用户填 name → v2 用户只填 dir（title = basename 自动派生）。

## 6. UI 改造

- `Sidebar.tsx`：每个 workspace 行加「`...`」菜单（删除 / 重命名）；工作区切换条加 drag handle（HTML5 drag-and-drop）；右上 `+` 改 menu（picker 单 action）
- `WorkspacePickerModal.tsx`：去掉「工作区名称」input，只留「文件夹」path

## 7. 线程模型

- `WorkspaceStore.create/delete/rename/insertBefore` 同步阻塞（File I/O 快）；多 agent 并发可能冲突 → 用 `<name>.lock` 文件锁
- `list()` 同步（无 I/O for status；status 检查用 Files.exists() 快）
- 启动时 `ensureOrderInitialized` 同步执行（一次性，读 header JSON list）

## 8. 测试

- `WorkspaceStoreTest`：v1 → v2 迁移 / realpath 复用 / 同 path 同 canonical / 不同 path 同 basename / delete 保留 session/dir / insertBefore / order 持久化 / missing-dir status
- `WorkspaceControllerTest`：5 个新端点 + DELETE / PATCH / PUT 错误码
- `WorkspacePickerModal.test.tsx`：去掉 name 输入步骤的断言
- `Sidebar.test.tsx`：右键菜单 + 拖拽重排
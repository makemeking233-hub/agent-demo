/**
 * workspace 异步 picker + v2 REST 客户端 (picker-async + align-dsh-workspace).
 */

export interface PickFolderStart {
  task_id: string;
  timeout_seconds: number;
}

export interface PickFolderStatus {
  status: "running" | "done" | "cancelled" | "timeout" | "error" | "invalid_path" | "interrupted" | "unknown";
  path?: string;
  reason?: string;
}

/** Workspace v2 record (align-dsh-workspace): id / title / status / updatedAt 与 v1 name/dir/sessionCount 合并。 */
export interface WorkspaceDto {
  name: string;
  id: string;
  title: string;
  dir: string;
  sessionCount: number;
  lastActiveAt: number;
  updatedAt: number;
  status: "ok" | "missing_dir";
}

const BASE = "/api/workspaces";

// ---------- picker (existing) ----------

export async function startPickFolder(): Promise<PickFolderStart> {
  const r = await fetch(`${BASE}/pick-folder`, { method: "POST" });
  if (r.status !== 202 && !r.ok) {
    throw new Error(`startPickFolder ${r.status}: ${await r.text()}`);
  }
  return (await r.json()) as PickFolderStart;
}

export async function pollPickFolder(taskId: string): Promise<PickFolderStatus> {
  const r = await fetch(`${BASE}/pick-folder/${encodeURIComponent(taskId)}`);
  if (!r.ok) throw new Error(`pollPickFolder ${r.status}`);
  return (await r.json()) as PickFolderStatus;
}

export async function cancelPickFolder(taskId: string): Promise<void> {
  await fetch(`${BASE}/pick-folder/${encodeURIComponent(taskId)}`, { method: "DELETE" });
}

// ---------- v2 REST (align-dsh-workspace) ----------

/** 列所有 workspace（默认 workspace 在前 + 按 durable order 排序）。 */
export async function listWorkspaces(): Promise<WorkspaceDto[]> {
  const r = await fetch(`${BASE}`);
  if (!r.ok) throw new Error(`listWorkspaces ${r.status}`);
  return (await r.json()) as WorkspaceDto[];
}

/**
 * 创建 workspace（DSH 单 action）：只需 path，name + title 由后端从 basename(path) 派生。
 * 同 canonical path 重复创建返回现有 record。
 */
export async function createWorkspace(path: string): Promise<WorkspaceDto> {
  const r = await fetch(`${BASE}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ path }),
  });
  if (!r.ok) {
    throw new Error(`createWorkspace ${r.status}: ${await r.text()}`);
  }
  const body = (await r.json()) as { ok: boolean; name: string; title: string; dir: string; id: string };
  return {
    name: body.name,
    id: body.id,
    title: body.title,
    dir: body.dir,
    sessionCount: 0,
    lastActiveAt: 0,
    updatedAt: Date.now(),
    status: "ok",
  };
}

/** 删除 workspace record（不动 dir / session log）。204=ok / 404=不存在 */
export async function deleteWorkspace(name: string): Promise<void> {
  const r = await fetch(`${BASE}/${encodeURIComponent(name)}`, { method: "DELETE" });
  if (r.status !== 204 && !r.ok) {
    throw new Error(`deleteWorkspace ${r.status}: ${await r.text()}`);
  }
}

/** 重命名 display title（不动 dir/path/name/id）。 */
export async function renameWorkspace(name: string, title: string): Promise<void> {
  const r = await fetch(`${BASE}/${encodeURIComponent(name)}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ title }),
  });
  if (!r.ok) {
    throw new Error(`renameWorkspace ${r.status}: ${await r.text()}`);
  }
}

/** 拖拽重排后一次性应用新顺序（不含默认 workspace）。 */
export async function updateWorkspaceOrder(order: string[]): Promise<void> {
  const r = await fetch(`${BASE}/order`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ order }),
  });
  if (!r.ok) {
    throw new Error(`updateWorkspaceOrder ${r.status}: ${await r.text()}`);
  }
}
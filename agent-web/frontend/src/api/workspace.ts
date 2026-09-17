/**
 * workspace 异步 picker API 客户端 (picker-async).
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

const BASE = "/api/workspaces";

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

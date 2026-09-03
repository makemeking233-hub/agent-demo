/**
 * /api/fs 客户端（add-workspace-picker-modal）。
 *
 * <p>前端 WorkspacePickerModal 通过该模块访问后端 fs API；错误统一抛 {@link FsError}，
 * Modal 内按 code 渲染中文错误条。
 */

export interface FsEntry {
  name: string;
  path: string;
  isDir: boolean;
  size: number;
  mtime: number;
}

export interface FsListResponse {
  path: string;
  parent: string | null;
  entries: FsEntry[];
}

export interface FsHomeResponse {
  path: string;
  platform: "windows" | "linux" | "mac";
}

export interface FsDrive {
  name: string;
  path: string;
}

export interface FsDrivesResponse {
  drives: FsDrive[];
}

export interface FsMkdirResponse {
  path: string;
}

/** /api/fs 调用错误（带 HTTP 状态码 + 服务端错误码）。 */
export class FsError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = "FsError";
  }
}

const BASE = "/api/fs";

async function parseError(res: Response): Promise<FsError> {
  let code = "unknown";
  let message = res.statusText;
  try {
    const body = await res.json();
    if (body && typeof body.error === "string") {
      code = body.error;
      message = body.message ?? code;
    }
  } catch {
    // body 不是 JSON；保持默认 statusText
  }
  return new FsError(res.status, code, message);
}

export async function getHome(): Promise<FsHomeResponse> {
  const res = await fetch(`${BASE}/home`);
  if (!res.ok) throw await parseError(res);
  return (await res.json()) as FsHomeResponse;
}

export async function listDir(
  path: string,
  includeHidden = false,
): Promise<FsListResponse> {
  const url = new URL(`${BASE}/list`, window.location.origin);
  url.searchParams.set("path", path);
  if (includeHidden) url.searchParams.set("includeHidden", "true");
  const res = await fetch(url.toString());
  if (!res.ok) throw await parseError(res);
  return (await res.json()) as FsListResponse;
}

export interface MkdirOptions {
  /** 是否创建中间目录（与 mkdir -p 类似）。默认 false。 */
  recursive?: boolean;
}

export async function mkdir(
  path: string,
  _options: MkdirOptions = {},
): Promise<FsMkdirResponse> {
  const res = await fetch(`${BASE}/mkdir`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ path }),
  });
  if (!res.ok) throw await parseError(res);
  return (await res.json()) as FsMkdirResponse;
}

export async function getDrives(): Promise<FsDrivesResponse> {
  const res = await fetch(`${BASE}/drives`);
  if (!res.ok) throw await parseError(res);
  return (await res.json()) as FsDrivesResponse;
}

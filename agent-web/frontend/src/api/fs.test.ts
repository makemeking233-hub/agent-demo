/**
 * /api/fs 客户端单测（add-workspace-picker-modal）。
 *
 * <p>mock fetch，验证 200/4xx/5xx 三个分支：
 * <ul>
 *   <li>200 → 返回解析后的 JSON；
 *   <li>4xx → 抛 {@link FsError}，code 从 body.error 提取；
 *   <li>5xx → 抛 {@link FsError}，code="unknown"，message 用 statusText。
 * </ul>
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  FsError,
  getDrives,
  getHome,
  getQuickAccess,
  listDir,
  mkdir,
  type FsListResponse,
} from "./fs";

const ORIGINAL_FETCH = global.fetch;

/**
 * 把 fetch mock 成"每次调用都返回新 Response"，避免 Response.body 单次消费问题。
 * 测试代码可以安全地在同一用例里多次调被测函数。
 */
function mockFetchJson(
  body: unknown,
  init: ResponseInit = { status: 200 },
): void {
  const payload = typeof body === "string" ? body : JSON.stringify(body);
  global.fetch = vi
    .fn()
    .mockImplementation(
      () => Promise.resolve(new Response(payload, init)),
    ) as unknown as typeof fetch;
}

/** 同上但每次返回空 body（用于 4xx 错误且不读 body 的场景）。 */
function mockFetchEmpty(status: number, statusText = ""): void {
  global.fetch = vi
    .fn()
    .mockImplementation(
      () => Promise.resolve(new Response(null, { status, statusText })),
    ) as unknown as typeof fetch;
}

beforeEach(() => {
  // 重新绑定每个测试的 mock
});

afterEach(() => {
  global.fetch = ORIGINAL_FETCH;
  vi.restoreAllMocks();
});

describe("getHome", () => {
  it("200 返回 home path + platform", async () => {
    mockFetchJson({ path: "C:\\Users\\86184", platform: "windows" }, { status: 200 });

    const out = await getHome();
    expect(out).toEqual({ path: "C:\\Users\\86184", platform: "windows" });
    expect(fetch).toHaveBeenCalledWith("/api/fs/home");
  });

  it("5xx 抛 FsError(code=unknown)", async () => {
    mockFetchEmpty(503, "Service Unavailable");
    await expect(getHome()).rejects.toMatchObject({
      status: 503,
      code: "unknown",
    });
  });
});

describe("listDir", () => {
  const okBody: FsListResponse = {
    path: "C:\\home\\projects",
    parent: "C:\\home",
    entries: [
      { name: "agent-demo", path: "C:\\home\\projects\\agent-demo", isDir: true, size: 0, mtime: 1700000000000 },
      { name: "README.md", path: "C:\\home\\projects\\README.md", isDir: false, size: 2048, mtime: 1700000001000 },
    ],
  };

  it("200 解析 entries", async () => {
    mockFetchJson(okBody, { status: 200 });

    const out = await listDir("C:\\home\\projects", false);
    expect(out.entries).toHaveLength(2);
    expect(out.entries[0].name).toBe("agent-demo");
  });

  it("includeHidden=true 查询串带 includeHidden=true", async () => {
    mockFetchJson(okBody, { status: 200 });
    await listDir("C:\\home", true);
    const url = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0][0] as string;
    expect(url).toMatch(/[?&]includeHidden=true/);
  });

  it("403 path_outside_home 抛 FsError", async () => {
    mockFetchJson({ error: "path_outside_home" }, { status: 403 });
    await expect(listDir("C:\\Windows")).rejects.toBeInstanceOf(FsError);
    await expect(listDir("C:\\Windows")).rejects.toMatchObject({
      status: 403,
      code: "path_outside_home",
    });
  });

  it("400 path_not_absolute 抛 FsError", async () => {
    mockFetchJson({ error: "path_not_absolute" }, { status: 400 });
    await expect(listDir("relative/path")).rejects.toMatchObject({
      status: 400,
      code: "path_not_absolute",
    });
  });

  it("404 path_not_found 抛 FsError", async () => {
    mockFetchJson({ error: "path_not_found" }, { status: 404 });
    await expect(listDir("C:\\nonexistent")).rejects.toMatchObject({
      status: 404,
      code: "path_not_found",
    });
  });
});

describe("mkdir", () => {
  it("200 返回 path", async () => {
    mockFetchJson({ path: "C:\\home\\new" }, { status: 200 });
    const out = await mkdir("C:\\home\\new");
    expect(out).toEqual({ path: "C:\\home\\new" });
  });

  it("409 dir_exists 抛 FsError", async () => {
    mockFetchJson({ error: "dir_exists" }, { status: 409 });
    await expect(mkdir("C:\\home\\existing")).rejects.toMatchObject({
      status: 409,
      code: "dir_exists",
    });
  });

  it("403 path_outside_home 抛 FsError", async () => {
    mockFetchJson({ error: "path_outside_home" }, { status: 403 });
    await expect(mkdir("C:\\Windows\\new")).rejects.toMatchObject({
      status: 403,
      code: "path_outside_home",
    });
  });
});

describe("getDrives", () => {
  it("Windows 返回 drives 数组", async () => {
    mockFetchJson({ drives: [{ name: "C:", path: "C:\\" }] }, { status: 200 });
    const out = await getDrives();
    expect(out.drives).toHaveLength(1);
    expect(out.drives[0].name).toBe("C:");
  });

  it("Linux 返回空数组", async () => {
    mockFetchJson({ drives: [] }, { status: 200 });
    const out = await getDrives();
    expect(out.drives).toEqual([]);
  });
});

describe("getQuickAccess", () => {
  it("200 返回 items 列表（含 Home + Desktop + Documents）", async () => {
    mockFetchJson(
      {
        items: [
          { name: "Home", path: "/home/user" },
          { name: "Desktop", path: "/home/user/Desktop" },
          { name: "Documents", path: "/home/user/Documents" },
        ],
      },
      { status: 200 },
    );
    const out = await getQuickAccess();
    expect(out.items).toHaveLength(3);
    expect(out.items[0].name).toBe("Home");
    expect(out.items[1].path).toBe("/home/user/Desktop");
  });

  it("仅 Home（家目录下无快速访问目录）", async () => {
    mockFetchJson({ items: [{ name: "Home", path: "/home/user" }] }, { status: 200 });
    const out = await getQuickAccess();
    expect(out.items).toHaveLength(1);
    expect(out.items[0].name).toBe("Home");
  });

  it("403 host_not_trusted 抛 FsError", async () => {
    mockFetchJson({ error: "host_not_trusted" }, { status: 403 });
    await expect(getQuickAccess()).rejects.toMatchObject({
      status: 403,
      code: "host_not_trusted",
    });
  });

  it("5xx 抛 FsError(code=unknown)", async () => {
    mockFetchEmpty(500, "Internal Server Error");
    await expect(getQuickAccess()).rejects.toMatchObject({
      status: 500,
      code: "unknown",
    });
  });
});

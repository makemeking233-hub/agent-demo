/**
 * workspace 异步 picker API 客户端测试 (picker-async).
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cancelPickFolder, pollPickFolder, startPickFolder } from "./workspace";

describe("workspace picker API", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("startPickFolder sends POST and returns task_id", async () => {
    fetchMock.mockResolvedValueOnce({
      status: 202,
      ok: true,
      json: async () => ({ task_id: "abc-123", timeout_seconds: 300 }),
    });
    const result = await startPickFolder();
    expect(result.task_id).toBe("abc-123");
    expect(result.timeout_seconds).toBe(300);
    expect(fetchMock).toHaveBeenCalledWith("/api/workspaces/pick-folder", { method: "POST" });
  });

  it("pollPickFolder sends GET with task_id", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ status: "done", path: "/x/y" }),
    });
    const result = await pollPickFolder("task-1");
    expect(result.status).toBe("done");
    expect(result.path).toBe("/x/y");
    expect(fetchMock).toHaveBeenCalledWith("/api/workspaces/pick-folder/task-1");
  });

  it("cancelPickFolder sends DELETE", async () => {
    fetchMock.mockResolvedValueOnce({ status: 204, ok: true });
    await cancelPickFolder("task-1");
    expect(fetchMock).toHaveBeenCalledWith("/api/workspaces/pick-folder/task-1", { method: "DELETE" });
  });

  it("startPickFolder throws on non-202", async () => {
    fetchMock.mockResolvedValueOnce({
      status: 500,
      ok: false,
      text: async () => "server error",
    });
    await expect(startPickFolder()).rejects.toThrow(/startPickFolder 500/);
  });
});

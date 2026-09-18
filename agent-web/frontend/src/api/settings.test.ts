/**
 * SettingsApi 单元测试 (add-settings-foundation M1).
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SettingsApi } from "./settings";

describe("SettingsApi", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("getSettings returns parsed JSON", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ version: 1, general: {}, revision: 0 }),
    });
    const api = new SettingsApi();
    const view = await api.getSettings();
    expect(view.version).toBe(1);
    expect(fetchMock).toHaveBeenCalledWith("/api/settings");
  });

  it("patch sends PATCH with body", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ version: 1, general: {}, revision: 1 }),
    });
    const api = new SettingsApi();
    await api.patch("general.appearance.preference", "dark", 0);
    expect(fetchMock).toHaveBeenCalledWith("/api/settings/general.appearance.preference", {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ value: "dark", revision: 0 }),
    });
  });

  it("patch without revision omits revision field", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ version: 1, general: {}, revision: 0 }),
    });
    const api = new SettingsApi();
    await api.patch("general.appearance.preference", "dark");
    const call = fetchMock.mock.calls[0];
    expect(JSON.parse(call[1].body)).toEqual({ value: "dark" });
  });

  it("patch on error throws SettingsError with status", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: false,
      status: 404,
      json: async () => ({ error: "path_not_found" }),
    });
    const api = new SettingsApi();
    await expect(api.patch("general.unknown", "x")).rejects.toMatchObject({
      status: 404,
      error: "path_not_found",
    });
  });

  it("getFilePath returns path", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ path: "/home/x/.agent-demo/settings.yaml" }),
    });
    const api = new SettingsApi();
    const path = await api.getFilePath();
    expect(path).toBe("/home/x/.agent-demo/settings.yaml");
  });

  it("eventsUrl returns /api/settings/events", () => {
    const api = new SettingsApi();
    expect(api.eventsUrl()).toBe("/api/settings/events");
  });

  it("reveal returns revealed + path", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ revealed: true, path: "/home/x/settings.yaml" }),
    });
    const api = new SettingsApi();
    const r = await api.reveal();
    expect(r.revealed).toBe(true);
    expect(r.path).toBe("/home/x/settings.yaml");
  });
});

/**
 * WorkspacePickerModal 异步 picker 测试 (picker-async).
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { WorkspacePickerModal } from "./WorkspacePickerModal";

describe("WorkspacePickerModal (async)", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    localStorage.clear();
    fetchMock = vi.fn();
    fetchMock.mockImplementation(async () => ({
      status: 200,
      ok: true,
      json: async () => ({ status: "running" }),
    }));
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  function renderModal(overrides: Partial<Parameters<typeof WorkspacePickerModal>[0]> = {}) {
    const props = {
      open: true,
      onClose: vi.fn(),
      onSubmit: vi.fn().mockResolvedValue(undefined),
      ...overrides,
    };
    return { ...props, ...render(<WorkspacePickerModal {...props} />) };
  }

  it("renders dialog with title", () => {
    renderModal();
    expect(screen.getByRole("dialog", { name: "选择工作区目录" })).toBeInTheDocument();
    expect(screen.getByText("Select Workspace Directory")).toBeInTheDocument();
  });

  it("renders pick folder + reveal buttons; submit disabled initially", () => {
    renderModal();
    expect(screen.getByTestId("wp-pick-folder")).toBeInTheDocument();
    expect(screen.getByTestId("wp-reveal")).toBeInTheDocument();
    expect((screen.getByTestId("wp-submit") as HTMLButtonElement).disabled).toBe(true);
  });

  it("clicking pick button sends POST and receives task_id", async () => {
    fetchMock.mockResolvedValueOnce({
      status: 202,
      ok: true,
      json: async () => ({ task_id: "task-1", timeout_seconds: 300 }),
    });
    renderModal();
    fireEvent.click(screen.getByTestId("wp-pick-folder"));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith("/api/workspaces/pick-folder", expect.objectContaining({ method: "POST" })),
    );
  });

  it("polls until status=done and fills path + basename", async () => {
    // 前两次：POST 返回 task_id + poll 返回 running；第三次：done
    fetchMock
      .mockResolvedValueOnce({ status: 202, ok: true, json: async () => ({ task_id: "task-1", timeout_seconds: 300 }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ status: "done", path: "/Users/me/projects" }) });
    renderModal();
    fireEvent.click(screen.getByTestId("wp-pick-folder"));
    await waitFor(() =>
      expect(screen.getByTestId("wp-path-input")).toHaveValue("/Users/me/projects"),
    );
    expect(screen.getByTestId("wp-name-input")).toHaveValue("projects");
  });

  it("cancel status does not show error", async () => {
    fetchMock
      .mockResolvedValueOnce({ status: 202, ok: true, json: async () => ({ task_id: "task-1" }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ status: "cancelled" }) });
    renderModal();
    fireEvent.click(screen.getByTestId("wp-pick-folder"));
    await waitFor(() =>
      expect(screen.queryByText(/操作超时|操作失败/)).toBeNull(),
    );
  });

  it("timeout status shows error", async () => {
    fetchMock
      .mockResolvedValueOnce({ status: 202, ok: true, json: async () => ({ task_id: "task-1" }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ status: "timeout" }) });
    renderModal();
    fireEvent.click(screen.getByTestId("wp-pick-folder"));
    await waitFor(() => expect(screen.getByText(/操作超时/)).toBeInTheDocument());
  });

  it("invalid_path status shows error", async () => {
    fetchMock
      .mockResolvedValueOnce({ status: 202, ok: true, json: async () => ({ task_id: "task-1" }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ status: "invalid_path" }) });
    renderModal();
    fireEvent.click(screen.getByTestId("wp-pick-folder"));
    await waitFor(() => expect(screen.getByText(/选定路径无效/)).toBeInTheDocument());
  });

  it("reveal button calls /api/settings/reveal", async () => {
    fetchMock.mockResolvedValueOnce({ ok: true, json: async () => ({ revealed: true, path: "/x" }) });
    renderModal();
    fireEvent.click(screen.getByTestId("wp-reveal"));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith("/api/settings/reveal", expect.objectContaining({ method: "POST" })),
    );
  });

  it("Esc closes modal", () => {
    const onClose = vi.fn();
    renderModal({ onClose });
    fireEvent.keyDown(window, { key: "Escape" });
    expect(onClose).toHaveBeenCalled();
  });

  it("click overlay closes modal", () => {
    const onClose = vi.fn();
    const { container } = renderModal({ onClose });
    const overlay = container.firstChild as HTMLElement;
    fireEvent.click(overlay);
    expect(onClose).toHaveBeenCalled();
  });

  it("renders nothing when closed", () => {
    const { container } = renderModal({ open: false });
    expect(container.firstChild).toBeNull();
  });

  it("user can override default name", async () => {
    fetchMock
      .mockResolvedValueOnce({ status: 202, ok: true, json: async () => ({ task_id: "task-1" }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ status: "done", path: "/Users/me/projects" }) });
    renderModal();
    fireEvent.click(screen.getByTestId("wp-pick-folder"));
    await waitFor(() => expect(screen.getByTestId("wp-path-input")).toHaveValue("/Users/me/projects"));
    await waitFor(() => expect(screen.getByTestId("wp-name-input")).toHaveValue("projects"));
    fireEvent.change(screen.getByTestId("wp-name-input"), { target: { value: "my-ws" } });
    expect(screen.getByTestId("wp-name-input")).toHaveValue("my-ws");
  });

  it("submit enables with valid name + path", async () => {
    fetchMock
      .mockResolvedValueOnce({ status: 202, ok: true, json: async () => ({ task_id: "task-1" }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ status: "done", path: "/x/y" }) });
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const onClose = vi.fn();
    renderModal({ onSubmit, onClose });
    fireEvent.click(screen.getByTestId("wp-pick-folder"));
    await waitFor(() => expect(screen.getByTestId("wp-path-input")).toHaveValue("/x/y"));
    fireEvent.change(screen.getByTestId("wp-name-input"), { target: { value: "my-ws" } });
    fireEvent.click(screen.getByTestId("wp-submit"));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith("my-ws", "/x/y"));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });

  it("persists picked path to localStorage", async () => {
    fetchMock
      .mockResolvedValueOnce({ status: 202, ok: true, json: async () => ({ task_id: "task-1" }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ status: "done", path: "/Users/me/projects" }) });
    renderModal();
    fireEvent.click(screen.getByTestId("wp-pick-folder"));
    await waitFor(() => expect(screen.getByTestId("wp-path-input")).toHaveValue("/Users/me/projects"));
    expect(localStorage.getItem("agent-demo.workspace-picker.last-path")).toBe("/Users/me/projects");
  });
});

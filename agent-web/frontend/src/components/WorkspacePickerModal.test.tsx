/**
 * WorkspacePickerModal v2 测试 (picker-async + align-dsh-workspace):
 * DSH 单 action — 只传 path，name + title 由后端派生。
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { WorkspacePickerModal } from "./WorkspacePickerModal";

describe("WorkspacePickerModal (v2 single-action)", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    localStorage.clear();
    fetchMock = vi.fn();
    fetchMock.mockImplementation(async () => ({
      status: 200,
      ok: true,
      json: async () => ({}),
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

  it("renders path input + pick button + reveal; no name input (DSH single-action); submit disabled when path empty", () => {
    renderModal();
    expect(screen.getByTestId("wp-path-input")).toBeInTheDocument();
    expect(screen.getByTestId("wp-pick-folder")).toBeInTheDocument();
    expect(screen.getByTestId("wp-reveal")).toBeInTheDocument();
    // v2 不再需要 name input（后端自动派生）
    expect(screen.queryByTestId("wp-name-input")).toBeNull();
    expect((screen.getByTestId("wp-submit") as HTMLButtonElement).disabled).toBe(true);
  });

  it("clicking pick button sends POST /api/workspaces/pick-folder", async () => {
    fetchMock.mockResolvedValueOnce({
      status: 202,
      ok: true,
      json: async () => ({ task_id: "task-1", timeout_seconds: 300 }),
    });
    renderModal();
    fireEvent.click(screen.getByTestId("wp-pick-folder"));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        "/api/workspaces/pick-folder",
        expect.objectContaining({ method: "POST" }),
      ),
    );
  });

  it("polls until status=done and fills path", async () => {
    fetchMock
      .mockResolvedValueOnce({ status: 202, ok: true, json: async () => ({ task_id: "task-1" }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ status: "done", path: "/Users/me/projects" }) });
    renderModal();
    fireEvent.click(screen.getByTestId("wp-pick-folder"));
    await waitFor(() =>
      expect(screen.getByTestId("wp-path-input")).toHaveValue("/Users/me/projects"),
    );
  });

  it("restore last path from localStorage", () => {
    localStorage.setItem("agent-demo.workspace-picker.last-path", "C:\\Users\\test\\projects");
    renderModal();
    expect(screen.getByTestId("wp-path-input")).toHaveValue("C:\\Users\\test\\projects");
  });

  it("reveal button calls /api/settings/reveal", async () => {
    renderModal();
    fireEvent.click(screen.getByTestId("wp-reveal"));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        "/api/settings/reveal",
        expect.objectContaining({ method: "POST" }),
      ),
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

  it("submit calls onSubmit with path only (v2 single-action, no name)", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const onClose = vi.fn();
    renderModal({ onSubmit, onClose });
    fireEvent.change(screen.getByTestId("wp-path-input"), {
      target: { value: "C:\\Users\\test\\projects" },
    });
    fireEvent.click(screen.getByTestId("wp-submit"));
    // v2: onSubmit 只接收 path
    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith("C:\\Users\\test\\projects"),
    );
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });

  it("submit disabled when path empty", () => {
    renderModal();
    expect((screen.getByTestId("wp-submit") as HTMLButtonElement).disabled).toBe(true);
    fireEvent.change(screen.getByTestId("wp-path-input"), { target: { value: "C:\\x\\y" } });
    expect((screen.getByTestId("wp-submit") as HTMLButtonElement).disabled).toBe(false);
  });

  it("submit failure shows error", async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error("dir_not_found"));
    renderModal({ onSubmit });
    fireEvent.change(screen.getByTestId("wp-path-input"), { target: { value: "C:\\nope" } });
    fireEvent.click(screen.getByTestId("wp-submit"));
    await waitFor(() => expect(screen.getByText(/dir_not_found/)).toBeInTheDocument());
  });
});
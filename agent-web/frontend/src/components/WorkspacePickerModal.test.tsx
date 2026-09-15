/**
 * WorkspacePickerModal 简化版测试（native-folder-picker）。
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { WorkspacePickerModal } from "./WorkspacePickerModal";

describe("WorkspacePickerModal", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    localStorage.clear();
    fetchMock = vi.fn();
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

  it("renders pick folder button and disabled submit initially", () => {
    renderModal();
    expect(screen.getByTestId("wp-pick-folder")).toBeInTheDocument();
    expect((screen.getByTestId("wp-submit") as HTMLButtonElement).disabled).toBe(true);
  });

  it("clicking pick button calls POST /api/workspaces/pick-folder", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ path: "/Users/me/projects", reason: "" }),
    });
    renderModal();
    fireEvent.click(screen.getByTestId("wp-pick-folder"));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith("/api/workspaces/pick-folder", expect.objectContaining({ method: "POST" })));
    await waitFor(() => expect(screen.getByTestId("wp-path-input")).toHaveValue("/Users/me/projects"));
  });

  it("after picking folder, name defaults to basename", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ path: "/Users/me/projects", reason: "" }),
    });
    renderModal();
    fireEvent.click(screen.getByTestId("wp-pick-folder"));
    await waitFor(() =>
      expect(screen.getByTestId("wp-name-input")).toHaveValue("projects"),
    );
  });

  it("user can override default name", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ path: "/Users/me/projects", reason: "" }),
    });
    renderModal();
    fireEvent.click(screen.getByTestId("wp-pick-folder"));
    await waitFor(() => expect(screen.getByTestId("wp-name-input")).toHaveValue("projects"));
    fireEvent.change(screen.getByTestId("wp-name-input"), { target: { value: "my-ws" } });
    expect(screen.getByTestId("wp-name-input")).toHaveValue("my-ws");
  });

  it("submit disabled with invalid name (contains space)", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ path: "/x/y", reason: "" }),
    });
    renderModal();
    fireEvent.click(screen.getByTestId("wp-pick-folder"));
    await waitFor(() => expect(screen.getByTestId("wp-path-input")).toHaveValue("/x/y"));
    fireEvent.change(screen.getByTestId("wp-name-input"), { target: { value: "bad name" } });
    expect((screen.getByTestId("wp-submit") as HTMLButtonElement).disabled).toBe(true);
  });

  it("submit enables with valid name + path", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ path: "/x/y", reason: "" }),
    });
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

  it("submit failure shows error", async () => {
    fetchMock.mockResolvedValueOnce({ ok: true, json: async () => ({ path: "/x/y", reason: "" }) });
    const onSubmit = vi.fn().mockRejectedValue(new Error("workspace_exists"));
    renderModal({ onSubmit });
    fireEvent.click(screen.getByTestId("wp-pick-folder"));
    await waitFor(() => expect(screen.getByTestId("wp-path-input")).toHaveValue("/x/y"));
    fireEvent.change(screen.getByTestId("wp-name-input"), { target: { value: "my-ws" } });
    fireEvent.click(screen.getByTestId("wp-submit"));
    await waitFor(() => expect(screen.getByText(/workspace_exists/)).toBeInTheDocument());
  });

  it("timeout shows error message", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ path: "", reason: "timeout" }),
    });
    renderModal();
    fireEvent.click(screen.getByTestId("wp-pick-folder"));
    await waitFor(() => expect(screen.getByText(/操作超时/)).toBeInTheDocument());
  });

  it("cancel (no path, reason=cancelled) does not show error", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ path: "", reason: "cancelled" }),
    });
    renderModal();
    fireEvent.click(screen.getByTestId("wp-pick-folder"));
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(screen.queryByText(/超时/)).toBeNull();
    expect(screen.queryByText(/失败/)).toBeNull();
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

  it("persists picked path to localStorage", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ path: "/Users/me/projects", reason: "" }),
    });
    renderModal();
    fireEvent.click(screen.getByTestId("wp-pick-folder"));
    await waitFor(() => expect(screen.getByTestId("wp-path-input")).toHaveValue("/Users/me/projects"));
    expect(localStorage.getItem("agent-demo.workspace-picker.last-path")).toBe("/Users/me/projects");
  });
});

/**
 * WorkspacePickerModal 路径输入 + reveal 测试 (picker-reveal-only).
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { WorkspacePickerModal } from "./WorkspacePickerModal";

describe("WorkspacePickerModal (reveal-only)", () => {
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

  it("renders path + name inputs and reveal button; submit disabled when path empty", () => {
    renderModal();
    expect(screen.getByTestId("wp-path-input")).toBeInTheDocument();
    expect(screen.getByTestId("wp-name-input")).toBeInTheDocument();
    expect(screen.getByTestId("wp-reveal")).toBeInTheDocument();
    expect((screen.getByTestId("wp-submit") as HTMLButtonElement).disabled).toBe(true);
  });

  it("does NOT render the pick folder button (picker dialog removed)", () => {
    renderModal();
    expect(screen.queryByTestId("wp-pick-folder")).toBeNull();
  });

  it("restore last path from localStorage", () => {
    localStorage.setItem("agent-demo.workspace-picker.last-path", "C:\\Users\\test\\projects");
    renderModal();
    expect(screen.getByTestId("wp-path-input")).toHaveValue("C:\\Users\\test\\projects");
  });

  it("typing path auto-fills basename as name (when name is empty)", () => {
    renderModal();
    const pathInput = screen.getByTestId("wp-path-input") as HTMLInputElement;
    fireEvent.change(pathInput, { target: { value: "C:\\Users\\test\\projects\\md-main" } });
    expect(screen.getByTestId("wp-name-input")).toHaveValue("md-main");
  });

  it("does not overwrite user-edited name when path changes", () => {
    renderModal();
    const pathInput = screen.getByTestId("wp-path-input") as HTMLInputElement;
    const nameInput = screen.getByTestId("wp-name-input") as HTMLInputElement;
    fireEvent.change(pathInput, { target: { value: "C:\\x\\y" } });
    fireEvent.change(nameInput, { target: { value: "my-name" } });
    fireEvent.change(pathInput, { target: { value: "C:\\a\\b" } });
    expect(nameInput).toHaveValue("my-name");
  });

  it("reveal button calls /api/settings/reveal", async () => {
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

  it("submit enables with valid name + path", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const onClose = vi.fn();
    renderModal({ onSubmit, onClose });
    fireEvent.change(screen.getByTestId("wp-path-input"), {
      target: { value: "C:\\x\\y" },
    });
    fireEvent.change(screen.getByTestId("wp-name-input"), {
      target: { value: "my-ws" },
    });
    fireEvent.click(screen.getByTestId("wp-submit"));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith("my-ws", "C:\\x\\y"));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });

  it("submit disabled with invalid name (contains space)", () => {
    renderModal();
    fireEvent.change(screen.getByTestId("wp-path-input"), { target: { value: "C:\\x\\y" } });
    fireEvent.change(screen.getByTestId("wp-name-input"), { target: { value: "bad name" } });
    expect((screen.getByTestId("wp-submit") as HTMLButtonElement).disabled).toBe(true);
  });

  it("submit failure shows error", async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error("workspace_exists"));
    renderModal({ onSubmit });
    fireEvent.change(screen.getByTestId("wp-path-input"), { target: { value: "C:\\x\\y" } });
    fireEvent.change(screen.getByTestId("wp-name-input"), { target: { value: "my-ws" } });
    fireEvent.click(screen.getByTestId("wp-submit"));
    await waitFor(() => expect(screen.getByText(/workspace_exists/)).toBeInTheDocument());
  });
});

/**
 * SettingsModal 组件测试 (add-settings-foundation M1 + M3).
 */

import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ChatApi } from "../api/chat";
import { SettingsModal } from "./SettingsModal";

const mockApi = {
  listModels: vi.fn().mockResolvedValue({
    providers: [],
    defaultProvider: "deepseek",
    defaultModel: "deepseek-chat",
  }),
} as unknown as ChatApi;

const defaultProps = {
  api: mockApi,
  selection: { provider: "deepseek", model: "deepseek-chat", reasoningEffort: "medium" as const },
  reasoningEfforts: [],
  onSelectionChange: vi.fn(),
  onReasoningEffortChange: vi.fn(),
};

describe("SettingsModal", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders nothing when closed", () => {
    const { container } = render(<SettingsModal open={false} onClose={() => {}} {...defaultProps} />);
    expect(container.querySelector('[data-testid="settings-modal"]')).toBeNull();
  });

  it("renders 4 nav items and the title when open", () => {
    render(<SettingsModal open={true} onClose={() => {}} {...defaultProps} />);
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText("设置")).toBeInTheDocument();
    expect(screen.getByTestId("settings-nav-general")).toBeInTheDocument();
    expect(screen.getByTestId("settings-nav-models")).toBeInTheDocument();
    expect(screen.getByTestId("settings-nav-plugins")).toBeInTheDocument();
    expect(screen.getByTestId("settings-nav-agent-presets")).toBeInTheDocument();
  });

  it("marks default active item with aria-current", () => {
    render(<SettingsModal open={true} onClose={() => {}} {...defaultProps} />);
    const general = screen.getByTestId("settings-nav-general");
    expect(general.getAttribute("aria-current")).toBe("true");
  });

  it("clicking a nav item updates aria-current", async () => {
    const user = userEvent.setup();
    render(<SettingsModal open={true} onClose={() => {}} {...defaultProps} />);
    await user.click(screen.getByTestId("settings-nav-models"));
    expect(screen.getByTestId("settings-nav-models").getAttribute("aria-current")).toBe("true");
    expect(screen.getByTestId("settings-nav-general").getAttribute("aria-current")).toBeNull();
  });

  it("clicking the close button calls onClose", async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(<SettingsModal open={true} onClose={onClose} {...defaultProps} />);
    await user.click(screen.getByRole("button", { name: "关闭" }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("clicking the mask calls onClose", () => {
    const onClose = vi.fn();
    const { container } = render(<SettingsModal open={true} onClose={onClose} {...defaultProps} />);
    const mask = container.querySelector('[aria-hidden="true"]');
    expect(mask).toBeTruthy();
    fireEvent.click(mask!);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("pressing Escape calls onClose", async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(<SettingsModal open={true} onClose={onClose} {...defaultProps} />);
    await user.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("renders the general content placeholder by default", () => {
    render(<SettingsModal open={true} onClose={() => {}} {...defaultProps} />);
    expect(screen.getByTestId("settings-content-general")).toBeInTheDocument();
  });

  it("renders a different content placeholder when nav item switches", async () => {
    const user = userEvent.setup();
    render(<SettingsModal open={true} onClose={() => {}} {...defaultProps} />);
    await user.click(screen.getByTestId("settings-nav-models"));
    expect(screen.getByTestId("settings-content-models")).toBeInTheDocument();
  });
});

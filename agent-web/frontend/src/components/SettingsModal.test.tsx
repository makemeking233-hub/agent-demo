/**
 * SettingsModal 组件测试 (add-settings-foundation M1 + M3
 * → shadcn-components-p1: 用 shadcn Dialog).
 *
 * <p>这里用**真实** shadcn Dialog（不再 mock）：重新从 shadcn registry 拉取的
 * 组件是规范多行 JSX，`DialogPrimitive.Close asChild` 只有一个元素子节点，
 * Radix Slot 的 "failed to slot onto its children" 问题不复存在
 * （该问题源于早期用 esbuild 重新格式化把 JSX 压成多文本节点）。
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
    render(<SettingsModal open={false} onClose={() => {}} {...defaultProps} />);
    expect(screen.queryByTestId("settings-modal")).toBeNull();
  });

  it("renders 4 nav items and the title when open", () => {
    render(<SettingsModal open={true} onClose={() => {}} {...defaultProps} />);
    expect(screen.getByTestId("settings-modal")).toBeInTheDocument();
    expect(screen.getByText("设置")).toBeInTheDocument();
    expect(screen.getByTestId("settings-nav-general")).toBeInTheDocument();
    expect(screen.getByTestId("settings-nav-models")).toBeInTheDocument();
    expect(screen.getByTestId("settings-nav-plugins")).toBeInTheDocument();
    expect(screen.getByTestId("settings-nav-agent-presets")).toBeInTheDocument();
  });

  it("renders role=dialog (Radix DialogContent)", () => {
    render(<SettingsModal open={true} onClose={() => {}} {...defaultProps} />);
    expect(screen.getByRole("dialog")).toBeInTheDocument();
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

  it("pressing Escape calls onClose（Radix Dialog 默认行为）", async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(<SettingsModal open={true} onClose={onClose} {...defaultProps} />);
    await user.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalled();
  });

  it("clicking the overlay calls onClose", async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    const { baseElement } = render(
      <SettingsModal open={true} onClose={onClose} {...defaultProps} />,
    );
    // Radix DismissableLayer 通过 pointerdown 判定外部点击，
    // 单发 fireEvent.click 不触发；用 userEvent 走完整 pointer 序列。
    const overlay = baseElement.querySelector('[data-slot="dialog-overlay"]');
    expect(overlay).toBeTruthy();
    await user.pointer({ keys: "[MouseLeft]", target: overlay as Element });
    expect(onClose).toHaveBeenCalled();
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
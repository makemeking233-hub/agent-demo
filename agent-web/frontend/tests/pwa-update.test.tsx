/**
 * PwaUpdatePrompt 组件测试（add-pwa-support）。
 * mock usePwaUpdate 验证 Snackbar 渲染 + 立即刷新按钮点击触发 update()。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";

const mockUsePwaUpdate = vi.hoisted(() => ({
  needRefresh: { value: false },
  update: vi.fn().mockResolvedValue(undefined),
}));

vi.mock("../src/hooks/usePwaUpdate", () => ({
  usePwaUpdate: () => ({
    needRefresh: mockUsePwaUpdate.needRefresh.value,
    update: mockUsePwaUpdate.update,
  }),
}));

import { PwaUpdatePrompt } from "../src/components/PwaUpdatePrompt";

describe("PwaUpdatePrompt", () => {
  beforeEach(() => {
    mockUsePwaUpdate.needRefresh.value = false;
    mockUsePwaUpdate.update.mockClear();
  });
  afterEach(() => cleanup());

  it("needRefresh=false 时不渲染", () => {
    mockUsePwaUpdate.needRefresh.value = false;
    const { container } = render(<PwaUpdatePrompt />);
    expect(container.firstChild).toBeNull();
  });

  it("needRefresh=true 时显示 Snackbar", () => {
    mockUsePwaUpdate.needRefresh.value = true;
    render(<PwaUpdatePrompt />);
    expect(screen.getByText("检测到新版本")).toBeInTheDocument();
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("点击'立即刷新'触发 update()", () => {
    mockUsePwaUpdate.needRefresh.value = true;
    render(<PwaUpdatePrompt />);
    fireEvent.click(screen.getByRole("button", { name: "立即刷新" }));
    expect(mockUsePwaUpdate.update).toHaveBeenCalledTimes(1);
  });
});
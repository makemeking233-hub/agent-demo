/**
 * SettingsNav 组件测试 (add-settings-foundation M1).
 */

import { fireEvent, render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { SettingsNav, type SettingsNavItem } from "./SettingsNav";

const ITEMS: SettingsNavItem[] = [
  { id: "general", label: "通用设置" },
  { id: "models", label: "模型" },
];

describe("SettingsNav", () => {
  it("renders all items", () => {
    const { getByText } = render(<SettingsNav items={ITEMS} activeId="general" onSelect={() => {}} />);
    expect(getByText("通用设置")).toBeInTheDocument();
    expect(getByText("模型")).toBeInTheDocument();
  });

  it("marks active item with aria-current", () => {
    const { getByTestId } = render(<SettingsNav items={ITEMS} activeId="general" onSelect={() => {}} />);
    expect(getByTestId("settings-nav-general").getAttribute("aria-current")).toBe("true");
    expect(getByTestId("settings-nav-models").getAttribute("aria-current")).toBeNull();
  });

  it("invokes onSelect when clicked", () => {
    const onSelect = vi.fn();
    const { getByTestId } = render(<SettingsNav items={ITEMS} activeId="general" onSelect={onSelect} />);
    fireEvent.click(getByTestId("settings-nav-models"));
    expect(onSelect).toHaveBeenCalledWith("models");
  });
});

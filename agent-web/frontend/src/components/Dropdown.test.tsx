import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { Dropdown } from "./Dropdown";

describe("Dropdown", () => {
  const options = [
    { label: "Low", value: "low" },
    { label: "Medium", value: "medium" },
    { label: "High", value: "high" },
  ];

  it("renders trigger with current value label", () => {
    render(<Dropdown options={options} value="medium" onChange={() => {}} ariaLabel="test" />);
    expect(screen.getByRole("button", { name: "test" })).toHaveTextContent("Medium");
  });

  it("renders placeholder when value is empty", () => {
    render(<Dropdown options={options} value="" onChange={() => {}} placeholder="选择..." />);
    expect(screen.getByRole("button")).toHaveTextContent("选择...");
  });

  it("opens menu on click and triggers onChange on select", () => {
    const onChange = vi.fn();
    render(<Dropdown options={options} value="low" onChange={onChange} ariaLabel="effort" />);
    fireEvent.click(screen.getByRole("button", { name: "effort" }));
    // 菜单展开后能看到 High 选项
    fireEvent.click(screen.getByRole("option", { name: "High" }));
    expect(onChange).toHaveBeenCalledWith("high");
  });

  it("closes menu when clicking outside", () => {
    render(
      <div>
        <Dropdown options={options} value="medium" onChange={() => {}} ariaLabel="effort" />
        <button data-testid="outside">outside</button>
      </div>
    );
    fireEvent.click(screen.getByRole("button", { name: "effort" }));
    expect(screen.getByRole("listbox")).toBeTruthy();
    fireEvent.mouseDown(screen.getByTestId("outside"));
    expect(screen.queryByRole("listbox")).toBeNull();
  });

  it("closes menu on Escape key", () => {
    render(<Dropdown options={options} value="medium" onChange={() => {}} ariaLabel="effort" />);
    const trigger = screen.getByRole("button", { name: "effort" });
    fireEvent.click(trigger);
    expect(screen.getByRole("listbox")).toBeTruthy();
    fireEvent.keyDown(screen.getByRole("listbox"), { key: "Escape" });
    expect(screen.queryByRole("listbox")).toBeNull();
  });

  it("supports ArrowDown / ArrowUp /Enter keyboard navigation", () => {
    const onChange = vi.fn();
    render(<Dropdown options={options} value="medium" onChange={onChange} ariaLabel="effort" />);
    fireEvent.click(screen.getByRole("button", { name: "effort" }));
    const listbox = screen.getByRole("listbox");
    // ArrowDown 移动焦点
    fireEvent.keyDown(listbox, { key: "ArrowDown" });
    fireEvent.keyDown(listbox, { key: "ArrowDown" });
    // Enter 选中当前 focusIndex=2 (high)
    fireEvent.keyDown(listbox, { key: "Enter" });
    expect(onChange).toHaveBeenCalledWith("high");
  });

  it("disabled trigger cannot be clicked", () => {
    const onChange = vi.fn();
    render(
      <Dropdown options={options} value="low" onChange={onChange} ariaLabel="effort" disabled />
    );
    const btn = screen.getByRole("button", { name: "effort" }) as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
    fireEvent.click(btn);
    expect(screen.queryByRole("listbox")).toBeNull();
  });
});
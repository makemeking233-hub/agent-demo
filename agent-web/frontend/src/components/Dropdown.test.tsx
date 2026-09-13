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
    // 展开时高亮定位到当前选中项（medium，索引 1）——鼠标点击与键盘打开走同一条路径。
    // 因此一次 ↓ 即落到索引 2 的 high（此处原先写的是两次 ↓，其隐含前提是"展开时高亮第一项"，
    // 与组件实际的键盘分支行为不一致）。
    fireEvent.keyDown(listbox, { key: "ArrowDown" });
    fireEvent.keyDown(listbox, { key: "Enter" });
    expect(onChange).toHaveBeenCalledWith("high");
  });

  it("ArrowUp 从第一项回绕到最后一项", () => {
    const onChange = vi.fn();
    render(<Dropdown options={options} value="low" onChange={onChange} ariaLabel="effort" />);
    fireEvent.click(screen.getByRole("button", { name: "effort" }));
    const listbox = screen.getByRole("listbox");
    // low 是索引 0，一次 ↑ 回绕到索引 2 的 high
    fireEvent.keyDown(listbox, { key: "ArrowUp" });
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
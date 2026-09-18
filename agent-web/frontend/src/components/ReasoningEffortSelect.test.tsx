import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ReasoningEffort } from "../api/chat";
import { ReasoningEffortSelect } from "./ReasoningEffortSelect";

/**
 * ReasoningEffortSelect prop 升级（add-provider-catalog-abstract task 10.3）。
 *
 * <p>prop 从 `model: ModelEntry` 改为 `options: ReasoningEffort[]`。
 * 覆盖：options 渲染 / value 对应 label 选中 / 空 options 返回 null / onChange 回调。
 */
describe("ReasoningEffortSelect（options prop）", () => {
  afterEach(() => cleanup());

  const OPTIONS: ReasoningEffort[] = [
    { id: "low", name: "Low" },
    { id: "medium", name: "Medium", description: "默认档" },
    { id: "high", name: "High" },
  ];

  it("空 options 时返回 null（不渲染）", () => {
    const { container } = render(
      <ReasoningEffortSelect options={[]} value="medium" onChange={vi.fn()} />,
    );
    expect(container.firstChild).toBeNull();
  });

  it("从 options 渲染 label（思考 + name）", async () => {
    render(<ReasoningEffortSelect options={OPTIONS} value="medium" onChange={vi.fn()} />);
    const trigger = await screen.findByRole("button", { name: "思考强度" });
    expect(trigger.textContent).toContain("思考 Medium");
  });

  it("value 对应 label 正确显示（high）", async () => {
    render(<ReasoningEffortSelect options={OPTIONS} value="high" onChange={vi.fn()} />);
    const trigger = await screen.findByRole("button", { name: "思考强度" });
    expect(trigger.textContent).toContain("思考 High");
  });

  it("打开下拉后可选项来自 options", async () => {
    render(<ReasoningEffortSelect options={OPTIONS} value="low" onChange={vi.fn()} />);
    fireEvent.click(await screen.findByRole("button", { name: "思考强度" }));
    // 注意：trigger 也含 "思考 Low" 文案，故用 role=option 精确定位下拉项
    const opts = screen.getAllByRole("option");
    expect(opts.map((o) => o.textContent)).toEqual(["思考 Low", "思考 Medium", "思考 High"]);
  });

  it("切换档位触发 onChange（传 id）", async () => {
    const onChange = vi.fn();
    render(<ReasoningEffortSelect options={OPTIONS} value="low" onChange={onChange} />);
    fireEvent.click(await screen.findByRole("button", { name: "思考强度" }));
    const high = screen.getAllByRole("option").find((o) => o.textContent === "思考 High");
    fireEvent.click(high!);
    expect(onChange).toHaveBeenCalledWith("high");
  });

  it("只支持单档位时仍渲染并可选中", async () => {
    const single: ReasoningEffort[] = [{ id: "medium", name: "Medium" }];
    render(<ReasoningEffortSelect options={single} value="medium" onChange={vi.fn()} />);
    const trigger = await screen.findByRole("button", { name: "思考强度" });
    expect(trigger.textContent).toContain("思考 Medium");
  });
});

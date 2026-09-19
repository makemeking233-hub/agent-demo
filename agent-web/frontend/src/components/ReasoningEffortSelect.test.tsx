import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ReasoningEffort } from "../api/chat";
import { ReasoningEffortSelect } from "./ReasoningEffortSelect";

/**
 * ReasoningEffortSelect（shadcn-prototype：用 Popover + RadioGroup 重写）。
 *
 * <p>trigger 用 shadcn Button，每个档位项是 Radix RadioGroupItem 渲染出的 button
 * （shadcn 把 item 渲染为 type="button" value=id，而非 role="option"）。
 * 改用 role="radio" 精确定位。
 */
describe("ReasoningEffortSelect（shadcn Popover + RadioGroup）", () => {
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

  it("trigger 显示 current.name（避免与下拉项文案撞车）", async () => {
    render(<ReasoningEffortSelect options={OPTIONS} value="medium" onChange={vi.fn()} />);
    const trigger = await screen.findByRole("button", { name: "思考强度" });
    expect(trigger.textContent).toBe("Medium");
  });

  it("value=high 时 trigger 显示 High", async () => {
    render(<ReasoningEffortSelect options={OPTIONS} value="high" onChange={vi.fn()} />);
    const trigger = await screen.findByRole("button", { name: "思考强度" });
    expect(trigger.textContent).toBe("High");
  });

  it("打开下拉后每个档位一个 radio 项（3 个）", async () => {
    render(<ReasoningEffortSelect options={OPTIONS} value="low" onChange={vi.fn()} />);
    fireEvent.click(await screen.findByRole("button", { name: "思考强度" }));
    // shadcn RadioGroupItem 渲染成 type=button + value=id 的 <button>
    // 关联的 <label> 含档位文字（"思考 Low" 等）
    const labels = screen.getAllByText(/^思考 (Low|Medium|High)/);
    expect(labels.map((l) => l.textContent)).toEqual(["思考 Low", "思考 Medium默认档", "思考 High"]);
  });

  it("点击档位触发 onChange（传 id）", async () => {
    const onChange = vi.fn();
    render(<ReasoningEffortSelect options={OPTIONS} value="low" onChange={onChange} />);
    fireEvent.click(await screen.findByRole("button", { name: "思考强度" }));
    // 找到关联 'effort-high' label 的 input 然后点 label（更接近真实交互）
    const highLabel = screen.getByText("思考 High");
    fireEvent.click(highLabel);
    expect(onChange).toHaveBeenCalledWith("high");
  });

  it("只支持单档位时仍渲染并可选中", async () => {
    const single: ReasoningEffort[] = [{ id: "medium", name: "Medium" }];
    render(<ReasoningEffortSelect options={single} value="medium" onChange={vi.fn()} />);
    const trigger = await screen.findByRole("button", { name: "思考强度" });
    expect(trigger.textContent).toBe("Medium");
  });
});
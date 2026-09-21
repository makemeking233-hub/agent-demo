/**
 * ReasoningEffortSelect 主题切换验证（shadcn-prototype §A Task A9
 * → shadcn-components-p2 C6：机制收敛到 <html data-theme>）。
 *
 * <p>原型阶段不能真启 vite dev server 截图，改用 jsdom + 切换主题属性
 * 模拟 light/dark 两主题，断言组件在两主题下行为一致。
 *
 * <p>主题机制已统一为 `<html data-theme="...">`（useThemeApplication 写入，
 * tokens-dark.css 的 `:root[data-theme="dark"]` 响应），旧
 * `body[data-ds-dark-theme]` 不再使用。
 */

import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ReasoningEffort } from "../api/chat";
import { ReasoningEffortSelect } from "./ReasoningEffortSelect";

const OPTIONS: ReasoningEffort[] = [
  { id: "low", name: "Low" },
  { id: "medium", name: "Medium" },
  { id: "high", name: "High" },
];

function applyTheme(theme: "light" | "dark" | "hc") {
  document.documentElement.dataset.theme = theme;
}

describe("ReasoningEffortSelect — 两主题行为一致", () => {
  afterEach(() => {
    cleanup();
    applyTheme("light");
  });

  it("light 主题：trigger 渲染当前档位名字", async () => {
    applyTheme("light");
    render(<ReasoningEffortSelect options={OPTIONS} value="high" onChange={vi.fn()} />);
    const trigger = await screen.findByRole("button", { name: "思考强度" });
    expect(trigger.textContent).toBe("High");
  });

  it("dark 主题：trigger 仍渲染当前档位名字（行为一致）", async () => {
    applyTheme("dark");
    render(<ReasoningEffortSelect options={OPTIONS} value="high" onChange={vi.fn()} />);
    const trigger = await screen.findByRole("button", { name: "思考强度" });
    expect(trigger.textContent).toBe("High");
  });

  it("dark 主题：打开 popover 仍可点击档位触发 onChange", async () => {
    applyTheme("dark");
    const onChange = vi.fn();
    render(<ReasoningEffortSelect options={OPTIONS} value="low" onChange={onChange} />);
    fireEvent.click(await screen.findByRole("button", { name: "思考强度" }));
    fireEvent.click(screen.getByText("思考 Medium"));
    expect(onChange).toHaveBeenCalledWith("medium");
  });
});
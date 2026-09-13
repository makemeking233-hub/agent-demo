import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";

/**
 * mermaid 在 jsdom 里**无法真实渲染**——它依赖 `getBBox`、`getComputedTextLength` 等
 * jsdom 未实现的 SVG 测量 API，调用必然抛错。所以这里 mock 掉 mermaid，只断言：
 *
 * <ul>
 *   <li>初始化配置正确（安全级别 + 深色主题）；
 *   <li>`render` 被调用且收到正确源码；
 *   <li>三条分支（成功 / 渲染失败 / 重复源码不重渲染）的 UI 表现。
 * </ul>
 *
 * <p>**真实能否出图不由此文件证明**，必须另在浏览器里实开验证（见 tasks.md 5.3）。
 */
const mermaidMock = vi.hoisted(() => ({
  initialize: vi.fn(),
  render: vi.fn(),
}));

vi.mock("mermaid", () => ({ default: mermaidMock }));

// mock 之后再 import 组件，确保它拿到的是 mock 模块。
import { MermaidBlock } from "./MermaidBlock";

const SOURCE = "flowchart TD\n  A[开始] --> B[结束]";

describe("MermaidBlock", () => {
  beforeEach(() => {
    mermaidMock.initialize.mockReset();
    mermaidMock.render.mockReset();
  });

  it("以 strict 安全级别与深色主题初始化", async () => {
    mermaidMock.render.mockResolvedValue({ svg: "<svg data-testid='diagram'></svg>" });

    render(<MermaidBlock source={SOURCE} />);

    await waitFor(() => expect(mermaidMock.render).toHaveBeenCalled());
    expect(mermaidMock.initialize).toHaveBeenCalledWith(
      expect.objectContaining({ securityLevel: "strict", theme: "dark" }),
    );
  });

  it("把渲染结果放进带无障碍标签的容器", async () => {
    mermaidMock.render.mockResolvedValue({ svg: "<svg data-testid='diagram'></svg>" });

    render(<MermaidBlock source={SOURCE} />);

    await waitFor(() =>
      expect(document.querySelector("[data-testid='diagram']")).not.toBeNull(),
    );
    const holder = screen.getByRole("img");
    expect(holder.getAttribute("aria-label")).toBeTruthy();
  });

  it("render 收到的是围栏里的源码", async () => {
    mermaidMock.render.mockResolvedValue({ svg: "<svg></svg>" });

    render(<MermaidBlock source={SOURCE} />);

    await waitFor(() => expect(mermaidMock.render).toHaveBeenCalled());
    // 第一个参数是 mermaid 要求的唯一 id，第二个才是源码
    expect(mermaidMock.render.mock.calls[0][1]).toBe(SOURCE);
  });

  it("渲染失败时显示原始源码与一行提示，且不抛未捕获异常", async () => {
    mermaidMock.render.mockRejectedValue(new Error("Parse error on line 2"));

    render(<MermaidBlock source={SOURCE} />);

    await waitFor(() => expect(screen.getByText(/渲染失败/)).toBeInTheDocument());
    expect(screen.getByText(/Parse error on line 2/)).toBeInTheDocument();
    // 源码仍完整可见（用户既能知情又有据可查）
    expect(screen.getByText(/flowchart TD/)).toBeInTheDocument();
  });

  it("源码未变化时不重复渲染", async () => {
    mermaidMock.render.mockResolvedValue({ svg: "<svg></svg>" });

    const { rerender } = render(<MermaidBlock source={SOURCE} />);
    await waitFor(() => expect(mermaidMock.render).toHaveBeenCalledTimes(1));

    rerender(<MermaidBlock source={SOURCE} />);

    // 给 effect 一点时间；若实现有去重，这里仍是 1 次
    await new Promise((r) => setTimeout(r, 20));
    expect(mermaidMock.render).toHaveBeenCalledTimes(1);
  });
});

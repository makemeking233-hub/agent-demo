/**
 * ThinkingCollapse 单元测试（add-reasoning-thinking-streaming）。
 */
import { afterEach, describe, expect, it } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { ThinkingCollapse } from "../src/components/ThinkingCollapse";

describe("ThinkingCollapse", () => {
  afterEach(() => cleanup());

  it("text 为空时不渲染", () => {
    const { container } = render(<ThinkingCollapse text="" />);
    expect(container.firstChild).toBeNull();
  });

  it("默认折叠 + 标题显示估算 token 数", () => {
    render(<ThinkingCollapse text="我先思考" />);
    const summary = screen.getByText(/思考过程 \(/);
    expect(summary).toBeInTheDocument();
    expect(summary.textContent).toMatch(/1 token/); // 4 字 → 1 token
  });

  it("短文本完整展示（无'查看更多'）", () => {
    render(<ThinkingCollapse text="思考短文本" />);
    expect(screen.queryByText("查看更多")).toBeNull();
    expect(screen.getByText("思考短文本")).toBeInTheDocument();
  });

  it("长文本（> 2000 字符）默认截断 + 显示'查看更多'", () => {
    const longText = "x".repeat(2500);
    render(<ThinkingCollapse text={longText} />);
    expect(screen.getByText("查看更多")).toBeInTheDocument();
    // 默认折叠 → 不显示完整
    expect(screen.queryByText(longText)).toBeNull();
  });

  it("显式 tokens 参数覆盖估算", () => {
    render(<ThinkingCollapse text="短文本" tokens={42} />);
    expect(screen.getByText(/42 token/)).toBeInTheDocument();
  });
});
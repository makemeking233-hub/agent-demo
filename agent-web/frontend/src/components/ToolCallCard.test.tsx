import { describe, expect, it } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { ToolCallCard } from "./ToolCallCard";

describe("ToolCallCard", () => {
  it("renders running state with accent label", () => {
    render(<ToolCallCard name="Shell" status="running" />);
    expect(screen.getByText(/Shell/)).toBeInTheDocument();
    expect(screen.getByText(/执行中/)).toBeInTheDocument();
  });

  it("renders ok state with success label and duration", () => {
    render(<ToolCallCard name="ReadFile" status="ok" text="ok result" durationMs={5} />);
    // meta 区拼 "完成 · 5ms"
    expect(screen.getByText(/完成.*5ms/)).toBeInTheDocument();
    // 默认折叠：output 不可见；点击 header 展开后可见
    expect(screen.queryByText("ok result")).not.toBeInTheDocument();
    fireEvent.click(screen.getByText(/ReadFile/));
    expect(screen.getByText("ok result")).toBeInTheDocument();
  });

  it("collapses on second click", () => {
    render(<ToolCallCard name="ReadFile" status="ok" text="ok result" />);
    fireEvent.click(screen.getByText(/ReadFile/));
    expect(screen.getByText("ok result")).toBeInTheDocument();
    fireEvent.click(screen.getByText(/ReadFile/));
    expect(screen.queryByText("ok result")).not.toBeInTheDocument();
  });

  it("renders fail state with danger label", () => {
    render(<ToolCallCard name="EditFile" status="fail" text="error" />);
    expect(screen.getByText(/失败/)).toBeInTheDocument();
    // 默认折叠：点击展开后 error 可见
    fireEvent.click(screen.getByText(/EditFile/));
    expect(screen.getByText("error")).toBeInTheDocument();
  });

  it("omits output block when text absent", () => {
    const { container } = render(<ToolCallCard name="Ls" status="ok" />);
    expect(screen.getByText(/Ls/)).toBeInTheDocument();
    // 无 text prop → <pre> 不应出现（即使展开也没有）
    fireEvent.click(screen.getByText(/Ls/));
    expect(container.querySelector("pre")).toBeNull();
  });
});

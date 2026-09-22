import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { ToolCallCard, type SandboxDenial } from "./ToolCallCard";

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

// ---- rewrite-permission-mode-dsh T11.3: sandbox denial 渲染 ----

const denialWithSuggestion: SandboxDenial = {
  kind: "write-out-of-bounds",
  currentMode: "ask",
  suggestedMode: "danger-full",
  marker:
    "[sandbox: write-out-of-bounds under ask mode] 路径越界（mode=ask）: ../escape.txt Escalate: POST /api/chat/{streamId}/permission with mode=danger-full",
};

const denialWithoutSuggestion: SandboxDenial = {
  kind: "sensitive-path",
  currentMode: "ask",
  suggestedMode: null,
  marker: "[sandbox: sensitive-path under ask mode] 命中敏感路径",
};

describe("ToolCallCard sandbox denial (T11.3)", () => {
  it("renders denial marker when denial present", () => {
    render(
      <ToolCallCard
        name="WriteFile"
        status="fail"
        denial={denialWithSuggestion}
        onEscalate={() => {}}
      />,
    );
    const marker = screen.getByTestId("sandbox-denial-marker");
    expect(marker.textContent).toContain("[sandbox: write-out-of-bounds under ask mode]");
    expect(marker.textContent).toContain("danger-full");
  });

  it("renders denial metadata (current mode + kind)", () => {
    render(<ToolCallCard name="WriteFile" status="fail" denial={denialWithSuggestion} />);
    const banner = screen.getByTestId("sandbox-denial");
    expect(banner.textContent).toContain("当前模式：ask");
    expect(banner.textContent).toContain("拒绝原因：write-out-of-bounds");
  });

  it("shows escalate button when suggestedMode is non-null", () => {
    const onEscalate = vi.fn();
    render(
      <ToolCallCard
        name="WriteFile"
        status="fail"
        denial={denialWithSuggestion}
        onEscalate={onEscalate}
      />,
    );
    const btn = screen.getByTestId("sandbox-escalate-button");
    expect(btn.textContent).toContain("升级到 danger-full");
    fireEvent.click(btn);
    expect(onEscalate).toHaveBeenCalledWith("danger-full");
  });

  it("hides escalate button when suggestedMode is null but keeps marker", () => {
    render(
      <ToolCallCard
        name="ReadFile"
        status="fail"
        denial={denialWithoutSuggestion}
        onEscalate={() => {}}
      />,
    );
    expect(screen.queryByTestId("sandbox-escalate-button")).toBeNull();
    expect(screen.getByTestId("sandbox-denial-marker")).toBeInTheDocument();
  });

  it("hides escalate button when onEscalate not provided", () => {
    render(<ToolCallCard name="WriteFile" status="fail" denial={denialWithSuggestion} />);
    expect(screen.queryByTestId("sandbox-escalate-button")).toBeNull();
  });

  it("renders nothing denial-related when no denial prop", () => {
    render(<ToolCallCard name="WriteFile" status="ok" text="写入成功" />);
    expect(screen.queryByTestId("sandbox-denial")).toBeNull();
  });

  it("still renders normal tool card header alongside denial", () => {
    render(
      <ToolCallCard
        name="WriteFile"
        status="fail"
        text="路径越界"
        durationMs={12}
        denial={denialWithSuggestion}
      />,
    );
    expect(screen.getByLabelText(/WriteFile 工具调用/)).toBeInTheDocument();
    expect(screen.getByTestId("sandbox-denial")).toBeInTheDocument();
  });
});

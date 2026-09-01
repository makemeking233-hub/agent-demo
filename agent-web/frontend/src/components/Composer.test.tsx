import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { Composer } from "./Composer";

describe("Composer", () => {
  it("renders permission dropdown with three options, default Read Only", () => {
    render(<Composer busy={false} onSend={() => {}} />);
    const select = screen.getByLabelText("权限模式") as HTMLSelectElement;
    expect(select.value).toBe("read_only");
    expect(screen.getByText("Read Only")).toBeInTheDocument();
    expect(screen.getByText("Workspace Write")).toBeInTheDocument();
    expect(screen.getByText("Full access")).toBeInTheDocument();
  });

  it("calls onPermissionModeChange when a mode is selected", () => {
    const onChange = vi.fn();
    render(<Composer busy={false} onSend={() => {}} onPermissionModeChange={onChange} />);
    const select = screen.getByLabelText("权限模式");
    fireEvent.change(select, { target: { value: "full_access" } });
    expect(onChange).toHaveBeenCalledWith("full_access");
  });

  it("reflects controlled permissionMode prop", () => {
    render(<Composer busy={false} onSend={() => {}} permissionMode="workspace_write" />);
    const select = screen.getByLabelText("权限模式") as HTMLSelectElement;
    expect(select.value).toBe("workspace_write");
  });
});

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

describe("Composer 语音按钮（add-voice-interaction）", () => {
  it("提供 onVoiceToggle/onMuteToggle 时渲染 🎤 与 🔊", () => {
    render(<Composer busy={false} onSend={() => {}} onVoiceToggle={() => {}} onMuteToggle={() => {}} />);
    expect(screen.getByLabelText("开启自由语音")).toBeInTheDocument();
    expect(screen.getByLabelText("静音朗读")).toBeInTheDocument();
  });

  it("点击 🎤 触发 onVoiceToggle", () => {
    const onVoiceToggle = vi.fn();
    render(<Composer busy={false} onSend={() => {}} onVoiceToggle={onVoiceToggle} onMuteToggle={() => {}} />);
    fireEvent.click(screen.getByLabelText("开启自由语音"));
    expect(onVoiceToggle).toHaveBeenCalled();
  });

  it("muted=true 时展示「开启朗读」", () => {
    render(<Composer busy={false} onSend={() => {}} muted onVoiceToggle={() => {}} onMuteToggle={() => {}} />);
    expect(screen.getByLabelText("开启朗读")).toBeInTheDocument();
  });

  it("voiceState=listening 时 🎤 展示「关闭自由语音」", () => {
    render(<Composer busy={false} onSend={() => {}} voiceState="listening" onVoiceToggle={() => {}} onMuteToggle={() => {}} />);
    expect(screen.getByLabelText("关闭自由语音")).toBeInTheDocument();
  });
});

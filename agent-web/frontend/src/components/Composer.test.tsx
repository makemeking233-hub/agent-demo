import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { Composer } from "./Composer";

describe("Composer", () => {
  it("renders permission dropdown with four dsh modes, default Plan", () => {
    render(<Composer busy={false} onSend={() => {}} />);
    const select = screen.getByLabelText("权限模式") as HTMLSelectElement;
    expect(select.value).toBe("plan");
    expect(screen.getByText("Plan")).toBeInTheDocument();
    expect(screen.getByText("Ask")).toBeInTheDocument();
    expect(screen.getByText("Danger Full")).toBeInTheDocument();
    expect(screen.getByText("Don't Ask")).toBeInTheDocument();
  });

  it("calls onPermissionModeChange when a mode is selected", () => {
    const onChange = vi.fn();
    render(<Composer busy={false} onSend={() => {}} onPermissionModeChange={onChange} />);
    const select = screen.getByLabelText("权限模式");
    fireEvent.change(select, { target: { value: "danger-full" } });
    expect(onChange).toHaveBeenCalledWith("danger-full");
  });

  it("reflects controlled permissionMode prop", () => {
    render(<Composer busy={false} onSend={() => {}} permissionMode="ask" />);
    const select = screen.getByLabelText("权限模式") as HTMLSelectElement;
    expect(select.value).toBe("ask");
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

// improve-voice-accuracy T6：partial result UI
describe("Composer partial UI（T6）", () => {
  it("语音循环未启动（voiceState=idle）时，partial 不渲染", () => {
    const { container } = render(
      <Composer busy={false} onSend={() => {}} voiceState="idle" lastPartial="帮我看看" />,
    );
    // 无 .partial 元素
    expect(container.querySelector('[class*="partial"]')).toBeNull();
  });

  it("voiceState=listening 且 lastPartial 非空时，渲染 partial 内容", () => {
    render(
      <Composer
        busy={false}
        onSend={() => {}}
        voiceState="listening"
        lastPartial="帮我看看日志"
      />,
    );
    expect(screen.getByText("帮我看看日志")).toBeInTheDocument();
  });

  it("voiceState=listening 但 lastPartial 为空时，partial 不渲染", () => {
    const { container } = render(
      <Composer busy={false} onSend={() => {}} voiceState="listening" lastPartial="" />,
    );
    expect(container.querySelector('[class*="partial"]')).toBeNull();
  });

  it("isProcessingVoice=true 时显示「纠错中...」占位", () => {
    render(
      <Composer
        busy={false}
        onSend={() => {}}
        voiceState="sending"
        isProcessingVoice
        lastPartial=""
      />,
    );
    expect(screen.getByText("纠错中...")).toBeInTheDocument();
  });

  it("isProcessingVoice=true 优先于 lastPartial 显示", () => {
    render(
      <Composer
        busy={false}
        onSend={() => {}}
        voiceState="sending"
        isProcessingVoice
        lastPartial="原始 partial"
      />,
    );
    // 纠错中优先显示"纠错中..."
    expect(screen.getByText("纠错中...")).toBeInTheDocument();
    expect(screen.queryByText("原始 partial")).toBeNull();
  });
});

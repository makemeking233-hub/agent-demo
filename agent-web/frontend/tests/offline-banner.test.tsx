/**
 * OfflineBanner + Composer 离线行为测试（add-pwa-support）。
 * mock useOnline 验证 Snackbar 渲染 + 发送按钮禁用。
 */
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { OnlineProvider, useOnline } from "../src/hooks/useOnline";
import { OfflineBanner } from "../src/components/OfflineBanner";
import { Composer } from "../src/components/Composer";

beforeEach(() => {
  // 强制 jsdom navigator.onLine = true（初始）
  Object.defineProperty(navigator, "onLine", {
    configurable: true,
    value: true,
  });
});

afterEach(() => {
  cleanup();
});

function setOnline(value: boolean) {
  Object.defineProperty(navigator, "onLine", {
    configurable: true,
    value,
  });
  window.dispatchEvent(new Event(value ? "online" : "offline"));
}

function Probe(): JSX.Element {
  const { isOnline } = useOnline();
  return <span data-testid="probe">{isOnline ? "online" : "offline"}</span>;
}

describe("OnlineProvider + useOnline", () => {
  it("初始 navigator.onLine 反映到 useOnline", () => {
    setOnline(true);
    render(
      <OnlineProvider>
        <Probe />
      </OnlineProvider>,
    );
    expect(screen.getByTestId("probe")).toHaveTextContent("online");
  });

  it("dispatch offline 事件后 isOnline 切到 false", async () => {
    setOnline(true);
    render(
      <OnlineProvider>
        <Probe />
      </OnlineProvider>,
    );
    expect(screen.getByTestId("probe")).toHaveTextContent("online");
    setOnline(false);
    await waitFor(() =>
      expect(screen.getByTestId("probe")).toHaveTextContent("offline"),
    );
  });
});

describe("OfflineBanner", () => {
  it("在线时不渲染", () => {
    setOnline(true);
    const { container } = render(
      <OnlineProvider>
        <OfflineBanner />
      </OnlineProvider>,
    );
    expect(container.firstChild).toBeNull();
  });

  it("离线时显示 Snackbar 文字'网络已断开'", () => {
    setOnline(false);
    render(
      <OnlineProvider>
        <OfflineBanner />
      </OnlineProvider>,
    );
    expect(screen.getByText("网络已断开")).toBeInTheDocument();
    expect(screen.getByRole("status")).toBeInTheDocument();
  });
});

describe("Composer 离线行为", () => {
  it("在线时 placeholder 是'输入消息...'", () => {
    setOnline(true);
    render(<Composer busy={false} onSend={() => {}} />);
    const textarea = screen.getByPlaceholderText("输入消息或 /help...");
    expect(textarea).toBeInTheDocument();
  });

  it("离线时 placeholder 变成'网络已断开'且 textarea 禁用", () => {
    setOnline(false);
    render(<Composer busy={false} onSend={() => {}} />);
    const textarea = screen.getByPlaceholderText("网络已断开");
    expect(textarea).toBeInTheDocument();
    expect(textarea).toBeDisabled();
    expect(screen.getByText("网络已断开")).toBeInTheDocument();
  });
});
/**
 * MessageActionRow 测试（add-message-actions P1 copy + P2 clock）。
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MessageActionRow, writeClipboard } from "./MessageActionRow";

describe("MessageActionRow (P1 copy)", () => {
  let writeTextMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    writeTextMock = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", {
      value: { writeText: writeTextMock },
      configurable: true,
      writable: true,
    });
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it("renders copy button + row container", () => {
    render(<MessageActionRow text="hello" />);
    expect(screen.getByTestId("message-action-row")).toBeInTheDocument();
    expect(screen.getByTestId("msg-copy")).toBeInTheDocument();
    expect(screen.getByTestId("msg-copy").getAttribute("aria-label")).toBe("复制");
  });

  it("clicking copy writes text to clipboard", async () => {
    render(<MessageActionRow text="要复制的文本" />);
    fireEvent.click(screen.getByTestId("msg-copy"));
    await waitFor(() => expect(writeTextMock).toHaveBeenCalledWith("要复制的文本"));
  });

  it("shows ✓ for 1s after successful copy, then reverts", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    render(<MessageActionRow text="x" />);
    fireEvent.click(screen.getByTestId("msg-copy"));
    await waitFor(() =>
      expect(screen.getByTestId("msg-copy").getAttribute("aria-label")).toBe("已复制"),
    );
    // 1s 后恢复
    await vi.advanceTimersByTimeAsync(1100);
    await waitFor(() =>
      expect(screen.getByTestId("msg-copy").getAttribute("aria-label")).toBe("复制"),
    );
  });

  it("does not re-write clipboard when clicked during ✓ window (防重入)", async () => {
    render(<MessageActionRow text="x" />);
    fireEvent.click(screen.getByTestId("msg-copy"));
    await waitFor(() => expect(writeTextMock).toHaveBeenCalledTimes(1));
    // ✓ 窗口内再点
    fireEvent.click(screen.getByTestId("msg-copy"));
    fireEvent.click(screen.getByTestId("msg-copy"));
    await new Promise((r) => setTimeout(r, 50));
    expect(writeTextMock).toHaveBeenCalledTimes(1);
  });

  it("renders extraActions children between copy and clock", () => {
    render(
      <MessageActionRow
        text="x"
        meta={{ uuid: "u1", duration_ms: 15_000, ttft_ms: 1200, tok_per_sec: 34, timestamp: Date.now() }}
      >
        <button type="button" data-testid="up">👍</button>
      </MessageActionRow>,
    );
    expect(screen.getByTestId("up")).toBeInTheDocument();
    expect(screen.getByTestId("msg-clock")).toBeInTheDocument();
    // 顺序：copy → children → clock
    const row = screen.getByTestId("message-action-row");
    const ids = Array.from(row.querySelectorAll("[data-testid]")).map((el) =>
      el.getAttribute("data-testid"),
    );
    expect(ids).toEqual(["msg-copy", "up", "msg-clock"]);
  });

  it("falls back to execCommand when navigator.clipboard throws", async () => {
    writeTextMock.mockRejectedValueOnce(new Error("not allowed"));
    const execMock = vi.fn().mockReturnValue(true);
    // jsdom 没有 execCommand，手动挂上
    (document as unknown as { execCommand: typeof execMock }).execCommand = execMock;
    const ok = await writeClipboard("x");
    expect(ok).toBe(true);
    expect(execMock).toHaveBeenCalledWith("copy");
  });

  it("returns false when both clipboard paths fail", async () => {
    writeTextMock.mockRejectedValueOnce(new Error("nope"));
    (document as unknown as { execCommand: () => boolean }).execCommand = () => {
      throw new Error("no execCommand");
    };
    const ok = await writeClipboard("x");
    expect(ok).toBe(false);
  });
});

describe("MessageActionRow (P2 clock)", () => {
  afterEach(() => cleanup());

  const ts = new Date(2026, 8, 14, 16, 23, 0).getTime();

  it("renders full clock when all fields present", () => {
    render(
      <MessageActionRow
        text="x"
        meta={{ uuid: "u1", duration_ms: 15_000, ttft_ms: 1200, tok_per_sec: 34, timestamp: ts }}
      />,
    );
    expect(screen.getByTestId("msg-clock").textContent).toBe(
      "16:23 · Ran for 15s · TTFT 1.2s · 34 tok/s",
    );
  });

  it("skips TTFT segment when ttft_ms is null", () => {
    render(
      <MessageActionRow
        text="x"
        meta={{ duration_ms: 15_000, ttft_ms: null, tok_per_sec: 34, timestamp: ts }}
      />,
    );
    expect(screen.getByTestId("msg-clock").textContent).toBe("16:23 · Ran for 15s · 34 tok/s");
  });

  it("skips tok/s segment when tok_per_sec is null", () => {
    render(
      <MessageActionRow
        text="x"
        meta={{ duration_ms: 2000, ttft_ms: 800, tok_per_sec: null, timestamp: ts }}
      />,
    );
    expect(screen.getByTestId("msg-clock").textContent).toBe("16:23 · Ran for 2.0s · TTFT 800ms");
  });

  it("renders no clock element when meta is absent", () => {
    const first = render(<MessageActionRow text="x" />);
    expect(screen.queryByTestId("msg-clock")).toBeNull();
    first.unmount();
    render(<MessageActionRow text="x" meta={null} />);
    expect(screen.queryByTestId("msg-clock")).toBeNull();
  });
});

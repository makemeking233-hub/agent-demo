import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { ChatPanel } from "./ChatPanel";

const KEY = "agent-demo.chat.v1";

describe("ChatPanel 会话重进恢复", () => {
  beforeAll(() => {
    // jsdom 未实现 scrollTo；ChatPanel 的自动滚动 effect 需要它，否则挂载即抛错。
    Object.defineProperty(Element.prototype, "scrollTo", {
      configurable: true,
      value: () => {},
    });
  });
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
    // mock 全局 fetch：history 默认返回空，避免真实网络误触。
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({ session_id: "s-1", messages: [] }),
      }),
    );
  });
  afterEach(() => cleanup());

  it("挂载时从 localStorage 恢复消息快照", () => {
    localStorage.setItem(
      KEY,
      JSON.stringify({
        v: 1,
        sessionId: "s-1",
        items: [
          { kind: "text", id: "u1", role: "user", text: "恢复的用户消息" },
          { kind: "text", id: "a1", role: "assistant", text: "恢复的助手回复" },
        ],
      }),
    );
    render(<ChatPanel />);
    expect(screen.getByText("恢复的用户消息")).toBeInTheDocument();
    expect(screen.getByText("恢复的助手回复")).toBeInTheDocument();
  });

  it("无持久化时显示空态", () => {
    render(<ChatPanel />);
    expect(screen.getByText(/开始对话/)).toBeInTheDocument();
  });

  it("无本地快照但有会话时用服务端历史回填", async () => {
    // 只持久化 session_id（items 为空），服务端返回一段历史。
    localStorage.setItem(KEY, JSON.stringify({ v: 1, sessionId: "s-1", items: [] }));
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({
          session_id: "s-1",
          messages: [
            { role: "user", content: "服务端历史用户" },
            { role: "assistant", content: "服务端历史助手", toolCalls: [] },
          ],
        }),
      }),
    );
    render(<ChatPanel />);
    // 回填是异步（mount 后 fetch history），用 waitFor 等待渲染。
    const { waitFor } = await import("@testing-library/react");
    await waitFor(() => expect(screen.getByText("服务端历史用户")).toBeInTheDocument());
    expect(screen.getByText("服务端历史助手")).toBeInTheDocument();
  });

  it("回填失败时不阻断（降级为空态）", async () => {
    localStorage.setItem(KEY, JSON.stringify({ v: 1, sessionId: "s-1", items: [] }));
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));
    render(<ChatPanel />);
    const { waitFor } = await import("@testing-library/react");
    await waitFor(() => expect(screen.getByText(/开始对话/)).toBeInTheDocument());
  });
});

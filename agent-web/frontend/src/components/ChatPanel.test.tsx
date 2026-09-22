import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { ChatPanel, attachMetaToTimeline, mapHistoryToItems, type Item } from "./ChatPanel";
import type { MessageClock } from "../lib/message-clock";

const KEY = "agent-demo.chat.v1";

// rewrite-permission-mode-dsh T10.2/T11.2：ChatPanel 从 settings 读权限模式。
// 用可变 store 让各测试控制 general.permission.mode。
const settingsStore = {
  snapshot: {
    version: 1,
    general: { permission: { mode: "plan" as string } },
    revision: 0,
  } as { version: number; general: { permission: { mode?: string } }; revision: number },
  status: "ready" as const,
  error: null,
  patch: vi.fn().mockResolvedValue(undefined),
  refresh: vi.fn(),
};

vi.mock("../hooks/useSettingsStore", () => ({
  useSettingsStore: (selector: (s: typeof settingsStore) => unknown) => selector(settingsStore),
}));

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

  /**
   * add-models-dropdown-v0 之后 ChatPanel 新增了必填 props（provider / model / reasoningEffort /
   * currentModelEntry / onReasoningEffortChange；provider 为 add-provider-catalog-abstract task 11.4 新增）。
   * 本文件只关心历史恢复与空态，与模型选择无关，故统一给固定桩值，避免多处重复。
   */
  function renderPanel() {
    return render(
      <ChatPanel
        provider="deepseek"
        model="deepseek-chat"
        reasoningEffort="medium"
        currentModelEntry={null}
        onReasoningEffortChange={() => {}}
      />,
    );
  }

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
    renderPanel();
    expect(screen.getByText("恢复的用户消息")).toBeInTheDocument();
    expect(screen.getByText("恢复的助手回复")).toBeInTheDocument();
  });

  it("无持久化时显示空态", () => {
    renderPanel();
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
    renderPanel();
    // 回填是异步（mount 后 fetch history），用 waitFor 等待渲染。
    const { waitFor } = await import("@testing-library/react");
    await waitFor(() => expect(screen.getByText("服务端历史用户")).toBeInTheDocument());
    expect(screen.getByText("服务端历史助手")).toBeInTheDocument();
  });

  it("回填失败时不阻断（降级为空态）", async () => {
    localStorage.setItem(KEY, JSON.stringify({ v: 1, sessionId: "s-1", items: [] }));
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));
    renderPanel();
    const { waitFor } = await import("@testing-library/react");
    await waitFor(() => expect(screen.getByText(/开始对话/)).toBeInTheDocument());
  });
});

// ---- rewrite-permission-mode-dsh T11.2: ChatPanel 从 settings 读权限模式 ----

describe("ChatPanel 权限模式 (T11.2)", () => {
  beforeAll(() => {
    Object.defineProperty(Element.prototype, "scrollTo", {
      configurable: true,
      value: () => {},
    });
  });
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
    settingsStore.snapshot = {
      version: 1,
      general: { permission: { mode: "plan" } },
      revision: 0,
    };
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

  function renderPanel() {
    return render(
      <ChatPanel
        provider="deepseek"
        model="deepseek-chat"
        reasoningEffort="medium"
        currentModelEntry={null}
        onReasoningEffortChange={() => {}}
      />,
    );
  }

  it("Composer 权限下拉反映 settings 中的 mode", () => {
    settingsStore.snapshot.general.permission.mode = "danger-full";
    renderPanel();
    const select = screen.getByLabelText("权限模式") as HTMLSelectElement;
    expect(select.value).toBe("danger-full");
  });

  it("settings 无 mode 时缺省 plan", () => {
    settingsStore.snapshot.general.permission = {};
    renderPanel();
    const select = screen.getByLabelText("权限模式") as HTMLSelectElement;
    expect(select.value).toBe("plan");
  });

  it("用户在 Composer 切换 mode 时 session 级覆盖生效", () => {
    settingsStore.snapshot.general.permission.mode = "plan";
    renderPanel();
    const select = screen.getByLabelText("权限模式") as HTMLSelectElement;
    expect(select.value).toBe("plan");
    fireEvent.change(select, { target: { value: "ask" } });
    expect((screen.getByLabelText("权限模式") as HTMLSelectElement).value).toBe("ask");
  });
});

/** add-message-actions P2：per-message clock 的时间线装配。 */
describe("ChatPanel per-message clock（P2）", () => {
  const ts = new Date(2026, 8, 14, 16, 23, 0).getTime();
  const meta: MessageClock = {
    uuid: "u-42",
    duration_ms: 15_000,
    ttft_ms: 1200,
    tok_per_sec: 34,
    timestamp: ts,
  };

  const assistantItem = (id: string, text = "回复"): Item => ({
    kind: "text",
    id,
    role: "assistant",
    text,
  });
  const userItem = (id: string): Item => ({ kind: "text", id, role: "user", text: "问" });

  it("attachMetaToTimeline 把读数贴到最后一条 assistant 文本项", () => {
    const out = attachMetaToTimeline([userItem("u1"), assistantItem("a1")], meta);
    expect((out[1] as Extract<Item, { kind: "text" }>).meta).toEqual(meta);
    expect((out[1] as Extract<Item, { kind: "text" }>).uuid).toBe("u-42");
  });

  it("一轮被工具拆成多条 assistant item 时，读数贴到最后一条（整轮读数）", () => {
    const withTool: Item = {
      kind: "text",
      id: "a1",
      role: "assistant",
      text: "先说一句",
      tools: [{ id: "t1", name: "read", status: "ok" }],
    };
    const multi: Item[] = [userItem("u1"), withTool, assistantItem("a2", "基于结果再说一句")];
    const out = attachMetaToTimeline(multi, meta);
    expect((out[1] as Extract<Item, { kind: "text" }>).meta).toBeUndefined();
    expect((out[2] as Extract<Item, { kind: "text" }>).meta).toEqual(meta);
  });

  it("没有 assistant 文本项时原样返回（不抛错）", () => {
    const only: Item[] = [userItem("u1")];
    expect(attachMetaToTimeline(only, meta)).toBe(only);
  });

  it("历史回填把后端 meta / uuid 透传到 item（刷新后 clock 仍在）", () => {
    const items = mapHistoryToItems([
      { role: "user", content: "问" },
      { role: "assistant", content: "答", toolCalls: [], uuid: "u-42", meta },
    ]);
    const a = items.find((it) => it.kind === "text" && it.role === "assistant");
    expect(a).toBeDefined();
    expect((a as Extract<Item, { kind: "text" }>).meta).toEqual(meta);
    expect((a as Extract<Item, { kind: "text" }>).uuid).toBe("u-42");
  });

  it("后端没给读数时 item 的 meta 为 undefined（不渲染 clock）", () => {
    const items = mapHistoryToItems([{ role: "assistant", content: "旧的回复", toolCalls: [] }]);
    expect((items[0] as Extract<Item, { kind: "text" }>).meta).toBeUndefined();
  });
});

/** add-message-feedback F3.5：👍/👎 接线、乐观更新、409 调和、失败回滚。 */
describe("ChatPanel 消息反馈（P3）", () => {
  const ASSISTANT_UUID = "u-rate-1";

  beforeAll(() => {
    Object.defineProperty(Element.prototype, "scrollTo", { configurable: true, value: () => {} });
  });

  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  afterEach(() => cleanup());

  /**
   * 按 URL + method 路由的 fetch 桩：返回 history / feedback GET / feedback PUT|DELETE，
   * 未匹配的 URL 一律 200 空对象（settings 等旁路请求）。
   *
   * @param opts.feedback       GET /api/feedback/{sid} 的 items
   * @param opts.writeResponse  写请求（PUT/DELETE）的「状态 + body」
   * @param opts.onWrite        写请求回调（用于断言 body）
   */
  function stubRoutedFetch(opts: {
    feedback?: Record<string, { rating: string; version: number; updated_at: number }>;
    writeStatus?: number;
    writeBody?: unknown;
    onWrite?: (method: string, url: string, body: unknown) => void;
  }) {
    const fn = vi.fn().mockImplementation(async (url: string, init?: RequestInit) => {
      const method = (init?.method ?? "GET").toUpperCase();
      if (url.includes("/api/feedback/") && method !== "GET") {
        const body = init?.body ? JSON.parse(init.body as string) : {};
        opts.onWrite?.(method, url, body);
        const status = opts.writeStatus ?? 200;
        return {
          ok: status >= 200 && status < 300,
          status,
          json: async () => opts.writeBody ?? { rating: "up", version: 1, updated_at: 1 },
        };
      }
      if (url.includes("/api/feedback/")) {
        return {
          ok: true,
          status: 200,
          json: async () => ({ session_id: "s-1", items: opts.feedback ?? {} }),
        };
      }
      if (url.includes("/messages")) {
        return {
          ok: true,
          status: 200,
          json: async () => ({
            session_id: "s-1",
            messages: [
              { role: "user", content: "问" },
              { role: "assistant", content: "答", toolCalls: [], uuid: ASSISTANT_UUID },
            ],
          }),
        };
      }
      return { ok: true, status: 200, json: async () => ({}) };
    });
    vi.stubGlobal("fetch", fn);
    return fn;
  }

  /**
   * 渲染并模拟「用户在侧边栏点了 s-1」的真实流程。
   *
   * <p>不能直接在挂载时传 `currentSessionId="s-1"`：ChatPanel 的会话切换 effect 用
   * `lastSessionIdRef` 做了「首次挂载不入内」的短路（首次加载交给 localStorage 恢复路径），
   * 挂载即传会走到 early-return，`sessionIdRef` 保持 null → 历史与反馈都不会加载。
   * 先以 null 挂载、再 rerender 成 "s-1"，与真实点击行为一致。
   *
   * @returns testing-library 的 render 结果（可 rerender）
   */
  function renderWithSession() {
    const view = render(
      <ChatPanel
        currentSessionId={null}
        provider="deepseek"
        model="deepseek-chat"
        reasoningEffort="medium"
        currentModelEntry={null}
        onReasoningEffortChange={() => {}}
      />,
    );
    view.rerender(
      <ChatPanel
        currentSessionId="s-1"
        provider="deepseek"
        model="deepseek-chat"
        reasoningEffort="medium"
        currentModelEntry={null}
        onReasoningEffortChange={() => {}}
      />,
    );
    return view;
  }

  it("带 uuid 的 assistant 消息渲染赞踩按钮，且初始都未选中", async () => {
    stubRoutedFetch({});
    renderWithSession();
    const { waitFor } = await import("@testing-library/react");
    await waitFor(() => expect(screen.getByTestId("msg-up")).toBeInTheDocument());
    expect(screen.getByTestId("msg-up").getAttribute("aria-pressed")).toBe("false");
    expect(screen.getByTestId("msg-down").getAttribute("aria-pressed")).toBe("false");
  });

  it("首屏拉取已有 feedback 并高亮（刷新后仍选中）", async () => {
    stubRoutedFetch({
      feedback: { [ASSISTANT_UUID]: { rating: "down", version: 2, updated_at: 1 } },
    });
    renderWithSession();
    const { waitFor } = await import("@testing-library/react");
    await waitFor(() =>
      expect(screen.getByTestId("msg-down").getAttribute("aria-pressed")).toBe("true"),
    );
    expect(screen.getByTestId("msg-up").getAttribute("aria-pressed")).toBe("false");
  });

  it("点击 👍 首次创建：PUT ifVersion=null，成功后保持高亮", async () => {
    const writes: Array<{ method: string; body: unknown }> = [];
    stubRoutedFetch({
      onWrite: (method, _url, body) => writes.push({ method, body }),
      writeBody: { rating: "up", version: 1, updated_at: 1 },
    });
    renderWithSession();
    const { waitFor, fireEvent } = await import("@testing-library/react");
    await waitFor(() => expect(screen.getByTestId("msg-up")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("msg-up"));
    await waitFor(() => expect(writes.length).toBe(1));
    expect(writes[0].method).toBe("PUT");
    expect(writes[0].body).toEqual({ rating: "up", ifVersion: null });
    await waitFor(() =>
      expect(screen.getByTestId("msg-up").getAttribute("aria-pressed")).toBe("true"),
    );
  });

  it("已 👍 再点 👍 → DELETE 取消", async () => {
    const writes: Array<{ method: string; body: unknown }> = [];
    stubRoutedFetch({
      feedback: { [ASSISTANT_UUID]: { rating: "up", version: 1, updated_at: 1 } },
      writeStatus: 204,
      onWrite: (method, _url, body) => writes.push({ method, body }),
    });
    renderWithSession();
    const { waitFor, fireEvent } = await import("@testing-library/react");
    await waitFor(() =>
      expect(screen.getByTestId("msg-up").getAttribute("aria-pressed")).toBe("true"),
    );
    fireEvent.click(screen.getByTestId("msg-up"));
    await waitFor(() => expect(writes.length).toBe(1));
    expect(writes[0].method).toBe("DELETE");
    expect(writes[0].body).toEqual({ ifVersion: 1 });
    await waitFor(() =>
      expect(screen.getByTestId("msg-up").getAttribute("aria-pressed")).toBe("false"),
    );
  });

  it("已 👍 点 👎 → PUT 切换方向（ifVersion=当前）", async () => {
    const writes: Array<{ method: string; body: unknown }> = [];
    stubRoutedFetch({
      feedback: { [ASSISTANT_UUID]: { rating: "up", version: 1, updated_at: 1 } },
      writeBody: { rating: "down", version: 2, updated_at: 2 },
      onWrite: (method, _url, body) => writes.push({ method, body }),
    });
    renderWithSession();
    const { waitFor, fireEvent } = await import("@testing-library/react");
    await waitFor(() =>
      expect(screen.getByTestId("msg-up").getAttribute("aria-pressed")).toBe("true"),
    );
    fireEvent.click(screen.getByTestId("msg-down"));
    await waitFor(() => expect(writes.length).toBe(1));
    expect(writes[0].body).toEqual({ rating: "down", ifVersion: 1 });
    await waitFor(() =>
      expect(screen.getByTestId("msg-down").getAttribute("aria-pressed")).toBe("true"),
    );
  });

  it("PUT 返回 500 → 回滚到点击前状态（不静默留错）", async () => {
    stubRoutedFetch({ writeStatus: 500 });
    renderWithSession();
    const { waitFor, fireEvent } = await import("@testing-library/react");
    await waitFor(() => expect(screen.getByTestId("msg-up")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("msg-up"));
    // 乐观窗口内可能已高亮，最终必须回滚为未选中
    await waitFor(() =>
      expect(screen.getByTestId("msg-up").getAttribute("aria-pressed")).toBe("false"),
    );
  });

  it("PUT 返回 409 → 用服务端 current 调和（对方点过 👎）", async () => {
    stubRoutedFetch({
      writeStatus: 409,
      writeBody: { current: { rating: "down", version: 3, updated_at: 7 } },
    });
    renderWithSession();
    const { waitFor, fireEvent } = await import("@testing-library/react");
    await waitFor(() => expect(screen.getByTestId("msg-up")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("msg-up"));
    await waitFor(() =>
      expect(screen.getByTestId("msg-down").getAttribute("aria-pressed")).toBe("true"),
    );
    expect(screen.getByTestId("msg-up").getAttribute("aria-pressed")).toBe("false");
  });
});

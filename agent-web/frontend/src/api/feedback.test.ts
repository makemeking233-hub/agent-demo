/**
 * 消息反馈 API 客户端测试（add-message-feedback F3.1 / F3.5）。
 */

import { afterEach, describe, expect, it, vi } from "vitest";
import {
  deleteFeedback,
  FeedbackConflictError,
  getFeedback,
  nextRating,
  putFeedback,
} from "./feedback";

describe("nextRating（两态互斥 + 再点取消）", () => {
  it("首次点赞", () => {
    expect(nextRating(null, "up")).toBe("up");
  });

  it("首次点踩", () => {
    expect(nextRating(null, "down")).toBe("down");
  });

  it("再点已选中 → 取消（null）", () => {
    expect(nextRating("up", "up")).toBeNull();
    expect(nextRating("down", "down")).toBeNull();
  });

  it("切换方向", () => {
    expect(nextRating("up", "down")).toBe("down");
    expect(nextRating("down", "up")).toBe("up");
  });
});

describe("feedback API", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  function stubFetch(res: { ok?: boolean; status?: number; json?: unknown }) {
    const fn = vi.fn().mockResolvedValue({
      ok: res.ok ?? true,
      status: res.status ?? 200,
      json: async () => res.json ?? {},
    });
    vi.stubGlobal("fetch", fn);
    return fn;
  }

  it("getFeedback 返回 items 映射", async () => {
    stubFetch({
      json: { session_id: "s-1", items: { "u-1": { rating: "up", version: 1, updated_at: 1 } } },
    });
    const r = await getFeedback("s-1");
    expect(r.items["u-1"].rating).toBe("up");
  });

  it("getFeedback 非 2xx 抛错", async () => {
    stubFetch({ ok: false, status: 500 });
    await expect(getFeedback("s-1")).rejects.toThrow("getFeedback 500");
  });

  it("putFeedback 成功返回写入项（带 ifVersion）", async () => {
    const fn = stubFetch({ json: { rating: "down", version: 2, updated_at: 9 } });
    const item = await putFeedback("s-1", "u-1", "down", 1);
    expect(item).toEqual({ rating: "down", version: 2, updated_at: 9 });
    const body = JSON.parse((fn.mock.calls[0][1] as RequestInit).body as string);
    expect(body).toEqual({ rating: "down", ifVersion: 1 });
  });

  it("putFeedback 首次创建时 ifVersion 为 null", async () => {
    const fn = stubFetch({ json: { rating: "up", version: 1, updated_at: 1 } });
    await putFeedback("s-1", "u-1", "up", null);
    const body = JSON.parse((fn.mock.calls[0][1] as RequestInit).body as string);
    expect(body.ifVersion).toBeNull();
  });

  it("putFeedback 409 抛 FeedbackConflictError 并携带 current", async () => {
    stubFetch({ ok: false, status: 409, json: { current: { rating: "up", version: 3, updated_at: 5 } } });
    await expect(putFeedback("s-1", "u-1", "down", 1)).rejects.toBeInstanceOf(FeedbackConflictError);
    try {
      await putFeedback("s-1", "u-1", "down", 1);
    } catch (e) {
      expect((e as FeedbackConflictError).current).toEqual({ rating: "up", version: 3, updated_at: 5 });
    }
  });

  it("putFeedback 409 且 current 为 null（对方已删除）", async () => {
    stubFetch({ ok: false, status: 409, json: { current: null } });
    try {
      await putFeedback("s-1", "u-1", "down", 2);
      throw new Error("should have thrown");
    } catch (e) {
      expect(e).toBeInstanceOf(FeedbackConflictError);
      expect((e as FeedbackConflictError).current).toBeNull();
    }
  });

  it("putFeedback 500 抛普通 Error（前端据此回滚）", async () => {
    stubFetch({ ok: false, status: 500 });
    await expect(putFeedback("s-1", "u-1", "up", null)).rejects.toThrow("putFeedback 500");
  });

  it("deleteFeedback 204 视为成功", async () => {
    stubFetch({ ok: false, status: 204 });
    await expect(deleteFeedback("s-1", "u-1", 1)).resolves.toBeUndefined();
  });

  it("deleteFeedback 409 抛冲突", async () => {
    stubFetch({ ok: false, status: 409, json: { current: null } });
    try {
      await deleteFeedback("s-1", "u-1", 2);
      throw new Error("should have thrown");
    } catch (e) {
      expect(e).toBeInstanceOf(FeedbackConflictError);
    }
  });

  it("路径参数做 URL 编码", async () => {
    const fn = stubFetch({ json: { session_id: "s 1", items: {} } });
    await getFeedback("s 1");
    expect(fn.mock.calls[0][0]).toContain("s%201");
  });
});
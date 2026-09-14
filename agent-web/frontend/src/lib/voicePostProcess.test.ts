import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  contextCorrect,
  dedupeRepeats,
  filterShort,
} from "./voicePostProcess";

describe("filterShort（T3.3）", () => {
  it("null/空串/<2 字 → 返回 null（应丢弃）", () => {
    expect(filterShort(null)).toBeNull();
    expect(filterShort(undefined)).toBeNull();
    expect(filterShort("")).toBeNull();
    expect(filterShort(" ")).toBeNull();
    expect(filterShort("嗯")).toBeNull();
    expect(filterShort(" a ")).toBeNull();
  });

  it("纯语气词（≥ 2 字）→ 返回 null", () => {
    expect(filterShort("嗯啊")).toBeNull();
    expect(filterShort("啊呃嗯")).toBeNull();
  });

  it("正常文本 → 返回 trim 后原值", () => {
    expect(filterShort("帮我看看")).toBe("帮我看看");
    expect(filterShort(" 帮我看看 ")).toBe("帮我看看");
  });
});

describe("dedupeRepeats（T3.1）", () => {
  it("连续 2 次重复 → 截到 1 次", () => {
    expect(dedupeRepeats("凶手凶手")).toBe("凶手");
  });

  it("连续 3 次重复 → 截到 1 次", () => {
    expect(dedupeRepeats("凶手凶手凶手")).toBe("凶手");
  });

  it("重复 + 扩展（句末渐进修正）→ 截到 1 次 + 扩展", () => {
    expect(dedupeRepeats("凶手凶手升级")).toBe("凶手升级");
    expect(dedupeRepeats("邪念邪念邪念眼角")).toBe("邪念眼角");
  });

  it("无重复 → 原样返回", () => {
    expect(dedupeRepeats("现在看再正常不过了")).toBe("现在看再正常不过了");
    expect(dedupeRepeats("")).toBe("");
  });

  it("单字符重复也处理", () => {
    expect(dedupeRepeats("好好好")).toBe("好");
    expect(dedupeRepeats("哈哈哈")).toBe("哈");
  });
});

describe("contextCorrect（T3.2 降级路径）", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("enabled=false → 不调 API，直接返回 raw", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    const out = await contextCorrect("邪念 眼角舍小", "s1", { enabled: false });
    expect(out).toBe("邪念 眼角舍小");
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("HTTP 200 + corrected → 返回 corrected", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ corrected: "我现在想问一下", cached: false }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    const out = await contextCorrect("邪念 眼角舍小", "s1", { enabled: true });
    expect(out).toBe("我现在想问一下");
  });

  it("HTTP 5xx → 降级返回 raw", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("internal error", { status: 500 }),
    );
    const out = await contextCorrect("邪念 眼角舍小", "s1", { enabled: true });
    expect(out).toBe("邪念 眼角舍小");
  });

  it("HTTP 4xx → 降级返回 raw", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("bad request", { status: 400 }),
    );
    const out = await contextCorrect("邪念 眼角舍小", "s1", { enabled: true });
    expect(out).toBe("邪念 眼角舍小");
  });

  it("超时（AbortError）→ 降级返回 raw", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementation(
      () =>
        new Promise((_resolve, reject) => {
          // 模拟 AbortController.abort 触发的 AbortError
          setTimeout(() => {
            const err = new Error("aborted");
            err.name = "AbortError";
            reject(err);
          }, 10);
        }),
    );
    const out = await contextCorrect("帮我看看", "s1", {
      enabled: true,
      timeoutMs: 50,
    });
    expect(out).toBe("帮我看看");
  });

  it("网络错误（TypeError）→ 降级返回 raw", async () => {
    vi.spyOn(globalThis, "fetch").mockRejectedValue(new TypeError("fetch failed"));
    const out = await contextCorrect("帮我看看", "s1", { enabled: true });
    expect(out).toBe("帮我看看");
  });

  it("HTTP 200 + corrected 为空 → 降级返回 raw（不提交空文本）", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ corrected: "", cached: false }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    const out = await contextCorrect("帮我看看", "s1", { enabled: true });
    expect(out).toBe("帮我看看");
  });
});

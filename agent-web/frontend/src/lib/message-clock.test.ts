/**
 * per-message clock 格式化测试（add-message-actions P2 / task 2.9）。
 */

import { describe, expect, it } from "vitest";
import {
  CLOCK_SEPARATOR,
  formatClock,
  formatClockTime,
  formatDuration,
  formatTps,
  formatTtft,
} from "./message-clock";

describe("formatDuration", () => {
  it("毫秒级用 ms", () => {
    expect(formatDuration(450)).toBe("450ms");
  });

  it("10 秒内保留 1 位小数", () => {
    expect(formatDuration(1500)).toBe("1.5s");
    expect(formatDuration(9999)).toBe("10.0s");
  });

  it("10 秒以上取整秒", () => {
    expect(formatDuration(15_000)).toBe("15s");
    expect(formatDuration(59_400)).toBe("59s");
  });

  it("超过 1 分钟用 m/m+s", () => {
    expect(formatDuration(120_000)).toBe("2m");
    expect(formatDuration(123_000)).toBe("2m3s");
  });

  it("缺失/非法值返回 null", () => {
    expect(formatDuration(null)).toBeNull();
    expect(formatDuration(undefined)).toBeNull();
    expect(formatDuration(-1)).toBeNull();
    expect(formatDuration(Number.NaN)).toBeNull();
  });
});

describe("formatTtft / formatTps / formatClockTime", () => {
  it("ttft 亚秒用 ms，秒级 1 位小数", () => {
    expect(formatTtft(820)).toBe("820ms");
    expect(formatTtft(1200)).toBe("1.2s");
    expect(formatTtft(null)).toBeNull();
  });

  it("tok/s 两位数取整、个位数 1 位小数，0/负/空为 null", () => {
    expect(formatTps(34.4)).toBe("34 tok/s");
    expect(formatTps(8.53)).toBe("8.5 tok/s");
    expect(formatTps(0)).toBeNull();
    expect(formatTps(-3)).toBeNull();
    expect(formatTps(null)).toBeNull();
  });

  it("时间戳按本地时区输出 HH:MM", () => {
    const ts = new Date(2026, 8, 14, 16, 23, 45).getTime();
    expect(formatClockTime(ts)).toBe("16:23");
    expect(formatClockTime(0)).toBeNull();
    expect(formatClockTime(null)).toBeNull();
  });
});

describe("formatClock", () => {
  const ts = new Date(2026, 8, 14, 16, 23, 45).getTime();

  it("全字段拼装（task 2.9 用例 1）", () => {
    expect(
      formatClock({ uuid: "u1", duration_ms: 15_000, ttft_ms: 1200, tok_per_sec: 34, timestamp: ts }),
    ).toBe(["16:23", "Ran for 15s", "TTFT 1.2s", "34 tok/s"].join(CLOCK_SEPARATOR));
  });

  it("缺 ttft_ms 时跳过该段（用例 2）", () => {
    expect(
      formatClock({ duration_ms: 15_000, ttft_ms: null, tok_per_sec: 34, timestamp: ts }),
    ).toBe(["16:23", "Ran for 15s", "34 tok/s"].join(CLOCK_SEPARATOR));
  });

  it("缺 tok_per_sec 时跳过该段（用例 3）", () => {
    expect(
      formatClock({ duration_ms: 15_000, ttft_ms: 1200, tok_per_sec: null, timestamp: ts }),
    ).toBe(["16:23", "Ran for 15s", "TTFT 1.2s"].join(CLOCK_SEPARATOR));
  });

  it("缺全部派生指标时只剩时间与 Ran for（用例 4）", () => {
    expect(formatClock({ duration_ms: 2000, ttft_ms: null, tok_per_sec: null, timestamp: ts })).toBe(
      ["16:23", "Ran for 2.0s"].join(CLOCK_SEPARATOR),
    );
  });

  it("meta 为空 / 时间戳也缺时返回空串（调用方据此不渲染）", () => {
    expect(formatClock(null)).toBe("");
    expect(formatClock(undefined)).toBe("");
    expect(
      formatClock({ duration_ms: 0, ttft_ms: null, tok_per_sec: null, timestamp: 0 }),
    ).toBe("Ran for 0ms");
  });
});

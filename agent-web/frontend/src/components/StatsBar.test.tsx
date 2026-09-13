import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { StatsBar } from "./StatsBar";
import type { SessionStats } from "../api/chat";

function stats(over: Partial<SessionStats> = {}): SessionStats {
  return {
    turns: 3,
    steps: 7,
    tokens_in: 1200,
    tokens_out: 340,
    llm_ms: 125_000,
    tool_ms: 8_000,
    avg_ttft_ms: 7600,
    tok_per_sec: 120.4,
    cache_hit_rate: 1,
    ...over,
  };
}

describe("StatsBar", () => {
  it("renders all segments", () => {
    render(<StatsBar stats={stats()} />);
    const bar = screen.getByTestId("stats-bar");
    expect(bar.textContent).toContain("3 轮 · 7 步");
    expect(bar.textContent).toContain("LLM 2m5s");
    expect(bar.textContent).toContain("工具调用 8s");
    expect(bar.textContent).toContain("首 token 平均 7.6s");
    expect(bar.textContent).toContain("120.4 tok/s");
    expect(bar.textContent).toContain("缓存命中 100%");
    expect(bar.textContent).toContain("输入 1200 tok · 输出 340 tok");
  });

  it("shows N/A for unavailable derived metrics", () => {
    render(
      <StatsBar
        stats={stats({ avg_ttft_ms: null, tok_per_sec: null, cache_hit_rate: null })}
      />,
    );
    const bar = screen.getByTestId("stats-bar");
    expect(bar.textContent).toContain("首 token 平均 N/A");
    expect(bar.textContent).toContain("N/A tok/s");
    expect(bar.textContent).toContain("缓存命中 N/A");
  });

  it("handles null stats gracefully", () => {
    render(<StatsBar stats={null} />);
    const bar = screen.getByTestId("stats-bar");
    expect(bar.textContent).toContain("0 轮 · 0 步");
    expect(bar.textContent).toContain("缓存命中 N/A");
  });
});

import type { SessionStats } from "../api/chat";
import styles from "./StatsBar.module.css";

interface StatsBarProps {
  stats: SessionStats | null;
}

/** 毫秒 → 人类可读（如 92s / 2m5s）。 */
function formatDuration(ms: number): string {
  if (!ms || ms <= 0) return "0s";
  const totalSec = Math.round(ms / 1000);
  const m = Math.floor(totalSec / 60);
  const s = totalSec % 60;
  return m > 0 ? `${m}m${s}s` : `${s}s`;
}

/** 秒 → 一位小数（如 7.6s）。 */
function formatSeconds(ms: number | null): string {
  if (ms == null) return "N/A";
  return `${(ms / 1000).toFixed(1)}s`;
}

/** 数值 → 保留一位小数；null → N/A。 */
function formatRate(v: number | null): string {
  return v == null ? "N/A" : v.toFixed(1);
}

/** 比例 → 百分比整数；null → N/A。 */
function formatPercent(v: number | null): string {
  return v == null ? "N/A" : `${Math.round(v * 100)}%`;
}

/**
 * 底部统计状态栏（add-session-stats-bar）：仿 DSH，分段展示轮次/步数、耗时、TTFT/吞吐、缓存命中、token。
 *
 * <p>派生指标不可用时显示 {@code N/A}（不显示误导性的 0）。
 */
export function StatsBar({ stats }: StatsBarProps) {
  const s = stats;
  const segments = [
    `${s?.turns ?? 0} 轮 · ${s?.steps ?? 0} 步`,
    `LLM ${formatDuration(s?.llm_ms ?? 0)} · 工具调用 ${formatDuration(s?.tool_ms ?? 0)}`,
    `首 token 平均 ${formatSeconds(s?.avg_ttft_ms ?? null)} · ${formatRate(s?.tok_per_sec ?? null)} tok/s`,
    `缓存命中 ${formatPercent(s?.cache_hit_rate ?? null)}`,
    `输入 ${s?.tokens_in ?? 0} tok · 输出 ${s?.tokens_out ?? 0} tok`,
  ];
  return (
    <div className={styles.bar} data-testid="stats-bar" title={segments.join(" | ")}>
      {segments.join(" | ")}
    </div>
  );
}

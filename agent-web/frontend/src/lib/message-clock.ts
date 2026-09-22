/**
 * per-message clock 格式化（add-message-actions P2）。
 *
 * <p>对齐 DSH `MessageIconActions` 的读数行：
 *
 * ```text
 * 16:23 · Ran for 15s · TTFT 1.2s · 34 tok/s
 * ```
 *
 * <p>任一段缺失（provider 未返回 usage、无 wall time）就整段跳过，绝不用 `NaN` / `undefined` 占位。
 */

/** 单条消息的读数（`message_meta` SSE 事件 / 历史 `meta` 字段同构）。 */
export interface MessageClock {
  uuid?: string | null;
  duration_ms: number;
  ttft_ms: number | null;
  tok_per_sec: number | null;
  timestamp: number;
}

/** 分隔符（与 DSH 一致用中点 + 两侧空格）。 */
export const CLOCK_SEPARATOR = " · ";

/**
 * 毫秒 → 人类可读时长。
 *
 * @param ms 毫秒（负数/非有限值视为缺失）
 * @returns 形如 `450ms` / `1.5s` / `15s` / `2m3s`；不可用时为 `null`
 */
export function formatDuration(ms: number | null | undefined): string | null {
  if (ms == null || !Number.isFinite(ms) || ms < 0) return null;
  if (ms < 1000) return `${Math.round(ms)}ms`;
  if (ms < 10_000) return `${(ms / 1000).toFixed(1)}s`;
  if (ms < 60_000) return `${Math.round(ms / 1000)}s`;
  const total = Math.round(ms / 1000);
  const min = Math.floor(total / 60);
  const sec = total % 60;
  return sec === 0 ? `${min}m` : `${min}m${sec}s`;
}

/**
 * 首 token 延迟。
 *
 * @param ms 毫秒
 * @returns 形如 `450ms` / `1.2s`；不可用时为 `null`
 */
export function formatTtft(ms: number | null | undefined): string | null {
  if (ms == null || !Number.isFinite(ms) || ms < 0) return null;
  if (ms < 1000) return `${Math.round(ms)}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

/**
 * 输出吞吐。
 *
 * @param tps token/秒
 * @returns 形如 `34 tok/s` / `8.5 tok/s`；不可用时为 `null`
 */
export function formatTps(tps: number | null | undefined): string | null {
  if (tps == null || !Number.isFinite(tps) || tps <= 0) return null;
  const v = tps >= 10 ? String(Math.round(tps)) : tps.toFixed(1);
  return `${v} tok/s`;
}

/**
 * 时间戳 → `HH:MM`（本地时区，24 小时制）。
 *
 * @param timestamp epoch 毫秒（0/负数视为缺失）
 * @returns `16:23`；不可用时为 `null`
 */
export function formatClockTime(timestamp: number | null | undefined): string | null {
  if (timestamp == null || !Number.isFinite(timestamp) || timestamp <= 0) return null;
  const d = new Date(timestamp);
  const hh = String(d.getHours()).padStart(2, "0");
  const mm = String(d.getMinutes()).padStart(2, "0");
  return `${hh}:${mm}`;
}

/**
 * 拼装完整 clock 文本（缺失段自动跳过）。
 *
 * @param meta 读数（可空）
 * @returns 空格段用 ` · ` 连接；全部缺失时返回空串（调用方据此不渲染）
 */
export function formatClock(meta: MessageClock | null | undefined): string {
  if (!meta) return "";
  const parts = [
    formatClockTime(meta.timestamp),
    prefix(formatDuration(meta.duration_ms), "Ran for "),
    prefix(formatTtft(meta.ttft_ms), "TTFT "),
    formatTps(meta.tok_per_sec),
  ].filter((p): p is string => !!p);
  return parts.join(CLOCK_SEPARATOR);
}

/** 给非空段加前缀。 */
function prefix(seg: string | null, label: string): string | null {
  return seg ? label + seg : null;
}

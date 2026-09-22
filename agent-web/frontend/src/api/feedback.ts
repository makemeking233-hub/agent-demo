/**
 * 消息反馈 API 客户端（add-message-feedback F3.1）。
 *
 * <p>后端端点：
 *
 * - `GET    /api/feedback/{sessionId}`                  拉全量 rating
 * - `PUT    /api/feedback/{sessionId}/{messageId}`      创建/更新，body `{rating, ifVersion}`
 * - `DELETE /api/feedback/{sessionId}/{messageId}`      取消，body `{ifVersion}`
 *
 * <p>CAS 语义：`ifVersion` 为 `null` 表示「必须不存在」（首次创建），为 `N` 表示「必须等于当前
 * version N」。冲突返回 409 + `{current: {rating, version} | null}`，本模块抛
 * {@link FeedbackConflictError} 携带 `current` 供前端调和。
 */

/** 👍 / 👎 两态。 */
export type Rating = "up" | "down";

/** 后端返回的单条 feedback。 */
export interface FeedbackItem {
  rating: Rating;
  version: number;
  updated_at: number;
}

/** `GET` 响应：messageId(uuid) → feedback。 */
export interface FeedbackResponse {
  session_id: string;
  items: Record<string, FeedbackItem>;
}

/** 409 冲突：携带服务端当前真实状态（`null` = 对方已删除）。 */
export class FeedbackConflictError extends Error {
  readonly current: FeedbackItem | null;

  constructor(current: FeedbackItem | null) {
    super("feedback_version_conflict");
    this.name = "FeedbackConflictError";
    this.current = current;
  }
}

function base(): string {
  return "";
}

/**
 * 拉取某会话全部 feedback。
 *
 * @param sessionId 会话 id
 * @returns uuid → feedback；无反馈时 `items` 为空对象
 */
export async function getFeedback(sessionId: string): Promise<FeedbackResponse> {
  const r = await fetch(`${base()}/api/feedback/${encodeURIComponent(sessionId)}`);
  if (!r.ok) throw new Error(`getFeedback ${r.status}`);
  return (await r.json()) as FeedbackResponse;
}

/**
 * 创建或更新反馈。
 *
 * @param sessionId 会话 id
 * @param messageId 消息 uuid
 * @param rating    `up` / `down`
 * @param ifVersion `null` = 必须不存在（首次）；数字 = 必须等于该 version
 * @returns 写入后的 feedback
 * @throws FeedbackConflictError 409 时抛出，`current` 为服务端当前状态
 */
export async function putFeedback(
  sessionId: string,
  messageId: string,
  rating: Rating,
  ifVersion: number | null,
): Promise<FeedbackItem> {
  const r = await fetch(
    `${base()}/api/feedback/${encodeURIComponent(sessionId)}/${encodeURIComponent(messageId)}`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ rating, ifVersion }),
    },
  );
  if (r.status === 409) {
    const body = (await r.json().catch(() => ({}))) as { current?: FeedbackItem | null };
    throw new FeedbackConflictError(body.current ?? null);
  }
  if (!r.ok) throw new Error(`putFeedback ${r.status}`);
  return (await r.json()) as FeedbackItem;
}

/**
 * 取消反馈（点已选中项 = 取消）。
 *
 * @param sessionId 会话 id
 * @param messageId 消息 uuid
 * @param ifVersion 必须等于当前 version
 * @throws FeedbackConflictError 409 时抛出
 */
export async function deleteFeedback(
  sessionId: string,
  messageId: string,
  ifVersion: number | null,
): Promise<void> {
  const r = await fetch(
    `${base()}/api/feedback/${encodeURIComponent(sessionId)}/${encodeURIComponent(messageId)}`,
    {
      method: "DELETE",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ifVersion }),
    },
  );
  if (r.status === 409) {
    const body = (await r.json().catch(() => ({}))) as { current?: FeedbackItem | null };
    throw new FeedbackConflictError(body.current ?? null);
  }
  if (!r.ok && r.status !== 204) throw new Error(`deleteFeedback ${r.status}`);
}

/**
 * 计算点击某按钮后的目标 rating（两态互斥 + 再点取消）。
 *
 * <p>纯函数（便于单测）：当前 `up` 点 `up` → `null`（取消）；当前 `up` 点 `down` → `down`（切换）；
 * 当前无点 `up` → `up`（首次）。
 *
 * @param current 当前 rating（`null` = 无）
 * @param clicked 被点击的按钮
 * @returns 目标 rating；`null` 表示应取消（DELETE）
 */
export function nextRating(current: Rating | null, clicked: Rating): Rating | null {
  return current === clicked ? null : clicked;
}
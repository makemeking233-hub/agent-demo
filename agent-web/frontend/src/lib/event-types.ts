export type SseEventType =
  | 'message_start'
  | 'message_delta'
  | 'tool_call_start'
  | 'tool_call_end'
  | 'permission_request'
  | 'message_meta'
  | 'turn_stats'
  | 'message_stop'
  | 'error';

export interface MessageStart {
  type: 'message_start';
  stream_id: string;
  session_id: string;
  model: string;
  timestamp: number;
}

export interface MessageDelta {
  type: 'message_delta';
  delta_type: 'text' | 'thinking';
  content: string;
}

export interface ToolCallStart {
  type: 'tool_call_start';
  tool_call_id: string;
  name: string;
  args: unknown;
}

export interface ToolCallEnd {
  type: 'tool_call_end';
  tool_call_id: string;
  name: string;
  ok: boolean;
  result: unknown;
  duration_ms: number;
}

export interface PermissionRequest {
  type: 'permission_request';
  permission_id: string;
  tool_call_id: string;
  tool_name: string;
  reason: string;
  choices: ('yes' | 'no' | 'always')[];
}

export interface MessageStop {
  type: 'message_stop';
  finish_reason: 'stop' | 'length' | 'max_iterations' | 'compact_broken' | 'aborted';
}

export interface ErrorEvent {
  type: 'error';
  code: string;
  message: string;
}

/** 回合统计（add-session-stats-bar）：每次回合结束时于 message_stop 前推送。 */
export interface TurnStats {
  type: 'turn_stats';
  turns: number;
  steps: number;
  tokens_in: number;
  tokens_out: number;
  llm_ms: number;
  tool_ms: number;
  avg_ttft_ms: number | null;
  tok_per_sec: number | null;
  cache_hit_rate: number | null;
}

/** 单条 assistant 消息读数（add-message-actions P2）：回合结束时于 message_stop 前推送。 */
export interface MessageMeta {
  type: 'message_meta';
  /** 该条 assistant 在会话存档里的条目 uuid；会话不落盘时为 null */
  uuid: string | null;
  /** 本轮 wall time（用户输入 → 最后一条 assistant 定稿） */
  duration_ms: number;
  /** 首 token 延迟；本轮无文本 chunk 时为 null */
  ttft_ms: number | null;
  /** 输出吞吐；纯生成耗时 ≤ 0 时为 null */
  tok_per_sec: number | null;
  timestamp: number;
}

export type SseEvent =
  | MessageStart
  | MessageDelta
  | ToolCallStart
  | ToolCallEnd
  | PermissionRequest
  | MessageMeta
  | TurnStats
  | MessageStop
  | ErrorEvent;

export type SseEventType =
  | 'message_start'
  | 'message_delta'
  | 'tool_call_start'
  | 'tool_call_end'
  | 'permission_request'
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

export type SseEvent =
  | MessageStart
  | MessageDelta
  | ToolCallStart
  | ToolCallEnd
  | PermissionRequest
  | MessageStop
  | ErrorEvent;
